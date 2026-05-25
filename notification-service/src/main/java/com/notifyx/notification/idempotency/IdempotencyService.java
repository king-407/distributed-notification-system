package com.notifyx.notification.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration TTL = Duration.ofHours(2);

    public Long getExistingNotificationId(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }

        Object value = redisTemplate.opsForValue().get(buildKey(idempotencyKey));

        if (value == null) {
            return null;
        }

        if ("PROCESSING".equals(value.toString())) {
            throw new IllegalStateException("Request is already being processed");
        }

        return Long.valueOf(value.toString());
    }

    // If idempotency key is not provided just do the job. If it is provided then it will just mark the req
    // as processing //
    public boolean lockRequest(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true;
        }

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(buildKey(idempotencyKey), "PROCESSING", TTL);

        return Boolean.TRUE.equals(success);
    }

    public void markCompleted(String idempotencyKey, Long notificationId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        redisTemplate.opsForValue()
                .set(buildKey(idempotencyKey), notificationId.toString(), TTL);
    }

    private String buildKey(String idempotencyKey) {
        return "idempotency:notification:" + idempotencyKey;
    }
}