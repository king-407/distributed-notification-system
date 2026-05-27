package com.notifyx.email.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notify.common.dto.NotificationEventDto;
import com.notify.common.dto.NotificationStatusEventDto;
import com.notify.common.enums.EvenType;
import com.notify.common.enums.OutBoxStatus;
import com.notifyx.email.entity.EmailStatus;
import com.notifyx.email.entity.OutBoxEvent;
import com.notifyx.email.entity.SentEmail;
import com.notifyx.email.repository.OutBoxEventRepository;
import com.notifyx.email.repository.SentEmailRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final SentEmailRepository sentEmailRepository;
    private final OutBoxEventRepository outboxEventRepository;
    private final EmailSenderService emailSenderService;
    private final DlqService dlqService;
    private final ObjectMapper objectMapper;

    private static final int MAX_EMAIL_RETRIES = 5;

    @Transactional
    public void processEmailNotification(NotificationEventDto event) {

        // checks for validation if the payload is valid //
        validateEvent(event);

        Optional<SentEmail> existingEmail =
                sentEmailRepository.findByNotificationId(event.getNotificationId());

        // if the same payload is already being processed then simply return and do nothing //
        if (existingEmail.isPresent()) {
            SentEmail existing = existingEmail.get();

            if (existing.getStatus() == EmailStatus.SENT) {
                return;
            }

            if (existing.getStatus() == EmailStatus.PROCESSING ||
                    existing.getStatus() == EmailStatus.RETRY_PENDING) {
                return;
            }
        }

        // if not present then make am entry in the sent email and mark it as PROCESSING //
        SentEmail sentEmail = SentEmail.builder()
                .notificationId(event.getNotificationId())
                .recipient(event.getRecipient())
                .subject(event.getSubject())
                .message(event.getMessage())
                .status(EmailStatus.PROCESSING)
                .retryCount(0)
                .build();

        sentEmailRepository.save(sentEmail);

        trySendEmail(sentEmail);
    }

    @Transactional
    public void retryEmail(SentEmail sentEmail) {
        trySendEmail(sentEmail);
    }

    private void trySendEmail(SentEmail sentEmail) {
        try {
            // CONTROL goes to the SMTP server for sending which checks first if
            // the email is valid //
            emailSenderService.sendEmail(sentEmail);

            sentEmail.setStatus(EmailStatus.SENT);
            sentEmail.setLastError(null);
            sentEmail.setNextRetryAt(null);
            sentEmailRepository.save(sentEmail);

            createStatusOutboxEvent(
                    sentEmail.getNotificationId(),
                    EvenType.EMAIL_SENT
            );

            // if invalid then caught by this exception which is handled by handlePermanentFailure(
        } catch (IllegalArgumentException ex) {
            handlePermanentFailure(sentEmail, ex);

            //If normal failure then retry (handleRetryableFailure)
        } catch (Exception ex) {
            handleRetryableFailure(sentEmail, ex);
        }
    }

    // It retries after every fixed amount of time with backoff time //
    private void handleRetryableFailure(SentEmail sentEmail, Exception ex) {
        int retryCount = sentEmail.getRetryCount() == null ? 0 : sentEmail.getRetryCount();
        retryCount++;

        sentEmail.setRetryCount(retryCount);
        sentEmail.setLastError(ex.getMessage());

        // if count exhausted then send it to dlq //
        if (retryCount >= MAX_EMAIL_RETRIES) {
            sentEmail.setStatus(EmailStatus.FAILED);
            sentEmail.setNextRetryAt(null);
            sentEmailRepository.save(sentEmail);

            // also send to the outbox event and send to the notofication service and with
            // failed event
            createStatusOutboxEvent(
                    sentEmail.getNotificationId(),
                    EvenType.EMAIL_FAILED
            );

            dlqService.sendToDlq(
                    sentEmail.getNotificationId(),
                    sentEmail,
                    "Email retries exhausted: " + ex.getMessage()
            );

            return;
        }

        // If retry not exhausted then mark it as RETRY_PENDING //
        sentEmail.setStatus(EmailStatus.RETRY_PENDING);
        // setting the next retry at time //
        sentEmail.setNextRetryAt(
                LocalDateTime.now().plusSeconds(calculateBackoffSeconds(retryCount))
        );

        sentEmailRepository.save(sentEmail);
    }

    // This method saves the sent_email db as FAILED
    // creates an OutBoxEvent which prepares the payload to send to the topic//
    private void handlePermanentFailure(SentEmail sentEmail, Exception ex) {
        sentEmail.setStatus(EmailStatus.FAILED);
        sentEmail.setLastError(ex.getMessage());
        sentEmail.setNextRetryAt(null);
        sentEmailRepository.save(sentEmail);

        createStatusOutboxEvent(
                sentEmail.getNotificationId(),
                EvenType.EMAIL_FAILED
        );

        dlqService.sendToDlq(
                sentEmail.getNotificationId(),
                sentEmail,
                "Permanent email failure: " + ex.getMessage()
        );
    }

    private void createStatusOutboxEvent(Long notificationId, EvenType eventType) {
        try {
            // First prepares the event to be sent to the topic //
            NotificationStatusEventDto statusEvent = NotificationStatusEventDto.builder()
                    .eventType(eventType)
                    .notificationId(notificationId)
                    .build();

            // now saves the event in outbox with status as PENDING//
            OutBoxEvent outboxEvent = OutBoxEvent.builder()
                    .aggregateId(notificationId)
                    .aggregateType("EMAIL")
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(statusEvent))
                    .status(OutBoxStatus.PENDING)
                    .retryCount(0)
                    .build();

            outboxEventRepository.save(outboxEvent);

        } catch (Exception ex) {
            throw new RuntimeException("Failed to create email status outbox event", ex);
        }
    }

    private void validateEvent(NotificationEventDto event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (event.getEventType() != EvenType.EMAIL_NOTIFICATION_CREATED) {
            throw new IllegalArgumentException("Unsupported event type: " + event.getEventType());
        }

        if (event.getNotificationId() == null) {
            throw new IllegalArgumentException("Notification id is required");
        }

        if (event.getRecipient() == null || event.getRecipient().isBlank()) {
            throw new IllegalArgumentException("Recipient is required");
        }
    }

    private long calculateBackoffSeconds(int retryCount) {
        return switch (retryCount) {
            case 1 -> 5;
            case 2 -> 15;
            case 3 -> 30;
            case 4 -> 60;
            default -> 120;
        };
    }
}