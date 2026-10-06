package com.kayogx.eventcard.service;

import com.kayogx.eventcard.dto.CompanyDetails;
import com.kayogx.eventcard.dto.CompanyPage;
import com.kayogx.eventcard.exception.InvalidInputException;
import com.kayogx.eventcard.exception.NotFoundException;
import com.kayogx.eventcard.model.AccountStatus;
import com.kayogx.eventcard.model.Company;
import com.kayogx.eventcard.repository.CompanyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** What the platform admin (the SaaS owner) can do with vendor companies. */
@Service
public class PlatformService {

    private static final int COMPANIES_PER_PAGE = 20;

    private final CompanyRepository companyRepository;
    private final CurrentPlan currentPlan;

    public PlatformService(CompanyRepository companyRepository, CurrentPlan currentPlan) {
        this.companyRepository = companyRepository;
        this.currentPlan = currentPlan;
    }

    /** Newest companies first. {@code search} filters by company name (optional). */
    @Transactional(readOnly = true)
    public CompanyPage listCompanies(String search, int page) {
        PageRequest pageRequest = PageRequest.of(page, COMPANIES_PER_PAGE, Sort.by("createdAt").descending());
        String nameFilter = search == null ? "" : search.trim();
        Page<Company> result = companyRepository.findByNameContainingIgnoreCase(nameFilter, pageRequest);
        return new CompanyPage(
                result.map(this::rowOf).getContent(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements());
    }

    private CompanyPage.CompanyRow rowOf(Company company) {
        CurrentPlan.PlanState plan = currentPlan.stateOf(company);
        return new CompanyPage.CompanyRow(CompanyDetails.from(company), plan.plan().getName(), plan.status(),
                plan.paidUntil(), company.getCreditBalance(), company.getSmsSenderName());
    }

    /**
     * Sets the company's own SMS sender name - only once it is registered with the SMS company.
     * Empty clears it (our platform sender name is used again).
     */
    @Transactional
    public CompanyPage.CompanyRow setSmsSenderName(UUID companyId, String senderName) {
        Company company = findCompany(companyId);
        String cleaned = senderName == null || senderName.isBlank() ? null : senderName.trim();
        if (cleaned != null && !cleaned.matches("^[A-Za-z0-9 ]{3,11}$")) {
            throw new InvalidInputException("A sender name has 3-11 letters or digits, e.g. KAYOEVENTS", "smsSenderName");
        }
        company.setSmsSenderName(cleaned);
        return rowOf(company);
    }

    @Transactional
    public CompanyDetails suspend(UUID companyId) {
        Company company = findCompany(companyId);
        company.setAccountStatus(AccountStatus.SUSPENDED);
        return CompanyDetails.from(company);
    }

    @Transactional
    public CompanyDetails reactivate(UUID companyId) {
        Company company = findCompany(companyId);
        company.setAccountStatus(AccountStatus.ACTIVE);
        return CompanyDetails.from(company);
    }

    /** Unlocks WhatsApp/SMS sending once you have checked the company is genuine. */
    @Transactional
    public CompanyDetails allowSending(UUID companyId) {
        Company company = findCompany(companyId);
        company.setCanSendMessages(true);
        return CompanyDetails.from(company);
    }

    @Transactional
    public CompanyDetails blockSending(UUID companyId) {
        Company company = findCompany(companyId);
        company.setCanSendMessages(false);
        return CompanyDetails.from(company);
    }

    private Company findCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));
    }
}
