package com.kayogx.eventcard.service;

import com.kayogx.eventcard.repository.CompanyRepository;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

/**
 * Turns a company name into a short, link-friendly name ("slug").
 * Example: "Kayo Events & Weddings" becomes "kayo-events-weddings".
 */
@Component
public class SlugMaker {

    private final CompanyRepository companyRepository;

    public SlugMaker(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /** Makes a slug from the name, adding -2, -3... if it is already taken. */
    public String makeUniqueSlug(String companyName) {
        String baseSlug = toSlug(companyName);
        String candidate = baseSlug;
        int number = 2;
        while (companyRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + number;
            number++;
        }
        return candidate;
    }

    /** Lowercase letters, digits and single hyphens only. */
    public static String toSlug(String text) {
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = withoutAccents.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")   // anything that is not a letter or digit becomes "-"
                .replaceAll("(^-+)|(-+$)", "");  // no hyphen at the start or end
        return slug.isEmpty() ? "company" : slug;
    }
}
