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

        validateEvent(event);

        Optional<SentEmail> existingEmail =
                sentEmailRepository.findByNotificationId(event.getNotificationId());

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
            emailSenderService.sendEmail(sentEmail);

            sentEmail.setStatus(EmailStatus.SENT);
            sentEmail.setLastError(null);
            sentEmail.setNextRetryAt(null);
            sentEmailRepository.save(sentEmail);

            createStatusOutboxEvent(
                    sentEmail.getNotificationId(),
                    EvenType.EMAIL_SENT
            );

        } catch (IllegalArgumentException ex) {
            handlePermanentFailure(sentEmail, ex);

        } catch (Exception ex) {
            handleRetryableFailure(sentEmail, ex);
        }
    }

    private void handleRetryableFailure(SentEmail sentEmail, Exception ex) {
        int retryCount = sentEmail.getRetryCount() == null ? 0 : sentEmail.getRetryCount();
        retryCount++;

        sentEmail.setRetryCount(retryCount);
        sentEmail.setLastError(ex.getMessage());

        if (retryCount >= MAX_EMAIL_RETRIES) {
            sentEmail.setStatus(EmailStatus.FAILED);
            sentEmail.setNextRetryAt(null);
            sentEmailRepository.save(sentEmail);

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

        sentEmail.setStatus(EmailStatus.RETRY_PENDING);
        sentEmail.setNextRetryAt(
                LocalDateTime.now().plusSeconds(calculateBackoffSeconds(retryCount))
        );

        sentEmailRepository.save(sentEmail);
    }

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
            NotificationStatusEventDto statusEvent = NotificationStatusEventDto.builder()
                    .eventType(eventType)
                    .notificationId(notificationId)
                    .build();

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