package com.kayogx.eventcard.platform;

import com.kayogx.eventcard.billing.CurrentPlan;
import com.kayogx.eventcard.company.CompanyDetails;

import java.time.LocalDate;
import java.util.List;

/** One page of the platform admin's company list. Page numbers start at 0. */
public record CompanyPage(List<CompanyRow> companies, int page, int totalPages, long totalCompanies) {

    /** A company, with its plan and credits. */
    public record CompanyRow(CompanyDetails company, String planName, CurrentPlan.Status planStatus,
                             LocalDate planPaidUntil, long creditBalance) {
    }
}
