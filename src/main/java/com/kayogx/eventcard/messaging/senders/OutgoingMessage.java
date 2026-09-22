package com.kayogx.eventcard.messaging.senders;

import java.util.List;

/**
 * Everything a connector needs to send one message.
 *
 * @param toPhone        international format, e.g. "+255712345678"
 * @param text           the SMS text (for WhatsApp: the wording, for logs only)
 * @param smsSenderName  the SMS sender name (SMS only)
 * @param cardImageUrl   public address of the guest's card picture (WhatsApp only)
 * @param templateName   the approved WhatsApp template (WhatsApp only)
 * @param languageCode   "sw" or "en" (WhatsApp only)
 * @param templateValues values for the template's {{1}}, {{2}}... in order (WhatsApp only)
 */
public record OutgoingMessage(String toPhone, String text, String smsSenderName, String cardImageUrl,
                              String templateName, String languageCode, List<String> templateValues) {
}
