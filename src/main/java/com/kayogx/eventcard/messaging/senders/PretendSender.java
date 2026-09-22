package com.kayogx.eventcard.messaging.senders;

import com.kayogx.eventcard.billing.MessageChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Pretends to send: writes the message to the log and says it was delivered.
 * Used while building and testing, so nothing costs money and no real phone gets a message.
 *
 * Special phone numbers, handy for trying out failures:
 *   - ending in 0000: WhatsApp fails for good ("This number is not on WhatsApp") - SMS works
 *   - ending in 1111: SMS fails for good ("This phone number cannot receive SMS") - WhatsApp works
 *   - ending in 9999: both fail for now (tried again later)
 */
@Slf4j
public class PretendSender implements MessageSender {

    private final MessageChannel channel;

    public PretendSender(MessageChannel channel) {
        this.channel = channel;
    }

    @Override
    public MessageChannel channel() {
        return channel;
    }

    @Override
    public SendResult send(OutgoingMessage message) throws SendFailure {
        if (channel == MessageChannel.WHATSAPP && message.toPhone().endsWith("0000")) {
            throw new SendFailure("This number is not on WhatsApp", false);
        }
        if (channel == MessageChannel.SMS && message.toPhone().endsWith("1111")) {
            throw new SendFailure("This phone number cannot receive SMS", false);
        }
        if (message.toPhone().endsWith("9999")) {
            throw new SendFailure("The service is busy, trying again later", true);
        }
        log.info("[PRETEND {}] to {}: {}", channel, message.toPhone(), message.text());
        return new SendResult("pretend-" + UUID.randomUUID(), true);
    }
}
