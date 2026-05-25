package com.notifyx.notification.controller;

import com.notifyx.notification.dto.NotificationRequestDto;
import com.notifyx.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createNotification(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody NotificationRequestDto request
    ) {
        Long notificationId = notificationService.createNotification(request, idempotencyKey);

        return ResponseEntity.ok(
                Map.of(
                        "notificationId", notificationId,
                        "status", "PENDING",
                        "message", "Notification accepted successfully"
                )
        );
    }
}