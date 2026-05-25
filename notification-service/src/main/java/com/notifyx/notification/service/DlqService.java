package com.notifyx.notification.service;

import com.notifyx.notification.producer.KafkaProducerService;
import com.notifyx.notification.util.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DlqService {

    private final KafkaProducerService kafkaProducerService;

    public void sendToDlq(Long eventId, Object originalPayload, String reason) {

        Map<String, Object> dlqPayload = Map.of(
                "eventId", eventId,
                "originalPayload", originalPayload,
                "reason", reason,
                "failedAt", LocalDateTime.now().toString()
        );

        kafkaProducerService.publish(
                KafkaTopics.NOTIFICATION_DLQ_TOPIC,
                String.valueOf(eventId),
                dlqPayload
        );
    }
}