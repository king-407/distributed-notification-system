package com.notify.common.dto;

import com.notify.common.enums.EvenType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEventDto {

    private EvenType eventType;
    private Long notificationId;
    private String recipient;
    private String subject;
    private String message;
}