package com.kayogx.eventcard.tenant;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * THE place where the automatic company filter is switched on.
 *
 * Every entity with a field marked {@code @TenantId} (for example User.companyId)
 * is protected by Hibernate:
 *  - when reading, Hibernate adds "WHERE company_id = <current company>" to every query;
 *  - when saving a new row, Hibernate fills in company_id with the current company.
 *
 * Hibernate asks this class "which company is current?" each time a database
 * session opens, and we answer with {@link CurrentTenant}.
 */
@Component
public class HibernateTenantSetup implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return CurrentTenant.companyIdForDatabase();
    }

    /** The "all companies" value may see (and save) rows of every company. */
    @Override
    public boolean isRoot(UUID companyId) {
        return CurrentTenant.ALL_COMPANIES.equals(companyId);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    /** Tells Hibernate to use this class. */
    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
