package com.kayogx.eventcard.platform;

import com.kayogx.eventcard.company.CompanyDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Platform admin only: oversee all vendor companies. */
@RestController
@RequestMapping("/api/platform/companies")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformController {

    private final PlatformService platformService;

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @GetMapping
    public CompanyPage listCompanies(@RequestParam(required = false) String search,
                                     @RequestParam(defaultValue = "0") int page) {
        return platformService.listCompanies(search, page);
    }

    /** Body: { "smsSenderName": "KAYOEVENTS" } - or empty to use the platform sender again. */
    @PutMapping("/{companyId}/sms-sender")
    public CompanyPage.CompanyRow setSmsSenderName(@PathVariable UUID companyId, @RequestBody Map<String, String> body) {
        return platformService.setSmsSenderName(companyId, body.get("smsSenderName"));
    }

    @PutMapping("/{companyId}/suspend")
    public CompanyDetails suspend(@PathVariable UUID companyId) {
        return platformService.suspend(companyId);
    }

    @PutMapping("/{companyId}/reactivate")
    public CompanyDetails reactivate(@PathVariable UUID companyId) {
        return platformService.reactivate(companyId);
    }

    @PutMapping("/{companyId}/allow-sending")
    public CompanyDetails allowSending(@PathVariable UUID companyId) {
        return platformService.allowSending(companyId);
    }

    @PutMapping("/{companyId}/block-sending")
    public CompanyDetails blockSending(@PathVariable UUID companyId) {
        return platformService.blockSending(companyId);
    }
}
