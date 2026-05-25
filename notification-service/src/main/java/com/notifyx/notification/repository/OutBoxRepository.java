package com.notifyx.notification.repository;

import com.notify.common.enums.OutBoxStatus;
import com.notifyx.notification.entity.OutBoxEvent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
@Repository
public interface OutBoxRepository
        extends JpaRepository<OutBoxEvent, Long> {

    List<OutBoxEvent>
    findByStatus(String status);

    @Query("""
           SELECT o FROM OutBoxEvent o
           WHERE o.status IN :statuses
           AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now)
           """)
    List<OutBoxEvent> findPublishableEvents(
            List<OutBoxStatus> statuses,
            LocalDateTime now
    );
}
