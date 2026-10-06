package com.kayogx.eventcard.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * One WhatsApp or SMS message to one guest.
 *
 * Messages wait in this table (status QUEUED) until the background {@link MessageWorker}
 * sends them. Because the queue lives in the database, nothing is lost if the server restarts.
 */
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "messages_waiting", columnList = "status, next_attempt_at"),
        @Index(name = "messages_provider_id", columnList = "provider_message_id"),
        @Index(name = "messages_guest", columnList = "guest_id")})
@Getter
@Setter
public class Message {

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

    /** The "Send cards" action this message belongs to. Empty for a single Send/Resend. */
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageChannel channel;

    /** If this WhatsApp message fails, send an SMS instead. */
    private boolean fallbackToSms;

    @Column(nullable = false)
    private String toPhone;

    /** The final SMS text, or the WhatsApp wording (for showing in the app). */
    @Column(nullable = false, length = 1200)
    private String text;

    private int creditsCharged;

    /** True once the credits were given back (a message is refunded only once). */
    private boolean creditsRefunded;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus status = MessageStatus.QUEUED;

    /** The id WhatsApp or Beem gave the message - used to match their delivery reports. */
    private String providerMessageId;

    @Column(length = 300)
    private String failureReason;

    /** How many times sending was tried. */
    private int attempts;

    /** When the worker should (next) try to send it. */
    private Instant nextAttemptAt;

    @CreationTimestamp
    private Instant queuedAt;

    private Instant sentAt;

    private Instant deliveredAt;

    private Instant readAt;
}
