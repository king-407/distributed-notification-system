package com.notify.common.dto;

import com.notify.common.enums.EvenType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationStatusEventDto {

    private EvenType eventType;
    private Long notificationId;
}