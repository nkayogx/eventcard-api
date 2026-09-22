package com.kayogx.eventcard.guest;

import com.kayogx.eventcard.billing.PlanLimits;
import com.kayogx.eventcard.common.PhoneNumbers;
import com.kayogx.eventcard.company.CurrentCompany;
import com.kayogx.eventcard.event.CardType;
import com.kayogx.eventcard.event.CardTypeRepository;
import com.kayogx.eventcard.event.Event;
import com.kayogx.eventcard.event.EventFinder;
import com.kayogx.eventcard.guest.GuestFileReader.FileRow;
import com.kayogx.eventcard.guest.ImportResponses.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Uploading a guest list from a file, in two steps:
 *  1. check  - read the file and report which rows are ready, which have problems
 *              and which are duplicates. Nothing is saved.
 *  2. import - do exactly the same checks again, then save the ready rows.
 *
 * Both steps use {@link #sortRows} so the rules live in one place.
 */
@Service
public class GuestImportService {

    private static final int EXAMPLES_TO_SHOW = 10;

    private final GuestFileReader fileReader;
    private final GuestRepository guestRepository;
    private final CardTypeRepository cardTypeRepository;
    private final EventFinder eventFinder;
    private final CurrentCompany currentCompany;
    private final PlanLimits planLimits;

    public GuestImportService(GuestFileReader fileReader,
                              GuestRepository guestRepository,
                              CardTypeRepository cardTypeRepository,
                              EventFinder eventFinder,
                              CurrentCompany currentCompany,
                              PlanLimits planLimits) {
        this.fileReader = fileReader;
        this.guestRepository = guestRepository;
        this.cardTypeRepository = cardTypeRepository;
        this.eventFinder = eventFinder;
        this.currentCompany = currentCompany;
        this.planLimits = planLimits;
    }

    @Transactional(readOnly = true)
    public ImportPreview check(UUID eventId, MultipartFile file) {
        Event event = eventFinder.findChangeableEvent(eventId);
        SortedRows sorted = sortRows(event, fileReader.read(file));

        List<ReadyRow> examples = sorted.ready().stream()
                .limit(EXAMPLES_TO_SHOW)
                .map(ReadyGuest::toReadyRow)
                .toList();
        return new ImportPreview(sorted.ready().size(), examples, sorted.problems(), sorted.duplicates(),
                planLimits.remainingGuests(currentCompany.get(), eventId));
    }

    @Transactional
    public ImportResult importGuests(UUID eventId, MultipartFile file) {
        Event event = eventFinder.findChangeableEvent(eventId);
        SortedRows sorted = sortRows(event, fileReader.read(file));
        // All or nothing: an import that would go over the plan's guest limit is refused as a whole
        planLimits.checkCanAddGuests(currentCompany.get(), eventId, sorted.ready().size());

        List<Guest> newGuests = sorted.ready().stream()
                .map(ready -> ready.toGuest(event))
                .toList();
        guestRepository.saveAll(newGuests);
        return new ImportResult(newGuests.size(), sorted.problems(), sorted.duplicates());
    }

    // ---------- Sorting the rows into ready / problem / duplicate ----------

    /** A row that passed every check, with its card type looked up. */
    private record ReadyGuest(int row, String nameOnCard, String phone, CardType cardType, String groupName, String notes) {

        ReadyRow toReadyRow() {
            return new ReadyRow(row, nameOnCard, phone, cardType.getName(), groupName);
        }

        Guest toGuest(Event event) {
            Guest guest = new Guest();
            guest.setCompanyId(event.getCompanyId());
            guest.setEventId(event.getId());
            guest.setNameOnCard(nameOnCard);
            guest.setPhone(phone);
            guest.setCardTypeId(cardType.getId());
            guest.setGroupName(groupName);
            guest.setNotes(notes);
            return guest;
        }
    }

    private record SortedRows(List<ReadyGuest> ready, List<ImportProblem> problems, List<ImportDuplicate> duplicates) {
    }

    private SortedRows sortRows(Event event, List<FileRow> rows) {
        String countryCode = currentCompany.get().getCountryCode();
        List<CardType> cardTypes = cardTypeRepository.findByEventIdOrderBySortOrderAsc(event.getId());
        CardType defaultCardType = cardTypes.get(0);  // used when the card type cell is empty

        // Phones already on the list; we add each new phone as we go, to catch duplicates inside the file too
        Set<String> phonesSeen = new HashSet<>(guestRepository.findPhonesOfEvent(event.getId()));

        List<ReadyGuest> ready = new ArrayList<>();
        List<ImportProblem> problems = new ArrayList<>();
        List<ImportDuplicate> duplicates = new ArrayList<>();

        for (FileRow row : rows) {
            String problem = findProblem(row, countryCode, cardTypes);
            if (problem != null) {
                problems.add(new ImportProblem(row.rowNumber(), problem));
                continue;
            }

            String phone = PhoneNumbers.toInternationalFormat(row.phone(), countryCode).orElseThrow();
            if (!phonesSeen.add(phone)) {
                duplicates.add(new ImportDuplicate(row.rowNumber(), row.name(), phone));
                continue;
            }

            CardType cardType = row.cardType().isBlank()
                    ? defaultCardType
                    : findCardTypeByName(cardTypes, row.cardType()).orElseThrow();
            ready.add(new ReadyGuest(row.rowNumber(), row.name(), phone, cardType,
                    GuestService.blankToNull(row.group()), GuestService.blankToNull(row.notes())));
        }
        return new SortedRows(ready, problems, duplicates);
    }

    /** Returns a plain-language description of what is wrong with the row, or null if it is fine. */
    private static String findProblem(FileRow row, String countryCode, List<CardType> cardTypes) {
        if (row.name().isBlank()) {
            return "Name is missing";
        }
        if (row.name().length() > 150) {
            return "Name is longer than 150 characters";
        }
        if (row.phone().isBlank()) {
            return "Phone number is missing";
        }
        if (PhoneNumbers.toInternationalFormat(row.phone(), countryCode).isEmpty()) {
            return "Phone number \"" + row.phone() + "\" is not valid";
        }
        if (!row.cardType().isBlank() && findCardTypeByName(cardTypes, row.cardType()).isEmpty()) {
            return "Card type \"" + row.cardType() + "\" does not exist for this event";
        }
        if (row.group().length() > 60) {
            return "Group is longer than 60 characters";
        }
        if (row.notes().length() > 500) {
            return "Notes are longer than 500 characters";
        }
        return null;
    }

    private static Optional<CardType> findCardTypeByName(List<CardType> cardTypes, String name) {
        return cardTypes.stream()
                .filter(cardType -> cardType.getName().equalsIgnoreCase(name.trim()))
                .findFirst();
    }
}
