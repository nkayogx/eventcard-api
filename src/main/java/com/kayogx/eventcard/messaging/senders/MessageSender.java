package com.kayogx.eventcard.messaging.senders;

import com.kayogx.eventcard.billing.MessageChannel;

/**
 * Sends a message through an outside service (WhatsApp, an SMS company...).
 *
 * The app has one sender per channel; which one is chosen in the settings
 * (see {@link SendersSetup}): the real service, or {@link PretendSender} for trying things out.
 */
public interface MessageSender {

    MessageChannel channel();

    /**
     * @return the service's id for the message, so its delivery reports can be matched later
     * @throws SendFailure if the message could not be sent
     */
    SendResult send(OutgoingMessage message) throws SendFailure;

    /**
     * @param providerMessageId the id the service gave the message
     * @param alreadyDelivered  true if the service tells us at once that it was delivered
     */
    record SendResult(String providerMessageId, boolean alreadyDelivered) {
    }

    /**
     * Sending failed. {@code temporary} failures (network trouble, too many messages at once)
     * are tried again later; permanent ones (e.g. "this number is not on WhatsApp") are not.
     */
    class SendFailure extends Exception {

        private final boolean temporary;

        public SendFailure(String reasonInPlainWords, boolean temporary) {
            super(reasonInPlainWords);
            this.temporary = temporary;
        }

        public boolean isTemporary() {
            return temporary;
        }
    }
}
