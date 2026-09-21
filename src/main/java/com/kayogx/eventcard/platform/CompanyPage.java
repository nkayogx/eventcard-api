package com.kayogx.eventcard.platform;

import com.kayogx.eventcard.company.CompanyDetails;

import java.util.List;

/** One page of the platform admin's company list. Page numbers start at 0. */
public record CompanyPage(List<CompanyDetails> companies, int page, int totalPages, long totalCompanies) {
}
