package com.kayogx.eventcard.controller;

import com.kayogx.eventcard.exception.NotFoundException;
import com.kayogx.eventcard.repository.CompanyRepository;
import com.kayogx.eventcard.service.AllCompaniesTransaction;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Used by the web server (e.g. Caddy) before it gets an HTTPS certificate for a domain:
 * "Is invites.kayoevents.com one of your vendors' verified domains?"
 * Answers 200 for yes, 404 for no - so certificates are only made for real vendor domains.
 */
@RestController
public class AllowedDomainsController {

    private final CompanyRepository companyRepository;
    private final AllCompaniesTransaction allCompaniesTransaction;

    public AllowedDomainsController(CompanyRepository companyRepository, AllCompaniesTransaction allCompaniesTransaction) {
        this.companyRepository = companyRepository;
        this.allCompaniesTransaction = allCompaniesTransaction;
    }

    @GetMapping("/api/public/domains/allowed")
    public void checkDomain(@RequestParam String domain) {
        boolean allowed = allCompaniesTransaction.run(() ->
                companyRepository.existsByCustomDomainIgnoreCaseAndCustomDomainVerifiedTrue(domain.trim()));
        if (!allowed) {
            throw new NotFoundException("Unknown domain");
        }
    }
}
