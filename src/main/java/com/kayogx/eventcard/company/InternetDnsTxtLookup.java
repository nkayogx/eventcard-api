package com.kayogx.eventcard.company;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/** The real DNS lookup, using Java's built-in DNS support (no extra library needed). */
@Component
@Slf4j
public class InternetDnsTxtLookup implements DnsTxtLookup {

    @Override
    public List<String> findTxtRecords(String hostName) {
        Hashtable<String, String> settings = new Hashtable<>();
        settings.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");

        List<String> values = new ArrayList<>();
        try {
            Attributes records = new InitialDirContext(settings).getAttributes(hostName, new String[]{"TXT"});
            Attribute txtRecords = records.get("TXT");
            if (txtRecords == null) {
                return values;
            }
            NamingEnumeration<?> allValues = txtRecords.getAll();
            while (allValues.hasMore()) {
                // DNS may return the value wrapped in quotes - we remove them
                values.add(allValues.next().toString().replace("\"", "").trim());
            }
        } catch (NamingException lookupFailed) {
            log.info("No TXT record found for {}: {}", hostName, lookupFailed.getMessage());
        }
        return values;
    }
}
