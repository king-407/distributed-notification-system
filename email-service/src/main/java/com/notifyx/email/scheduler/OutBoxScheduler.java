package com.notifyx.email.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notify.common.dto.NotificationStatusEventDto;
import com.notify.common.enums.OutBoxStatus;
import com.notifyx.email.entity.OutBoxEvent;
import com.notifyx.email.producer.KafkaProducerService;
import com.notifyx.email.repository.OutBoxEventRepository;
import com.notifyx.email.service.DlqService;
import com.notifyx.email.util.KafkaTopics;
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

    private final OutBoxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;
    private final DlqService dlqService;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelay = 5000)
    public void publishOutboxEvents() {
        List<OutBoxEvent> events = outboxEventRepository.findPublishableEvents(
                List.of(OutBoxStatus.PENDING, OutBoxStatus.FAILED),
                LocalDateTime.now()
        );

        for (OutBoxEvent event : events) {
            publishEvent(event);
        }
    }

    private void publishEvent(OutBoxEvent event) {
        try {
            NotificationStatusEventDto payload = objectMapper.readValue(
                    event.getPayload(),
                    NotificationStatusEventDto.class
            );

            kafkaProducerService.publish(
                    KafkaTopics.NOTIFICATION_STATUS_TOPIC,
                    String.valueOf(payload.getNotificationId()),
                    payload
            );

            // After publishing to kafka set the status for outbox to PUBLISHED //
            event.setStatus(OutBoxStatus.PUBLISHED);
            event.setLastError(null);
            outboxEventRepository.save(event);

            log.info("Email outbox event {} published successfully", event.getId());

        } catch (Exception ex) {
            int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
            retryCount++;

            event.setRetryCount(retryCount);
            event.setLastError(ex.getMessage());

            if (retryCount >= MAX_RETRIES) {
                try {
                    dlqService.sendToDlq(
                            event.getAggregateId(),
                            event.getPayload(),
                            "Email outbox publish failed after retries: " + ex.getMessage()
                    );
                    event.setStatus(OutBoxStatus.DEAD_LETTERED);


                } catch (Exception dlqException) {
                    event.setStatus(OutBoxStatus.FAILED);
                    event.setLastError("DLQ publish failed: " + dlqException.getMessage());
                }

                // Before retrying mark this as FAILED //
            } else {
                event.setStatus(OutBoxStatus.FAILED);
                event.setNextRetryAt(
                        LocalDateTime.now().plusSeconds(calculateBackoffSeconds(retryCount))
                );
            }

            outboxEventRepository.save(event);
            log.error("Failed to publish email outbox event {}", event.getId(), ex);
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