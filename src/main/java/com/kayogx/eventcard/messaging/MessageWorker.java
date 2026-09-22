package com.kayogx.eventcard.messaging;

import com.kayogx.eventcard.billing.MessageChannel;
import com.kayogx.eventcard.card.InvitationLinks;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.company.CompanyRepository;
import com.kayogx.eventcard.event.Event;
import com.kayogx.eventcard.event.EventRepository;
import com.kayogx.eventcard.event.EventStatus;
import com.kayogx.eventcard.guest.Guest;
import com.kayogx.eventcard.guest.GuestRepository;
import com.kayogx.eventcard.messaging.senders.MessageSender;
import com.kayogx.eventcard.messaging.senders.MessageSender.SendFailure;
import com.kayogx.eventcard.messaging.senders.MessageSender.SendResult;
import com.kayogx.eventcard.messaging.senders.OutgoingMessage;
import com.kayogx.eventcard.messaging.senders.SendersSetup;
import com.kayogx.eventcard.tenant.AllCompaniesTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The background worker that sends queued messages.
 *
 * Every 2 seconds it takes up to 20 messages that are due and sends them one by one.
 * This steady pace keeps us within WhatsApp's and the SMS company's limits.
 * It works "as all companies", because the queue holds every company's messages.
 */
@Component
@Slf4j
public class MessageWorker {

    /** How long to wait before trying again after a temporary failure (1st, 2nd, 3rd retry). */
    private static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));

    private final MessageRepository messageRepository;
    private final GuestRepository guestRepository;
    private final EventRepository eventRepository;
    private final CompanyRepository companyRepository;
    private final SendersSetup senders;
    private final MessageOutcomes outcomes;
    private final InvitationLinks invitationLinks;
    private final AllCompaniesTransaction allCompaniesTransaction;
    private final boolean enabled;
    private final String publicApiUrl;
    private final String platformSmsSender;
    private final String whatsAppTemplateSwahili;
    private final String whatsAppTemplateEnglish;

    public MessageWorker(MessageRepository messageRepository,
                         GuestRepository guestRepository,
                         EventRepository eventRepository,
                         CompanyRepository companyRepository,
                         SendersSetup senders,
                         MessageOutcomes outcomes,
                         InvitationLinks invitationLinks,
                         AllCompaniesTransaction allCompaniesTransaction,
                         @Value("${app.messaging.worker-enabled}") boolean enabled,
                         @Value("${app.public-api-url}") String publicApiUrl,
                         @Value("${app.messaging.platform-sms-sender}") String platformSmsSender,
                         @Value("${app.messaging.meta.template-sw}") String whatsAppTemplateSwahili,
                         @Value("${app.messaging.meta.template-en}") String whatsAppTemplateEnglish) {
        this.messageRepository = messageRepository;
        this.guestRepository = guestRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.senders = senders;
        this.outcomes = outcomes;
        this.invitationLinks = invitationLinks;
        this.allCompaniesTransaction = allCompaniesTransaction;
        this.enabled = enabled;
        this.publicApiUrl = publicApiUrl;
        this.platformSmsSender = platformSmsSender;
        this.whatsAppTemplateSwahili = whatsAppTemplateSwahili;
        this.whatsAppTemplateEnglish = whatsAppTemplateEnglish;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void sendOnSchedule() {
        if (enabled) {
            sendDueMessages();
        }
    }

    /** Sends the messages that are due now. Returns how many it handled. (Tests call this directly.) */
    public int sendDueMessages() {
        // First mark the messages as SENDING, so they are never picked up twice
        List<UUID> dueMessageIds = allCompaniesTransaction.run(() -> {
            List<Message> due = messageRepository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByQueuedAtAsc(
                    MessageStatus.QUEUED, Instant.now());
            due.forEach(message -> message.setStatus(MessageStatus.SENDING));
            return due.stream().map(Message::getId).toList();
        });

        for (UUID messageId : dueMessageIds) {
            try {
                allCompaniesTransaction.run(() -> {
                    sendOne(messageRepository.findById(messageId).orElseThrow());
                    return null;
                });
            } catch (RuntimeException unexpected) {
                log.error("Could not handle message {}", messageId, unexpected);
            }
        }
        return dueMessageIds.size();
    }

    private void sendOne(Message message) {
        Guest guest = guestRepository.findById(message.getGuestId()).orElse(null);
        Event event = eventRepository.findById(message.getEventId()).orElse(null);
        Company company = companyRepository.findById(message.getCompanyId()).orElseThrow();

        if (guest == null || event == null) {
            outcomes.markFailed(message, "The guest or event was deleted");
            return;
        }
        if (company.isSuspended() || !company.isCanSendMessages()) {
            outcomes.markFailed(message, "Sending is locked for this company");
            return;
        }
        if (event.getStatus() != EventStatus.ACTIVE) {
            outcomes.markFailed(message, "The event is no longer active");
            return;
        }

        MessageSender sender = senders.senderFor(message.getChannel());
        message.setAttempts(message.getAttempts() + 1);
        try {
            SendResult result = sender.send(outgoingMessage(message, guest, event, company));
            outcomes.markSent(message, result.providerMessageId());
            if (result.alreadyDelivered()) {
                outcomes.markDelivered(message);
            }
        } catch (SendFailure failure) {
            if (failure.isTemporary() && message.getAttempts() <= RETRY_DELAYS.size()) {
                // Try again later
                message.setStatus(MessageStatus.QUEUED);
                message.setFailureReason(failure.getMessage());
                message.setNextAttemptAt(Instant.now().plus(RETRY_DELAYS.get(message.getAttempts() - 1)));
            } else {
                outcomes.markFailed(message, failure.getMessage());
            }
        }
    }

    private OutgoingMessage outgoingMessage(Message message, Guest guest, Event event, Company company) {
        if (message.getChannel() == MessageChannel.SMS) {
            String sender = company.getSmsSenderName() != null ? company.getSmsSenderName() : platformSmsSender;
            return new OutgoingMessage(message.getToPhone(), message.getText(), sender, null, null, null, List.of());
        }
        MessageWording.Details details = MessageWording.Details.of(guest, event, company,
                invitationLinks.linkFor(company, guest.getInvitationCode()));
        boolean swahili = event.getMessageLanguage() == MessageLanguage.SW;
        String cardImageUrl = publicApiUrl + "/api/public/invitations/" + guest.getInvitationCode() + "/card.png";
        return new OutgoingMessage(message.getToPhone(), message.getText(), null, cardImageUrl,
                swahili ? whatsAppTemplateSwahili : whatsAppTemplateEnglish, swahili ? "sw" : "en",
                details.whatsAppValues());
    }
}
