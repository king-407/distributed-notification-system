package com.notifyx.notification.consumer;

import com.notify.common.dto.NotificationStatusEventDto;
import com.notify.common.enums.EvenType;
import com.notifyx.notification.service.NotificationService;
import com.notifyx.notification.util.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStatusConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = KafkaTopics.NOTIFICATION_STATUS_TOPIC,
            groupId = "notification-status-group"
    )
    public void consume(NotificationStatusEventDto event) {

        log.info("Received notification status event: {}", event.getEventType());

        if (event.getEventType() == EvenType.EMAIL_SENT) {
            notificationService.markAsSent(event.getNotificationId());
            return;
        }

        if (event.getEventType() == EvenType.EMAIL_FAILED) {
            notificationService.markAsFailed(event.getNotificationId());
            return;
        }

        throw new IllegalArgumentException("Unsupported event type: " + event.getEventType());
    }
}