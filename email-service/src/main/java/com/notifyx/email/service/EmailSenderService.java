package com.notifyx.email.service;

import com.notifyx.email.entity.SentEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailSenderService {

    public void sendEmail(SentEmail sentEmail) {

        if (sentEmail.getRecipient() == null || !sentEmail.getRecipient().contains("@")) {
            throw new IllegalArgumentException("Invalid recipient email");
        }

        log.info("Sending email to: {}", sentEmail.getRecipient());
        log.info("Subject: {}", sentEmail.getSubject());
        log.info("Message: {}", sentEmail.getMessage());

        // Actual JavaMailSender integration can be added later.
    }
}