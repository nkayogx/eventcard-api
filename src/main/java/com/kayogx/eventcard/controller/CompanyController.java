package com.kayogx.eventcard.controller;

import com.kayogx.eventcard.dto.CompanyDetails;
import com.kayogx.eventcard.dto.CustomDomainRequest;
import com.kayogx.eventcard.dto.UpdateCompanyRequest;
import com.kayogx.eventcard.service.CompanyService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * The logged-in user's own company.
 * Everyone in the company may VIEW it; only the OWNER may CHANGE it.
 */
@RestController
@RequestMapping("/api/my-company")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CHECK_IN_STAFF')")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public CompanyDetails getMyCompany() {
        return companyService.getMyCompany();
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public CompanyDetails updateMyCompany(@Valid @RequestBody UpdateCompanyRequest request) {
        return companyService.updateMyCompany(request);
    }

    @PostMapping("/logo")
    @PreAuthorize("hasRole('OWNER')")
    public CompanyDetails uploadLogo(@RequestParam("file") MultipartFile file) {
        return companyService.uploadLogo(file);
    }

    @PostMapping("/custom-domain")
    @PreAuthorize("hasRole('OWNER')")
    public CompanyDetails setCustomDomain(@Valid @RequestBody CustomDomainRequest request) {
        return companyService.setCustomDomain(request);
    }

    @PostMapping("/custom-domain/verify")
    @PreAuthorize("hasRole('OWNER')")
    public CompanyDetails verifyCustomDomain() {
        return companyService.verifyCustomDomain();
    }

    @DeleteMapping("/custom-domain")
    @PreAuthorize("hasRole('OWNER')")
    public CompanyDetails removeCustomDomain() {
        return companyService.removeCustomDomain();
    }
}
