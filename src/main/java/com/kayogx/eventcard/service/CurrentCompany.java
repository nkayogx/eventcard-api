package com.kayogx.eventcard.service;

import com.kayogx.eventcard.exception.NotFoundException;
import com.kayogx.eventcard.model.Company;
import com.kayogx.eventcard.repository.CompanyRepository;
import com.kayogx.eventcard.security.LoggedInUser;
import org.springframework.stereotype.Component;

/** Gives you the company of the logged-in user. */
@Component
public class CurrentCompany {

    private final CompanyRepository companyRepository;

    public CurrentCompany(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public Company get() {
        return companyRepository.findById(LoggedInUser.current().companyId())
                .orElseThrow(() -> new NotFoundException("Company not found"));
    }
}
