package com.kayogx.eventcard.guest;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One invitation card on an event's guest list, e.g. "Mr & Mrs Juma" with a Double card.
 * A card can cover several people - its card type says how many seats.
 */
@Entity
@Table(name = "guests",
        // The same phone number may appear only once per event
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "phone"}))
@Getter
@Setter
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** Exactly as it should appear on the card, e.g. "Mr & Mrs Juma". */
    @Column(nullable = false, length = 150)
    private String nameOnCard;

    /** Always in international format, e.g. "+255712345678". */
    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private UUID cardTypeId;

    /** Optional grouping, e.g. "Bride's side". */
    @Column(length = 60)
    private String groupName;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
