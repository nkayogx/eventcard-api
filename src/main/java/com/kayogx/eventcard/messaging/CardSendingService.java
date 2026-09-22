package com.kayogx.eventcard.messaging;

import com.kayogx.eventcard.auth.LoggedInUser;
import com.kayogx.eventcard.billing.CreditAccount;
import com.kayogx.eventcard.billing.CreditMovement;
import com.kayogx.eventcard.billing.MessageChannel;
import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.InvalidInputException;
import com.kayogx.eventcard.common.NotAllowedException;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.company.CurrentCompany;
import com.kayogx.eventcard.event.Event;
import com.kayogx.eventcard.event.EventFinder;
import com.kayogx.eventcard.event.EventStatus;
import com.kayogx.eventcard.guest.Guest;
import com.kayogx.eventcard.guest.GuestRepository;
import com.kayogx.eventcard.messaging.MessageQueue.PlannedMessage;
import com.kayogx.eventcard.messaging.SendingForms.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * The vendor's side of sending cards: choose guests, see what it costs, send,
 * follow the progress, and set the message wording.
 */
@Service
public class CardSendingService {

    private static final int SKIPPED_EXAMPLES = 20;

    private final EventFinder eventFinder;
    private final CurrentCompany currentCompany;
    private final GuestRepository guestRepository;
    private final MessageRepository messageRepository;
    private final SendBatchRepository batchRepository;
    private final MessageQueue messageQueue;
    private final CreditAccount creditAccount;

    public CardSendingService(EventFinder eventFinder,
                              CurrentCompany currentCompany,
                              GuestRepository guestRepository,
                              MessageRepository messageRepository,
                              SendBatchRepository batchRepository,
                              MessageQueue messageQueue,
                              CreditAccount creditAccount) {
        this.eventFinder = eventFinder;
        this.currentCompany = currentCompany;
        this.guestRepository = guestRepository;
        this.messageRepository = messageRepository;
        this.batchRepository = batchRepository;
        this.messageQueue = messageQueue;
        this.creditAccount = creditAccount;
    }

    /** What pressing "Send" would do. Nothing is sent or charged. */
    @Transactional(readOnly = true)
    public SendPreview preview(UUID eventId, SendRequest request) {
        Event event = eventFinder.findEvent(eventId);
        Company company = currentCompany.get();
        Selection selection = choose(event, request);
        List<PlannedMessage> plans = plan(selection.guests(), event, company, request.channel());

        String sampleSms = null;
        int sampleParts = 0;
        String sampleWhatsApp = null;
        if (!selection.guests().isEmpty()) {
            Guest sampleGuest = selection.guests().get(0);
            PlannedMessage sms = messageQueue.plan(sampleGuest, event, company, MessageChannel.SMS);
            sampleSms = sms.text();
            sampleParts = sms.smsParts();
            sampleWhatsApp = messageQueue.plan(sampleGuest, event, company, MessageChannel.WHATSAPP).text();
        }

        long creditsNeeded = totalCredits(plans);
        return new SendPreview(plans.size(), creditsNeeded, company.getCreditBalance(),
                company.getCreditBalance() >= creditsNeeded, selection.skipped().size(),
                selection.skipped().stream().limit(SKIPPED_EXAMPLES).toList(),
                sampleSms, sampleParts, sampleWhatsApp, reasonWhySendingIsNotPossible(event, company));
    }

    /** Charges the credits and queues one message per chosen guest. The worker sends them. */
    @Transactional
    public BatchDetails send(UUID eventId, SendRequest request) {
        Event event = eventFinder.findEvent(eventId);
        Company company = currentCompany.get();
        checkSendingIsPossible(event, company);

        Selection selection = choose(event, request);
        if (selection.guests().isEmpty()) {
            throw new InvalidInputException("No guests to send to. Guests already on their way are skipped.", "who");
        }
        List<PlannedMessage> plans = plan(selection.guests(), event, company, request.channel());
        long credits = totalCredits(plans);

        // One line on the credit statement for the whole batch. Refused as a whole if credits are short.
        creditAccount.spend(company.getId(), credits, CreditMovement.Reason.MESSAGE_SENT,
                "Cards for " + event.getName() + ": " + plans.size() + " " + channelInWords(request.channel()),
                LoggedInUser.current().userId());

        SendBatch batch = new SendBatch();
        batch.setCompanyId(company.getId());
        batch.setEventId(eventId);
        batch.setChannel(request.channel());
        batch.setDescription(selection.description());
        batch.setMessageCount(plans.size());
        batch.setCreditsCharged(credits);
        batch.setCreatedByUserId(LoggedInUser.current().userId());
        batchRepository.save(batch);

        boolean fallbackToSms = request.channel() == SendChannel.WHATSAPP_THEN_SMS;
        plans.forEach(plan -> messageQueue.queue(plan, event, fallbackToSms, batch.getId()));
        return BatchDetails.from(batch);
    }

    /** The quick "Send" / "Resend" for one guest. */
    @Transactional
    public MessageDetails sendToGuest(UUID eventId, UUID guestId, SendToGuestRequest request) {
        Event event = eventFinder.findEvent(eventId);
        Company company = currentCompany.get();
        checkSendingIsPossible(event, company);
        Guest guest = guestRepository.findByIdAndEventId(guestId, eventId)
                .orElseThrow(() -> new NotFoundException("Guest not found"));

        Message latest = latestMessagePerGuest(eventId).get(guestId);
        if (latest != null && latest.getStatus().isOnItsWay()) {
            throw new ConflictException("A message to this guest is already on its way");
        }

        PlannedMessage plan = plan(List.of(guest), event, company, request.channel()).get(0);
        creditAccount.spend(company.getId(), plan.credits(), CreditMovement.Reason.MESSAGE_SENT,
                "Card for " + guest.getNameOnCard() + " (" + channelInWords(request.channel()) + ")",
                LoggedInUser.current().userId());
        Message message = messageQueue.queue(plan, event, request.channel() == SendChannel.WHATSAPP_THEN_SMS, null);
        return detailsOf(message, guest.getNameOnCard());
    }

    @Transactional(readOnly = true)
    public SendingOverview overview(UUID eventId) {
        Event event = eventFinder.findEvent(eventId);
        Map<MessageStatus, Long> totals = new EnumMap<>(MessageStatus.class);
        for (MessageStatus status : MessageStatus.values()) {
            totals.put(status, 0L);
        }
        for (Object[] row : messageRepository.countByStatus(eventId)) {
            totals.put((MessageStatus) row[0], (Long) row[1]);
        }

        List<Message> failures = messageRepository.findTop20ByEventIdAndStatusOrderByQueuedAtDesc(eventId, MessageStatus.FAILED);
        Map<UUID, String> names = guestNames(failures.stream().map(Message::getGuestId).toList());

        return new SendingOverview(totals,
                batchRepository.findTop10ByEventIdOrderByCreatedAtDesc(eventId).stream().map(BatchDetails::from).toList(),
                failures.stream().map(message -> detailsOf(message, names.get(message.getGuestId()))).toList(),
                reasonWhySendingIsNotPossible(event, currentCompany.get()),
                event.getMessageLanguage(), event.getSmsText(),
                MessageWording.standardSmsWording(event.getMessageLanguage()),
                MessageWording.whatsAppWording(event.getMessageLanguage()),
                MessageWording.PLACEHOLDERS);
    }

    @Transactional(readOnly = true)
    public List<MessageDetails> guestMessages(UUID eventId, UUID guestId) {
        eventFinder.findEvent(eventId);
        Guest guest = guestRepository.findByIdAndEventId(guestId, eventId)
                .orElseThrow(() -> new NotFoundException("Guest not found"));
        return messageRepository.findByEventIdAndGuestIdOrderByQueuedAtDesc(eventId, guestId).stream()
                .map(message -> detailsOf(message, guest.getNameOnCard()))
                .toList();
    }

    @Transactional
    public SendingOverview updateSettings(UUID eventId, MessageSettingsRequest request) {
        Event event = eventFinder.findChangeableEvent(eventId);
        String smsText = request.smsText() == null || request.smsText().isBlank() ? null : request.smsText().trim();
        if (smsText != null) {
            MessageWording.checkPlaceholders(smsText);
        }
        event.setMessageLanguage(request.language());
        event.setSmsText(smsText);
        return overview(eventId);
    }

    // ---------- choosing guests ----------

    /** The chosen guests, the ones left out (with the reason), and the choice in words. */
    private record Selection(List<Guest> guests, List<SkippedGuest> skipped, String description) {
    }

    private Selection choose(Event event, SendRequest request) {
        List<Guest> allGuests = guestRepository.findByEventIdOrderByNameOnCardAsc(event.getId());
        Map<UUID, Message> latest = latestMessagePerGuest(event.getId());
        Set<UUID> selectedIds = request.guestIds() == null ? Set.of() : new HashSet<>(request.guestIds());

        List<Guest> chosen = new ArrayList<>();
        List<SkippedGuest> skipped = new ArrayList<>();
        for (Guest guest : allGuests) {
            Message lastMessage = latest.get(guest.getId());
            if (!matches(request, guest, lastMessage, selectedIds)) {
                continue;
            }
            if (lastMessage != null && lastMessage.getStatus().isOnItsWay()) {
                skipped.add(new SkippedGuest(guest.getId(), guest.getNameOnCard(), "A message is already on its way"));
                continue;
            }
            chosen.add(guest);
        }
        return new Selection(chosen, skipped, describe(request));
    }

    private static boolean matches(SendRequest request, Guest guest, Message lastMessage, Set<UUID> selectedIds) {
        return switch (request.who()) {
            case ALL -> true;
            case NOT_SENT -> lastMessage == null;
            case FAILED -> lastMessage != null && lastMessage.getStatus() == MessageStatus.FAILED;
            case SELECTED -> selectedIds.contains(guest.getId());
            case FILTER -> (request.cardTypeId() == null || request.cardTypeId().equals(guest.getCardTypeId()))
                    && (request.group() == null || request.group().isBlank() || request.group().equals(guest.getGroupName()))
                    && (request.rsvp() == null || request.rsvp() == guest.getRsvpStatus());
        };
    }

    private static String describe(SendRequest request) {
        return switch (request.who()) {
            case ALL -> "All guests";
            case NOT_SENT -> "Guests not sent yet";
            case FAILED -> "Guests whose card failed";
            case SELECTED -> "Chosen guests";
            case FILTER -> "Filtered guests";
        };
    }

    /** Each guest's most recent message (guests without messages are not in the map). */
    private Map<UUID, Message> latestMessagePerGuest(UUID eventId) {
        Map<UUID, Message> latest = new HashMap<>();
        for (Message message : messageRepository.findByEventIdOrderByQueuedAtAsc(eventId)) {
            latest.put(message.getGuestId(), message);   // later messages replace earlier ones
        }
        return latest;
    }

    // ---------- helpers ----------

    /** With "WhatsApp, then SMS" we charge for WhatsApp now; an SMS is only charged if it is really needed. */
    private List<PlannedMessage> plan(List<Guest> guests, Event event, Company company, SendChannel channel) {
        MessageChannel firstChannel = channel == SendChannel.SMS ? MessageChannel.SMS : MessageChannel.WHATSAPP;
        return guests.stream().map(guest -> messageQueue.plan(guest, event, company, firstChannel)).toList();
    }

    private static long totalCredits(List<PlannedMessage> plans) {
        return plans.stream().mapToLong(PlannedMessage::credits).sum();
    }

    private void checkSendingIsPossible(Event event, Company company) {
        if (event.getStatus() != EventStatus.ACTIVE) {
            throw new ConflictException("Activate the event before sending cards");
        }
        if (!company.isCanSendMessages()) {
            throw new NotAllowedException("Sending is locked until EventCard verifies your company");
        }
    }

    private static String reasonWhySendingIsNotPossible(Event event, Company company) {
        if (event.getStatus() != EventStatus.ACTIVE) {
            return "Activate the event before sending cards";
        }
        if (!company.isCanSendMessages()) {
            return "Sending is locked until EventCard verifies your company";
        }
        return null;
    }

    private static String channelInWords(SendChannel channel) {
        return switch (channel) {
            case WHATSAPP -> "WhatsApp";
            case SMS -> "SMS";
            case WHATSAPP_THEN_SMS -> "WhatsApp (SMS if it fails)";
        };
    }

    private Map<UUID, String> guestNames(Collection<UUID> guestIds) {
        Map<UUID, String> names = new HashMap<>();
        guestRepository.findAllById(guestIds).forEach(guest -> names.put(guest.getId(), guest.getNameOnCard()));
        return names;
    }

    static MessageDetails detailsOf(Message message, String guestName) {
        return new MessageDetails(message.getId(), message.getGuestId(), guestName, message.getChannel(),
                message.getToPhone(), message.getText(), message.getStatus(), message.getFailureReason(),
                message.getCreditsCharged(), message.isCreditsRefunded(), message.getQueuedAt(), message.getSentAt(),
                message.getDeliveredAt(), message.getReadAt());
    }
}
