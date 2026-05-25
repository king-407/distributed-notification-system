package com.notifyx.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequestDto {

    @Email
    @NotBlank
    private String recipient;

    @NotBlank
    private String subject;

    @NotBlank
    private String message;
}