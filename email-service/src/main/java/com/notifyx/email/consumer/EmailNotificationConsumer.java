package com.notifyx.email.consumer;

import com.notify.common.dto.NotificationEventDto;
import com.notifyx.email.service.DlqService;
import com.notifyx.email.service.EmailService;
import com.notifyx.email.util.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationConsumer {

    private final EmailService emailService;
    private final DlqService dlqService;

    // Listening the vents pushed by the email notification service //
    @KafkaListener(
            topics = KafkaTopics.NOTIFICATION_EVENTS_TOPIC,
            groupId = "email-service-group"
    )
    public void consume(NotificationEventDto event) {
        try {
            log.info("Received email notification event for notificationId={}",
                    event.getNotificationId());

            // Control goes to the event service which processes this payload //
            emailService.processEmailNotification(event);

        } catch (Exception ex) {


            Long notificationId = event != null ? event.getNotificationId() : null;

            log.error("Failed to process email notification event", ex);

            dlqService.sendToDlq(
                    notificationId,
                    event,
                    "Consumer processing failed: " + ex.getMessage()
            );
        }
    }
}