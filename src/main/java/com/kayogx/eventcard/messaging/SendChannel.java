package com.kayogx.eventcard.messaging;

/** How the vendor chose to send the cards. */
public enum SendChannel {
    WHATSAPP,
    SMS,
    /** WhatsApp first; if that fails (e.g. the number is not on WhatsApp), an SMS instead. */
    WHATSAPP_THEN_SMS
}
