package com.notifyx.email.repository;

import com.notify.common.enums.OutBoxStatus;
import com.notifyx.email.entity.OutBoxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutBoxEventRepository extends JpaRepository<OutBoxEvent, Long> {

    @Query("""
           SELECT o FROM OutboxEvent o
           WHERE o.status IN :statuses
           AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now)
           """)
    List<OutBoxEvent> findPublishableEvents(
            List<OutBoxStatus> statuses,
            LocalDateTime now
    );
}