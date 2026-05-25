package com.notifyx.notification.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int LIMIT_PER_MINUTE = 10;

    public boolean isAllowed(String recipient) {
        String key = "rate_limit:notification:" + recipient;

        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        return count != null && count <= LIMIT_PER_MINUTE;
    }
}