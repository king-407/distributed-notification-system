package com.notifyx.notification.entity;

import com.notify.common.enums.EvenType;
import com.notify.common.enums.OutBoxStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "outbox_events")
public class OutBoxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long aggregateId;

    private String aggregateType;

    @Enumerated(EnumType.STRING)
    private EvenType eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    private OutBoxStatus status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();

        if (this.retryCount == null) {
            this.retryCount = 0;
        }

        if (this.status == null) {
            this.status = OutBoxStatus.PENDING;
        }
    }
}