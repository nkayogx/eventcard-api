package com.kayogx.eventcard.company;

import com.kayogx.eventcard.common.InvalidInputException;

import java.time.ZoneId;

/** Checks for company values that simple form rules cannot check on their own. */
public final class CompanyInputChecks {

    private CompanyInputChecks() {
    }

    /** Makes sure the time zone really exists, e.g. "Africa/Dar_es_Salaam". */
    public static void checkTimeZoneExists(String timeZone) {
        if (!ZoneId.getAvailableZoneIds().contains(timeZone)) {
            throw new InvalidInputException("Unknown time zone. Example of a valid one: Africa/Dar_es_Salaam", "timeZone");
        }
    }
}
