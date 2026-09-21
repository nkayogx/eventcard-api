package com.kayogx.eventcard.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** A celebration or gathering that a company sends invitation cards for. */
@Entity
@Table(name = "events")
@Getter
@Setter
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The company that owns this event. Filled in and filtered automatically. */
    @TenantId
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    /** The people inviting, e.g. "Mr & Mrs Salim". */
    @Column(length = 150)
    private String hostNames;

    /**
     * When the event starts, as the local clock time at the venue (e.g. 4:00 PM).
     * Together with {@link #timeZone} this is always shown exactly as the vendor typed it.
     */
    @Column(nullable = false)
    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    /** Copied from the company when the event is created, e.g. "Africa/Dar_es_Salaam". */
    @Column(nullable = false)
    private String timeZone;

    @Column(nullable = false, length = 150)
    private String venueName;

    private String venueAddress;

    /** A Google Maps (or similar) link so guests can find the venue. */
    @Column(length = 500)
    private String mapLink;

    @Column(length = 100)
    private String dressCode;

    @Column(length = 2000)
    private String extraInfo;

    private String contactPhone;

    private LocalDate rsvpDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    @Column(nullable = false)
    private UUID createdByUserId;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
