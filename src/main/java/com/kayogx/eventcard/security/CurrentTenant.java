package com.kayogx.eventcard.security;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Remembers which company ("tenant") the current request is working for.
 *
 * How it is used:
 *  - When a logged-in request arrives, JwtAuthFilter calls {@link #set(UUID)}
 *    with the user's company, and {@link #clear()} when the request is finished.
 *  - The database layer ({@link HibernateTenantSetup}) asks {@link #companyIdForDatabase()}
 *    and automatically shows only that company's rows.
 *
 * Every request runs on its own thread, so we store the value per thread
 * (a ThreadLocal). Two requests from two companies never see each other's value.
 */
public final class CurrentTenant {

    /**
     * Special value meaning "all companies". Used by platform admins and by a few
     * system tasks such as login, where we don't know the company yet.
     */
    public static final UUID ALL_COMPANIES = new UUID(0, 0);

    /**
     * Special value meaning "no company chosen". It matches no rows at all.
     * This is our safety net: if some code forgets to set a company,
     * it sees nothing - instead of seeing everything.
     */
    public static final UUID NO_COMPANY = new UUID(0, 1);

    private static final ThreadLocal<UUID> companyForThisRequest = new ThreadLocal<>();

    private CurrentTenant() {
    }

    public static void set(UUID companyId) {
        companyForThisRequest.set(companyId);
    }

    public static void clear() {
        companyForThisRequest.remove();
    }

    /** The company the database should filter by right now. */
    public static UUID companyIdForDatabase() {
        UUID companyId = companyForThisRequest.get();
        return companyId != null ? companyId : NO_COMPANY;
    }

    /**
     * Runs a piece of work that must be able to see data from every company,
     * then puts the previous company back.
     *
     * Important: the company filter is chosen when a database session opens,
     * so the work must open its own transaction (see {@link AllCompaniesTransaction}).
     */
    public static <T> T runAsAllCompanies(Supplier<T> work) {
        UUID previousCompany = companyForThisRequest.get();
        set(ALL_COMPANIES);
        try {
            return work.get();
        } finally {
            if (previousCompany == null) {
                clear();
            } else {
                set(previousCompany);
            }
        }
    }
}
