package com.kayogx.eventcard.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * One line on a company's credit statement - like a line on a bank statement.
 * Lines are only ever added, never changed, so the history can always be trusted.
 */
@Entity
@Table(name = "credit_movements")
@Getter
@Setter
public class CreditMovement {

    public enum Reason {
        /** Credits bought (a confirmed payment). */
        PURCHASE,
        /** Credits used to send a message. */
        MESSAGE_SENT,
        /** Credits given back because a message could not be sent. */
        MESSAGE_REFUND,
        /** Added or removed by the platform admin, e.g. a gift or a correction. */
        ADMIN_ADJUSTMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /** Positive when credits are added, negative when they are used. */
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Reason reason;

    /** The company's balance right after this movement. */
    private long balanceAfter;

    @Column(length = 200)
    private String note;

    private UUID paymentId;

    private UUID createdByUserId;

    @CreationTimestamp
    private Instant createdAt;
}
