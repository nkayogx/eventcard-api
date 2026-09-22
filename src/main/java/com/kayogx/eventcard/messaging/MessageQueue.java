package com.kayogx.eventcard.messaging;

import com.kayogx.eventcard.billing.MessageChannel;
import com.kayogx.eventcard.billing.MessagePrice;
import com.kayogx.eventcard.billing.MessagePriceRepository;
import com.kayogx.eventcard.card.InvitationLinks;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.event.Event;
import com.kayogx.eventcard.guest.Guest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Works out a message for a guest (its words and its cost in credits), and puts it in the queue.
 * Charging the credits is done by the caller, so a whole batch can be charged in one go.
 */
@Component
public class MessageQueue {

    private final MessageRepository messageRepository;
    private final MessagePriceRepository messagePriceRepository;
    private final InvitationLinks invitationLinks;

    public MessageQueue(MessageRepository messageRepository,
                        MessagePriceRepository messagePriceRepository,
                        InvitationLinks invitationLinks) {
        this.messageRepository = messageRepository;
        this.messagePriceRepository = messagePriceRepository;
        this.invitationLinks = invitationLinks;
    }

    /** A message worked out but not yet queued. */
    public record PlannedMessage(Guest guest, MessageChannel channel, String text, int smsParts, int credits) {
    }

    public PlannedMessage plan(Guest guest, Event event, Company company, MessageChannel channel) {
        MessageWording.Details details = MessageWording.Details.of(guest, event, company,
                invitationLinks.linkFor(company, guest.getInvitationCode()));

        if (channel == MessageChannel.WHATSAPP) {
            return new PlannedMessage(guest, channel, MessageWording.whatsAppText(event, details), 0,
                    creditsPerMessage(MessageChannel.WHATSAPP));
        }
        String text = MessageWording.smsText(event, details);
        int parts = MessageWording.smsParts(text);
        return new PlannedMessage(guest, channel, text, parts, parts * creditsPerMessage(MessageChannel.SMS));
    }

    /** Puts a planned message in the queue; the background worker will send it. */
    public Message queue(PlannedMessage plan, Event event, boolean fallbackToSms, UUID batchIdOrNull) {
        Message message = new Message();
        message.setCompanyId(event.getCompanyId());
        message.setEventId(event.getId());
        message.setGuestId(plan.guest().getId());
        message.setBatchId(batchIdOrNull);
        message.setChannel(plan.channel());
        message.setFallbackToSms(fallbackToSms && plan.channel() == MessageChannel.WHATSAPP);
        message.setToPhone(plan.guest().getPhone());
        message.setText(plan.text());
        message.setCreditsCharged(plan.credits());
        message.setStatus(MessageStatus.QUEUED);
        message.setNextAttemptAt(Instant.now());
        return messageRepository.save(message);
    }

    private int creditsPerMessage(MessageChannel channel) {
        return messagePriceRepository.findById(channel).map(MessagePrice::getCredits).orElse(1);
    }
}
