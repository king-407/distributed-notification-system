package com.notifyx.email.repository;

import com.notifyx.email.entity.EmailStatus;
import com.notifyx.email.entity.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {

    Optional<SentEmail> findByNotificationId(Long notificationId);

    List<SentEmail> findByStatusAndNextRetryAtLessThanEqual(
            EmailStatus status,
            LocalDateTime now
    );
}