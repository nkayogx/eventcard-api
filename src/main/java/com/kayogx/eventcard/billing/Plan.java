package com.kayogx.eventcard.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A monthly plan (Free, Starter, Pro...), set up by the platform admin.
 * The plan decides what a company may do: how many events, guests and staff,
 * and whether it may use a custom domain or its own card artwork.
 *
 * Plans belong to the whole platform, not to one company.
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** A short fixed name for code and tests, e.g. "PRO". */
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    /** Price for one month, in Tanzanian shillings. 0 for the Free plan. */
    private long monthlyPriceTzs;

    /** Empty (null) means unlimited. */
    private Integer maxActiveEvents;

    private Integer maxGuestsPerEvent;

    /** How many people can log in, including the owner. */
    private Integer maxStaff;

    private boolean allowsCustomDomain;

    private boolean allowsOwnArtwork;

    /** Exactly one plan is the Free plan: used by new companies and when a paid plan runs out. */
    private boolean freePlan;

    /** Plans no longer on sale are hidden from buyers, but companies already on them keep them. */
    private boolean available = true;

    private int sortOrder;
}
