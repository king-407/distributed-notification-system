package com.notifyx.email.scheduler;

import com.notifyx.email.entity.EmailStatus;
import com.notifyx.email.entity.SentEmail;
import com.notifyx.email.repository.SentEmailRepository;
import com.notifyx.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailRetryScheduler {

    private final SentEmailRepository sentEmailRepository;
    private final EmailService emailService;

    @Scheduled(fixedDelay = 5000)
    public void retryPendingEmails() {
        List<SentEmail> emails = sentEmailRepository
                .findByStatusAndNextRetryAtLessThanEqual(
                        EmailStatus.RETRY_PENDING,
                        LocalDateTime.now()
                );

        for (SentEmail email : emails) {
            log.info("Retrying email for notificationId={}", email.getNotificationId());
            emailService.retryEmail(email);
        }
    }
}