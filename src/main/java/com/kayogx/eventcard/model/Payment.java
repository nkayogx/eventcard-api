package com.kayogx.eventcard.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A company paying for something: a plan for some months, or a pack of credits.
 *
 * Life of a payment:
 *   WAITING_FOR_PAYMENT  ->  PAID       (the money arrived - plan or credits are switched on)
 *                        ->  REJECTED   (the platform admin could not find the money)
 *                        ->  CANCELLED  (the company changed its mind)
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

    public enum Type { PLAN, CREDITS }

    public enum Status { WAITING_FOR_PAYMENT, PAID, REJECTED, CANCELLED }

    public enum Method {
        /** The company pays to our Lipa Namba / till and the platform admin confirms by hand. */
        MANUAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /** Short code the company quotes when paying, e.g. "EC-7K3P9Q". */
    @Column(nullable = false, unique = true, length = 20)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    // For PLAN payments
    private UUID planId;
    private Integer months;

    // For CREDITS payments
    private UUID creditPackId;
    private Integer credits;

    /** What was bought, in words, e.g. "Pro plan - 3 months". Kept even if the plan is renamed later. */
    @Column(nullable = false, length = 120)
    private String description;

    /** The price when the order was made. Later price changes don't affect it. */
    private long amountTzs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Method method = Method.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.WAITING_FOR_PAYMENT;

    /** Filled in by the company after paying. */
    private String payerPhone;

    @Column(length = 60)
    private String transactionReference;

    private Instant submittedAt;

    private UUID confirmedByUserId;

    private Instant confirmedAt;

    @Column(length = 300)
    private String adminNote;

    private UUID createdByUserId;

    @CreationTimestamp
    private Instant createdAt;

    public boolean isWaiting() {
        return status == Status.WAITING_FOR_PAYMENT;
    }
}
