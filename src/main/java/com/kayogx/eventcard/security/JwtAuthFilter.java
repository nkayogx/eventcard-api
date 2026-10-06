package com.kayogx.eventcard.security;

import com.kayogx.eventcard.model.Company;
import com.kayogx.eventcard.model.User;
import com.kayogx.eventcard.repository.CompanyRepository;
import com.kayogx.eventcard.repository.UserRepository;
import com.kayogx.eventcard.service.AllCompaniesTransaction;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs before every request. If the request carries a login token, it:
 *  1. checks the token is genuine and not expired;
 *  2. loads the user fresh from the database (so deactivations take effect at once);
 *  3. refuses the request if the user's company is suspended;
 *  4. remembers who the user is and which company they work for, for this request only.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AllCompaniesTransaction allCompaniesTransaction;

    public JwtAuthFilter(JwtService jwtService,
                         UserRepository userRepository,
                         CompanyRepository companyRepository,
                         AllCompaniesTransaction allCompaniesTransaction) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.allCompaniesTransaction = allCompaniesTransaction;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain nextStep)
            throws ServletException, IOException {

        Optional<User> user = findUserFromToken(request);

        // No token, bad token, or deactivated user: continue as "not logged in".
        // Protected addresses will then answer 401.
        if (user.isEmpty() || !user.get().isActive()) {
            nextStep.doFilter(request, response);
            return;
        }

        User loggedInUser = user.get();
        if (belongsToSuspendedCompany(loggedInUser)) {
            refuseBecauseSuspended(response);
            return;
        }

        rememberForThisRequest(loggedInUser);
        try {
            nextStep.doFilter(request, response);
        } finally {
            // Forget everything, so the next request on this thread starts clean
            CurrentTenant.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<User> findUserFromToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = header.substring("Bearer ".length());
        Optional<UUID> userId = jwtService.readUserId(token);
        if (userId.isEmpty()) {
            return Optional.empty();
        }
        // We don't know the company yet, so we look the user up across all companies
        return allCompaniesTransaction.run(() -> userRepository.findById(userId.get()));
    }

    private boolean belongsToSuspendedCompany(User user) {
        if (user.isPlatformAdmin()) {
            return false;
        }
        return companyRepository.findById(user.getCompanyId())
                .map(Company::isSuspended)
                .orElse(true);
    }

    private void rememberForThisRequest(User user) {
        LoggedInUser loggedInUser = new LoggedInUser(user.getId(), user.getCompanyId(), user.getRole(), user.getEmail());

        // Spring checks roles like hasRole('OWNER') against the name "ROLE_OWNER"
        var roleForSpring = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        var login = new UsernamePasswordAuthenticationToken(loggedInUser, null, List.of(roleForSpring));
        SecurityContextHolder.getContext().setAuthentication(login);

        // Switch on the automatic company filter for this request
        CurrentTenant.set(user.getCompanyId());
    }

    private void refuseBecauseSuspended(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"Your company account is suspended. Please contact support.\"}");
    }
}
