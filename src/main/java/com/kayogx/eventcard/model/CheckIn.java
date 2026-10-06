package com.kayogx.eventcard.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry at the door: "3 people on Mr & Mrs Juma's card came in at 16:42, let in by Asha".
 * Kept as history, so mistakes can be traced and undone.
 */
@Entity
@Table(name = "check_ins")
@Getter
@Setter
public class CheckIn {

    public enum Method {
        /** The guest's QR code was scanned (camera or barcode scanner). */
        QR_SCAN,
        /** The guest was found by name or phone. */
        MANUAL_SEARCH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID guestId;

    /** How many people entered with this check-in. */
    private int people;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Method method;

    private UUID checkedInByUserId;

    @CreationTimestamp
    private Instant createdAt;

    /** Filled in when a manager undoes a mistaken check-in. */
    private Instant undoneAt;

    private UUID undoneByUserId;

    public boolean isUndone() {
        return undoneAt != null;
    }
}
