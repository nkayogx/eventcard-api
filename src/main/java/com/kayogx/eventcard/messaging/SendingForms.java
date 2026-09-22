package com.kayogx.eventcard.messaging;

import com.kayogx.eventcard.billing.MessageChannel;
import com.kayogx.eventcard.guest.RsvpStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The shapes of sending data sent to and from the API. */
public final class SendingForms {

    private SendingForms() {
    }

    /** Which guests to send to. */
    public enum Who {
        ALL,
        /** Guests who never had a message. */
        NOT_SENT,
        /** Guests whose latest message failed. */
        FAILED,
        /** Guests matching the card type / group / RSVP filters. */
        FILTER,
        /** The guests listed in guestIds. */
        SELECTED
    }

    public record SendRequest(

            @NotNull(message = "Please choose who to send to")
            Who who,

            UUID cardTypeId,
            String group,
            RsvpStatus rsvp,
            List<UUID> guestIds,

            @NotNull(message = "Please choose WhatsApp, SMS or both")
            SendChannel channel
    ) {
    }

    public record SendToGuestRequest(

            @NotNull(message = "Please choose WhatsApp, SMS or both")
            SendChannel channel
    ) {
    }

    public record MessageSettingsRequest(

            @NotNull(message = "Please choose a language")
            MessageLanguage language,

            @Size(max = 800, message = "The SMS text can be at most 800 characters (5 SMS)")
            String smsText
    ) {
    }

    /** A guest who will not get a message this time, and why. */
    public record SkippedGuest(UUID guestId, String nameOnCard, String reason) {
    }

    /** What pressing "Send" would do - nothing has been sent yet. */
    public record SendPreview(int guestCount, long creditsNeeded, long creditBalance, boolean enoughCredits,
                              int skippedCount, List<SkippedGuest> skippedExamples,
                              String sampleSms, int sampleSmsParts, String sampleWhatsApp,
                              String cannotSendReason) {
    }

    public record BatchDetails(UUID id, SendChannel channel, String description, int messageCount,
                               long creditsCharged, Instant createdAt) {

        static BatchDetails from(SendBatch batch) {
            return new BatchDetails(batch.getId(), batch.getChannel(), batch.getDescription(), batch.getMessageCount(),
                    batch.getCreditsCharged(), batch.getCreatedAt());
        }
    }

    public record MessageDetails(UUID id, UUID guestId, String guestName, MessageChannel channel, String toPhone,
                                 String text, MessageStatus status, String failureReason, int creditsCharged,
                                 boolean creditsRefunded, Instant queuedAt, Instant sentAt, Instant deliveredAt,
                                 Instant readAt) {
    }

    /**
     * The "Send cards" tab: how many messages are in each status, recent batches and failures,
     * and the event's message settings.
     */
    public record SendingOverview(Map<MessageStatus, Long> totals, List<BatchDetails> recentBatches,
                                  List<MessageDetails> recentFailures, String cannotSendReason,
                                  MessageLanguage language, String smsText, String standardSmsWording,
                                  String whatsAppWording, List<String> placeholders) {
    }
}
