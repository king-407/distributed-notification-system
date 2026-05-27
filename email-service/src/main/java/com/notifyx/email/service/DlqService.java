package com.notifyx.email.service;

import com.notifyx.email.producer.KafkaProducerService;
import com.notifyx.email.util.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DlqService {

    private final KafkaProducerService kafkaProducerService;

    public void sendToDlq(Long notificationId, Object originalPayload, String reason) {
        Map<String, Object> dlqPayload = Map.of(
                "notificationId", notificationId,
                "originalPayload", originalPayload,
                "reason", reason,
                "failedAt", LocalDateTime.now().toString()
        );

        kafkaProducerService.publish(
                KafkaTopics.EMAIL_DLQ_TOPIC,
                String.valueOf(notificationId),
                dlqPayload
        );
    }
}