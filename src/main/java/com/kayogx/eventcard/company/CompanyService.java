package com.kayogx.eventcard.company;

import com.kayogx.eventcard.auth.LoggedInUser;
import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.EmailAddresses;
import com.kayogx.eventcard.common.InvalidInputException;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.common.RandomCodes;
import com.kayogx.eventcard.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Everything a company can do with its own profile:
 * view and edit details, upload a logo, and set up a custom domain.
 *
 * "My company" always means the company of the logged-in user,
 * so a user can never reach another company's profile.
 */
@Service
public class CompanyService {

    private static final int MAX_LOGO_SIZE_IN_BYTES = 2 * 1024 * 1024;

    private static final String DOMAIN_PATTERN = "^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$";

    private final CompanyRepository companyRepository;
    private final FileStorage fileStorage;
    private final DnsTxtLookup dnsTxtLookup;

    private final String cnameTarget;

    public CompanyService(CompanyRepository companyRepository, FileStorage fileStorage, DnsTxtLookup dnsTxtLookup,
                          @Value("${app.custom-domain-target}") String cnameTarget) {
        this.companyRepository = companyRepository;
        this.fileStorage = fileStorage;
        this.dnsTxtLookup = dnsTxtLookup;
        this.cnameTarget = cnameTarget;
    }

    @Transactional(readOnly = true)
    public CompanyDetails getMyCompany() {
        return detailsOf(loadMyCompany());
    }

    @Transactional
    public CompanyDetails updateMyCompany(UpdateCompanyRequest request) {
        CompanyInputChecks.checkTimeZoneExists(request.timeZone());
        Company company = loadMyCompany();

        boolean slugChanged = !request.slug().equals(company.getSlug());
        if (slugChanged && companyRepository.existsBySlug(request.slug())) {
            throw new ConflictException("This short name is already used by another company", "slug");
        }

        company.setName(request.name().trim());
        company.setSlug(request.slug());
        company.setContactPhone(request.contactPhone());
        company.setContactEmail(EmailAddresses.normalize(request.contactEmail()));
        company.setAddress(request.address());
        company.setCity(request.city());
        company.setCountryCode(request.countryCode().toUpperCase());
        company.setTimeZone(request.timeZone());
        company.setPrimaryColor(upperCaseOrNull(request.primaryColor()));
        company.setSecondaryColor(upperCaseOrNull(request.secondaryColor()));
        return detailsOf(company);
    }

    /** Saves a new logo (PNG, JPG or SVG, max 2 MB) and returns the updated profile. */
    @Transactional
    public CompanyDetails uploadLogo(MultipartFile file) {
        byte[] content = readBytes(file);
        if (content.length > MAX_LOGO_SIZE_IN_BYTES) {
            throw new InvalidInputException("The logo is too large. The maximum size is 2 MB.", "file");
        }
        String fileType = detectImageType(content);
        if (fileType == null) {
            throw new InvalidInputException("The logo must be a PNG, JPG or SVG image", "file");
        }

        Company company = loadMyCompany();
        // A new random name each time, so browsers don't keep showing an old cached logo
        String fileName = company.getId() + "-" + RandomCodes.newCode() + "." + fileType;
        company.setLogoUrl(fileStorage.save("logos", fileName, content));
        return detailsOf(company);
    }

    /**
     * Step 1 of using a custom domain: the owner tells us the domain,
     * and we answer with a DNS TXT record they must add to prove they own it.
     */
    @Transactional
    public CompanyDetails setCustomDomain(CustomDomainRequest request) {
        String domain = cleanUpDomain(request.domain());
        if (!domain.matches(DOMAIN_PATTERN)) {
            throw new InvalidInputException("Please enter a valid domain, e.g. invites.yourcompany.com", "domain");
        }

        Company company = loadMyCompany();
        boolean domainChanged = !domain.equals(company.getCustomDomain());
        if (domainChanged && companyRepository.existsByCustomDomainIgnoreCase(domain)) {
            throw new ConflictException("This domain is already used by another company", "domain");
        }

        company.setCustomDomain(domain);
        company.setCustomDomainVerified(false);
        company.setDomainVerificationCode("eventcard-verify=" + RandomCodes.newCode());
        return detailsOf(company);
    }

    /** Step 2: we look up the DNS record. If it matches, the domain is verified. */
    @Transactional
    public CompanyDetails verifyCustomDomain() {
        Company company = loadMyCompany();
        if (company.getCustomDomain() == null) {
            throw new NotFoundException("Please add a custom domain first");
        }

        String recordName = CustomDomainSetup.TXT_RECORD_PREFIX + company.getCustomDomain();
        boolean recordFound = dnsTxtLookup.findTxtRecords(recordName).contains(company.getDomainVerificationCode());
        if (!recordFound) {
            throw new InvalidInputException(
                    "We could not find the DNS record yet. DNS changes can take up to a few hours - please try again later.",
                    "domain");
        }

        company.setCustomDomainVerified(true);
        return detailsOf(company);
    }

    @Transactional
    public CompanyDetails removeCustomDomain() {
        Company company = loadMyCompany();
        company.setCustomDomain(null);
        company.setCustomDomainVerified(false);
        company.setDomainVerificationCode(null);
        return detailsOf(company);
    }

    private CompanyDetails detailsOf(Company company) {
        return CompanyDetails.from(company).withCnameTarget(cnameTarget);
    }

    private Company loadMyCompany() {
        return companyRepository.findById(LoggedInUser.current().companyId())
                .orElseThrow(() -> new NotFoundException("Company not found"));
    }

    /** Accepts things people commonly type, like "https://Invites.MyCompany.com/". */
    private static String cleanUpDomain(String typedDomain) {
        String domain = typedDomain.trim().toLowerCase()
                .replaceFirst("^https?://", "");
        while (domain.endsWith("/") || domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain;
    }

    /**
     * Looks at the first bytes of the file to see what it really is.
     * (We don't trust the file name or the type the browser claims.)
     * Returns "png", "jpg", "svg" - or null if it is none of these.
     */
    private static String detectImageType(byte[] content) {
        if (content.length > 8
                && (content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
            return "png";
        }
        if (content.length > 3
                && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        String startOfText = new String(content, 0, Math.min(content.length, 1000), StandardCharsets.UTF_8);
        if (startOfText.contains("<svg")) {
            return "svg";
        }
        return null;
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("Please choose a logo file", "file");
        }
        try {
            return file.getBytes();
        } catch (IOException problem) {
            throw new UncheckedIOException("Could not read the uploaded file", problem);
        }
    }

    private static String upperCaseOrNull(String colour) {
        return colour == null || colour.isBlank() ? null : colour.toUpperCase();
    }
}
