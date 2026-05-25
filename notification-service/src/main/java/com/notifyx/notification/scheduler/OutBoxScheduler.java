package com.notifyx.notification.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notify.common.dto.NotificationEventDto;
import com.notify.common.enums.OutBoxStatus;
import com.notifyx.notification.entity.OutBoxEvent;
import com.notifyx.notification.producer.KafkaProducerService;
import com.notifyx.notification.repository.OutBoxRepository;
import com.notifyx.notification.service.DlqService;
import com.notifyx.notification.util.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutBoxScheduler {

    private final OutBoxRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;
    private final DlqService dlqService;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelay = 5000)
    public void publishOutboxEvents() {

        List<OutBoxEvent> outBoxEvents = outboxEventRepository.findPublishableEvents(
                List.of(OutBoxStatus.PENDING, OutBoxStatus.FAILED),
                LocalDateTime.now()
        );

        for (OutBoxEvent outBoxEvent : outBoxEvents) {
            publishEvent(outBoxEvent);
        }
    }

    private void publishEvent(OutBoxEvent event) {

        try {
            NotificationEventDto payload = objectMapper.readValue(
                    event.getPayload(),
                    NotificationEventDto.class
            );

            kafkaProducerService.publish(
                    KafkaTopics.NOTIFICATION_EVENTS_TOPIC,
                    String.valueOf(payload.getNotificationId()),
                    payload
            );

            event.setStatus(OutBoxStatus.PUBLISHED);
            event.setLastError(null);
            outboxEventRepository.save(event);

            log.info("Outbox event {} published successfully", event.getId());

        } catch (Exception ex) {

            int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
            retryCount++;

            event.setRetryCount(retryCount);
            event.setLastError(ex.getMessage());

            if (retryCount >= MAX_RETRIES) {
                try {
                    dlqService.sendToDlq(event.getId(), event.getPayload(), ex.getMessage());
                    event.setStatus(OutBoxStatus.DEAD_LETTERED);
                } catch (Exception dlqException) {
                    event.setStatus(OutBoxStatus.FAILED);
                    event.setLastError("DLQ publish failed: " + dlqException.getMessage());
                }
            } else {
                event.setStatus(OutBoxStatus.FAILED);
                event.setNextRetryAt(LocalDateTime.now().plusSeconds(calculateBackoffSeconds(retryCount)));
            }

            outboxEventRepository.save(event);

            log.error("Failed to publish outbox event {}", event.getId(), ex);
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