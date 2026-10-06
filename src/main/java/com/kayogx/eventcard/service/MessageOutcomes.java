package com.kayogx.eventcard.service;

import com.kayogx.eventcard.model.Company;
import com.kayogx.eventcard.model.CreditMovement;
import com.kayogx.eventcard.model.Event;
import com.kayogx.eventcard.model.Guest;
import com.kayogx.eventcard.model.Message;
import com.kayogx.eventcard.model.MessageChannel;
import com.kayogx.eventcard.model.MessageStatus;
import com.kayogx.eventcard.repository.CompanyRepository;
import com.kayogx.eventcard.repository.EventRepository;
import com.kayogx.eventcard.repository.GuestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Records what happened to a message: sent, delivered, read or failed.
 * Used by the worker (right after sending) and by delivery reports from WhatsApp and Beem.
 *
 * A failed message gets its credits back (once only), and a WhatsApp message that
 * failed can be replaced by an SMS if the vendor chose "WhatsApp, then SMS".
 */
@Component
@Slf4j
public class MessageOutcomes {

    private final CreditAccount creditAccount;
    private final MessageQueue messageQueue;
    private final GuestRepository guestRepository;
    private final EventRepository eventRepository;
    private final CompanyRepository companyRepository;

    public MessageOutcomes(CreditAccount creditAccount,
                           MessageQueue messageQueue,
                           GuestRepository guestRepository,
                           EventRepository eventRepository,
                           CompanyRepository companyRepository) {
        this.creditAccount = creditAccount;
        this.messageQueue = messageQueue;
        this.guestRepository = guestRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
    }

    public void markSent(Message message, String providerMessageId) {
        message.setProviderMessageId(providerMessageId);
        message.setSentAt(Instant.now());
        moveTo(message, MessageStatus.SENT);
    }

    public void markDelivered(Message message) {
        if (moveTo(message, MessageStatus.DELIVERED)) {
            message.setDeliveredAt(Instant.now());
        }
    }

    public void markRead(Message message) {
        if (moveTo(message, MessageStatus.READ)) {
            message.setReadAt(Instant.now());
        }
    }

    /** The message cannot be delivered: give the credits back, and try SMS if the vendor asked for that. */
    public void markFailed(Message message, String reason) {
        boolean alreadyArrived = message.getStatus() == MessageStatus.DELIVERED || message.getStatus() == MessageStatus.READ;
        if (message.getStatus() == MessageStatus.FAILED || alreadyArrived) {
            return;
        }
        message.setStatus(MessageStatus.FAILED);
        message.setFailureReason(reason);
        refund(message);

        if (message.isFallbackToSms() && message.getChannel() == MessageChannel.WHATSAPP) {
            sendSmsInstead(message);
        }
    }

    /** Statuses only move forward, e.g. a late "delivered" report never overwrites "read". */
    private static boolean moveTo(Message message, MessageStatus newStatus) {
        if (message.getStatus() == MessageStatus.FAILED || !message.getStatus().isBefore(newStatus)) {
            return false;
        }
        message.setStatus(newStatus);
        return true;
    }

    private void refund(Message message) {
        if (message.isCreditsRefunded() || message.getCreditsCharged() == 0) {
            return;
        }
        creditAccount.add(message.getCompanyId(), message.getCreditsCharged(), CreditMovement.Reason.MESSAGE_REFUND,
                "Refund: " + message.getChannel().name().toLowerCase() + " to " + message.getToPhone() + " failed", null, null);
        message.setCreditsRefunded(true);
    }

    private void sendSmsInstead(Message failedWhatsApp) {
        Guest guest = guestRepository.findById(failedWhatsApp.getGuestId()).orElse(null);
        Event event = eventRepository.findById(failedWhatsApp.getEventId()).orElse(null);
        Company company = companyRepository.findById(failedWhatsApp.getCompanyId()).orElse(null);
        if (guest == null || event == null || company == null) {
            return;
        }

        MessageQueue.PlannedMessage sms = messageQueue.plan(guest, event, company, MessageChannel.SMS);
        // Check first (instead of catching the refusal), so the refund above is never undone
        if (company.getCreditBalance() < sms.credits()) {
            failedWhatsApp.setFailureReason(failedWhatsApp.getFailureReason() + " (no SMS sent: not enough credits)");
            return;
        }
        creditAccount.spend(company.getId(), sms.credits(), CreditMovement.Reason.MESSAGE_SENT,
                "SMS instead of WhatsApp to " + guest.getNameOnCard(), null);
        messageQueue.queue(sms, event, false, failedWhatsApp.getBatchId());
        log.info("WhatsApp to {} failed; an SMS was queued instead", guest.getPhone());
    }
}
