package com.chae.promo.outbox.entity;

import com.chae.promo.common.entity.BaseTime;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EventOutbox extends BaseTime {

    public enum Status {
        PENDING, SENT, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String eventId;

    @Setter
    @Column(nullable = false, length = 20)
    private String type;                // "ORDER_PLACED"

    @Column(nullable = false, length = 36)
    private String aggregateId;         // orderPublicId

    @Lob
    @Column(nullable = false)
    private String payloadJson;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Setter
    @Column(nullable = false)
    private int retryCount;

    @Setter
    @Column(nullable = false)
    private LocalDateTime nextRetryAt;

    @Setter
    @Column(length = 512)
    private String lastError;


}
