package com.kayogx.eventcard.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/**
 * A kind of invitation card for one event, e.g. "Single" (1 seat), "Double" (2 seats)
 * or "VIP" (2 seats). Every guest entry has one card type, which says how many seats it gets.
 */
@Entity
@Table(name = "card_types")
@Getter
@Setter
public class CardType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false, length = 40)
    private String name;

    /** How many people this card lets in. */
    @Column(nullable = false)
    private int seats;

    /** Card types are shown in this order (smallest first). */
    @Column(nullable = false)
    private int sortOrder;

    /**
     * Optional special artwork for this card type (e.g. a gold VIP card), in the
     * "card-backgrounds" folder. Empty means: use the event's main artwork.
     */
    private String backgroundFile;
}
