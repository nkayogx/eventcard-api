package com.kayogx.eventcard.messaging;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One "Send cards" action: who pressed it, for whom, how, and what it cost. */
@Entity
@Table(name = "send_batches")
@Getter
@Setter
public class SendBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SendChannel channel;

    /** Who was chosen, in words, e.g. "Guests not sent yet". */
    @Column(nullable = false, length = 200)
    private String description;

    private int messageCount;

    private long creditsCharged;

    private UUID createdByUserId;

    @CreationTimestamp
    private Instant createdAt;
}
