package com.kayogx.eventcard.company;

import com.kayogx.eventcard.auth.LoggedInUser;
import com.kayogx.eventcard.common.NotFoundException;
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
