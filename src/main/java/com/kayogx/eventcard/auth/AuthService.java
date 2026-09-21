package com.kayogx.eventcard.auth;

import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.EmailAddresses;
import com.kayogx.eventcard.common.LoginFailedException;
import com.kayogx.eventcard.common.NotAllowedException;
import com.kayogx.eventcard.company.Company;
import com.kayogx.eventcard.company.CompanyInputChecks;
import com.kayogx.eventcard.company.CompanyRepository;
import com.kayogx.eventcard.company.SlugMaker;
import com.kayogx.eventcard.tenant.AllCompaniesTransaction;
import com.kayogx.eventcard.user.User;
import com.kayogx.eventcard.user.UserRepository;
import com.kayogx.eventcard.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Signing up a new company, logging in, and "who am I?". */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final SlugMaker slugMaker;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AllCompaniesTransaction allCompaniesTransaction;

    public AuthService(UserRepository userRepository,
                       CompanyRepository companyRepository,
                       SlugMaker slugMaker,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AllCompaniesTransaction allCompaniesTransaction) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.slugMaker = slugMaker;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.allCompaniesTransaction = allCompaniesTransaction;
    }

    /**
     * Creates a new company and its first user (the OWNER) in one step,
     * then logs the owner in straight away.
     */
    public LoginResponse signupCompany(SignupCompanyRequest request) {
        CompanyInputChecks.checkTimeZoneExists(request.timeZone());
        String email = EmailAddresses.normalize(request.email());

        // The company does not exist yet, so we work "as all companies".
        // Everything inside run(...) is saved together, or not at all.
        return allCompaniesTransaction.run(() -> {
            if (userRepository.existsByEmail(email)) {
                throw new ConflictException("This email is already registered", "email");
            }

            Company company = new Company();
            company.setName(request.companyName().trim());
            company.setSlug(slugMaker.makeUniqueSlug(request.companyName()));
            company.setContactEmail(email);
            company.setContactPhone(request.phone());
            company.setCountryCode(request.countryCode().toUpperCase());
            company.setTimeZone(request.timeZone());
            companyRepository.save(company);

            User owner = new User();
            owner.setCompanyId(company.getId());
            owner.setFullName(request.fullName().trim());
            owner.setEmail(email);
            owner.setPhone(request.phone());
            owner.setPasswordHash(passwordEncoder.encode(request.password()));
            owner.setRole(UserRole.OWNER);
            userRepository.save(owner);

            return loginResponseFor(owner, company);
        });
    }

    public LoginResponse login(LoginRequest request) {
        String email = EmailAddresses.normalize(request.email());

        // At login we don't know the company yet, so we search all companies
        User user = allCompaniesTransaction.run(() -> userRepository.findByEmail(email).orElse(null));

        // Same message for "no such email" and "wrong password",
        // so nobody can use the login form to discover who has an account.
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new LoginFailedException("Wrong email or password");
        }
        if (!user.isActive()) {
            throw new LoginFailedException("This account has been deactivated. Please contact your company owner.");
        }

        Company company = findCompanyOf(user);
        if (company != null && company.isSuspended()) {
            throw new NotAllowedException("Your company account is suspended. Please contact support.");
        }
        return loginResponseFor(user, company);
    }

    /** "Who am I?" for the logged-in user. */
    public MeResponse whoAmI() {
        LoggedInUser loggedInUser = LoggedInUser.current();
        User user = allCompaniesTransaction.run(() -> userRepository.findById(loggedInUser.userId()).orElseThrow());
        return MeResponse.from(user, findCompanyOf(user));
    }

    /** Builds the answer given after any successful login. Also used when accepting an invitation. */
    public LoginResponse loginResponseFor(User user, Company companyOrNull) {
        return new LoginResponse(jwtService.createToken(user), MeResponse.from(user, companyOrNull));
    }

    /** Platform admins don't belong to a company, so for them this returns null. */
    private Company findCompanyOf(User user) {
        if (user.isPlatformAdmin()) {
            return null;
        }
        return companyRepository.findById(user.getCompanyId()).orElse(null);
    }
}
