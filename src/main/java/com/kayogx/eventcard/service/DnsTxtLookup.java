package com.kayogx.eventcard.service;

import java.util.List;

/**
 * Reads DNS TXT records from the internet.
 * It is an interface so tests can replace it with a fake answer.
 */
public interface DnsTxtLookup {

    /** Returns all TXT values found at the given host name (empty list if none). */
    List<String> findTxtRecords(String hostName);
}
