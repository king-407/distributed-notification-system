package com.notifyx.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notify.common.dto.NotificationEventDto;
import com.notify.common.enums.ChannelType;
import com.notify.common.enums.EvenType;
import com.notify.common.enums.NotificationStatus;
import com.notify.common.enums.OutBoxStatus;
import com.notifyx.notification.dto.NotificationRequestDto;
import com.notifyx.notification.entity.Notification;
import com.notifyx.notification.entity.OutBoxEvent;
import com.notifyx.notification.idempotency.IdempotencyService;
import com.notifyx.notification.ratelimit.RateLimitService;
import com.notifyx.notification.repository.NotificationRepository;
import com.notifyx.notification.repository.OutBoxRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OutBoxRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final RateLimitService rateLimitService;

    @Transactional
    public Long createNotification(NotificationRequestDto request, String idempotencyKey) {

        Long existingNotificationId = idempotencyService.getExistingNotificationId(idempotencyKey);

        if (existingNotificationId != null) {
            return existingNotificationId;
        }

        boolean locked = idempotencyService.lockRequest(idempotencyKey);

        if (!locked) {
            throw new IllegalStateException("Duplicate request is already being processed");
        }

        if (!rateLimitService.isAllowed(request.getRecipient())) {
            throw new IllegalStateException("Rate limit exceeded for recipient: " + request.getRecipient());
        }

        try {
            Notification notification = Notification.builder()
                    .recipient(request.getRecipient())
                    .subject(request.getSubject())
                    .message(request.getMessage())
                    .channel(ChannelType.EMAIL)
                    .status(NotificationStatus.PENDING)
                    .build();

            Notification savedNotification = notificationRepository.save(notification);

            NotificationEventDto eventPayload = NotificationEventDto.builder()
                    .eventType(EvenType.EMAIL_NOTIFICATION_CREATED)
                    .notificationId(savedNotification.getId())
                    .recipient(savedNotification.getRecipient())
                    .subject(savedNotification.getSubject())
                    .message(savedNotification.getMessage())
                    .build();

            OutBoxEvent outboxEvent = OutBoxEvent.builder()
                    .aggregateId(savedNotification.getId())
                    .aggregateType("NOTIFICATION")
                    .eventType(EvenType.EMAIL_NOTIFICATION_CREATED)
                    .payload(objectMapper.writeValueAsString(eventPayload))
                    .status(OutBoxStatus.PENDING)
                    .retryCount(0)
                    .build();

            outboxEventRepository.save(outboxEvent);

            idempotencyService.markCompleted(idempotencyKey, savedNotification.getId());

            return savedNotification.getId();

        } catch (Exception ex) {
            throw new RuntimeException("Failed to create notification", ex);
        }
    }

    @Transactional
    public void markAsSent(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        if (notification.getStatus() == NotificationStatus.SENT) {
            return;
        }

        notification.setStatus(NotificationStatus.SENT);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAsFailed(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        if (notification.getStatus() == NotificationStatus.FAILED) {
            return;
        }

        notification.setStatus(NotificationStatus.FAILED);
        notificationRepository.save(notification);
    }
}