package com.kayogx.eventcard.messaging;

import com.kayogx.eventcard.common.InvalidInputException;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.event.Event;
import com.kayogx.eventcard.guest.Guest;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words of the messages guests receive.
 *
 * Wording can contain placeholders such as {name}, which are replaced by the guest's details:
 *   "Hello {name}, {company} invites you to {event}..."  ->  "Hello Mr & Mrs Juma, Kayo Events invites you to..."
 */
public final class MessageWording {

    public static final List<String> PLACEHOLDERS = List.of("name", "company", "event", "date", "venue", "link");

    private static final Map<MessageLanguage, String> STANDARD_SMS = Map.of(
            MessageLanguage.SW, "Habari {name}! {company} inakualika {event}, {date}, {venue}. Kadi & RSVP: {link}",
            MessageLanguage.EN, "Hi {name}! {company} invites you to {event}, {date}, {venue}. Card & RSVP: {link}");

    /** What the approved WhatsApp templates say (the real text lives at Meta; this is for showing in the app). */
    private static final Map<MessageLanguage, String> WHATSAPP_WORDING = Map.of(
            MessageLanguage.SW, "Habari {name}, {company} inakualika kwenye {event}, {date}. Kadi yako na RSVP: {link}",
            MessageLanguage.EN, "Hello {name}, {company} invites you to {event} on {date}. Your card and RSVP: {link}");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private static final DateTimeFormatter ENGLISH_DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter SWAHILI_DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.forLanguageTag("sw"));

    private MessageWording() {
    }

    /** The details that go into the placeholders for one guest. */
    public record Details(String name, String company, String event, String date, String venue, String link) {

        public static Details of(Guest guest, Event event, Company company, String link) {
            return new Details(guest.getNameOnCard(), company.getName(), event.getName(),
                    formatDate(event), event.getVenueName(), link);
        }

        /** The values of the WhatsApp template, in the order the template expects them. */
        public List<String> whatsAppValues() {
            return List.of(name, company, event, date, link);
        }
    }

    public static String smsText(Event event, Details details) {
        String wording = event.getSmsText() == null || event.getSmsText().isBlank()
                ? STANDARD_SMS.get(event.getMessageLanguage())
                : event.getSmsText();
        return fillIn(wording, details);
    }

    public static String whatsAppText(Event event, Details details) {
        return fillIn(WHATSAPP_WORDING.get(event.getMessageLanguage()), details);
    }

    public static String standardSmsWording(MessageLanguage language) {
        return STANDARD_SMS.get(language);
    }

    public static String whatsAppWording(MessageLanguage language) {
        return WHATSAPP_WORDING.get(language);
    }

    /** Refuses wording with placeholders we don't know, e.g. {nmae}. */
    public static void checkPlaceholders(String wording) {
        Set<String> unknown = new LinkedHashSet<>();
        Matcher found = PLACEHOLDER.matcher(wording);
        while (found.find()) {
            if (!PLACEHOLDERS.contains(found.group(1))) {
                unknown.add("{" + found.group(1) + "}");
            }
        }
        if (!unknown.isEmpty()) {
            throw new InvalidInputException("Unknown placeholder " + String.join(", ", unknown)
                    + ". You can use: {name}, {company}, {event}, {date}, {venue}, {link}", "smsText");
        }
    }

    /**
     * How many SMS parts a text needs. Plain letters fit 160 in one SMS (153 per part when split);
     * text with other characters, such as emoji, fits only 70 (67 per part).
     */
    public static int smsParts(String text) {
        boolean plainLetters = text.chars().allMatch(letter -> letter < 128);
        int singleLimit = plainLetters ? 160 : 70;
        int perPart = plainLetters ? 153 : 67;
        if (text.length() <= singleLimit) {
            return 1;
        }
        return (text.length() + perPart - 1) / perPart;
    }

    private static String fillIn(String wording, Details details) {
        Map<String, String> values = Map.of(
                "name", details.name(), "company", details.company(), "event", details.event(),
                "date", details.date(), "venue", details.venue(), "link", details.link());
        Matcher found = PLACEHOLDER.matcher(wording);
        StringBuilder result = new StringBuilder();
        while (found.find()) {
            String value = values.getOrDefault(found.group(1), found.group());
            found.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        found.appendTail(result);
        return result.toString();
    }

    private static String formatDate(Event event) {
        DateTimeFormatter format = event.getMessageLanguage() == MessageLanguage.SW ? SWAHILI_DATE : ENGLISH_DATE;
        return format.format(event.getStartsAt());
    }
}
