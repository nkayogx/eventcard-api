package com.kayogx.eventcard.platform;

import com.kayogx.eventcard.billing.CurrentPlan;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.company.AccountStatus;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.company.CompanyDetails;
import com.kayogx.eventcard.company.CompanyRepository;
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
                plan.paidUntil(), company.getCreditBalance());
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
