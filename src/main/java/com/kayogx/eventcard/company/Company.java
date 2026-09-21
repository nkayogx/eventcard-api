package com.kayogx.eventcard.company;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A vendor (event company) using the platform. Each company is one "tenant":
 * its users, events and guests are kept completely separate from other companies.
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Short name used in links, e.g. "kayo-events". Unique across the platform. */
    @Column(nullable = false, unique = true)
    private String slug;

    private String logoUrl;

    @Column(nullable = false)
    private String contactPhone;

    @Column(nullable = false)
    private String contactEmail;

    private String address;

    private String city;

    /** Two-letter country code, e.g. "TZ". Sets the default phone code. */
    @Column(nullable = false, length = 2)
    private String countryCode;

    /** Time zone for event dates, e.g. "Africa/Dar_es_Salaam". */
    @Column(nullable = false)
    private String timeZone;

    /** Brand colours as "#RRGGBB", used on the digital cards. */
    private String primaryColor;

    private String secondaryColor;

    /** The vendor's own web address for invitations, e.g. "invites.kayoevents.com". */
    @Column(unique = true)
    private String customDomain;

    private boolean customDomainVerified = false;

    /** The value the vendor must put in a DNS TXT record to prove they own the domain. */
    private String domainVerificationCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    /** Stays false until the platform admin verifies the company. Protects our SMS/WhatsApp costs. */
    private boolean canSendMessages = false;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public boolean isSuspended() {
        return accountStatus == AccountStatus.SUSPENDED;
    }
}
