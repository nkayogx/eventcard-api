package com.kayogx.eventcard.auth;

import com.kayogx.eventcard.company.CompanyRepository;
import com.kayogx.eventcard.tenant.AllCompaniesTransaction;
import com.kayogx.eventcard.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Security rules for the whole API: which addresses are public,
 * which need a login, and which need a special role.
 *
 * Finer role rules (for example "only OWNER may edit the company") are written
 * right on the controller methods with @PreAuthorize, so they are easy to find.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityRules(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                // We use login tokens instead of cookies, so these cookie-based features are not needed
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> {})

                // This API only returns data, never web pages, so we forbid running scripts
                // from anything it serves (protects against dangerous uploaded SVG files).
                .headers(headers -> headers.contentSecurityPolicy(policy ->
                        policy.policyDirectives("default-src 'none'; img-src 'self'; style-src 'unsafe-inline'")))

                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup-company", "/api/auth/login").permitAll()
                        .requestMatchers("/api/invitations/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN")
                        .anyRequest().authenticated())

                .exceptionHandling(problems -> problems
                        .authenticationEntryPoint((request, response, problem) ->
                                sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Please log in to continue."))
                        .accessDeniedHandler((request, response, problem) ->
                                sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "You are not allowed to do this.")))

                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** The filter that reads login tokens (see {@link JwtAuthFilter}). */
    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService,
                                       UserRepository userRepository,
                                       CompanyRepository companyRepository,
                                       AllCompaniesTransaction allCompaniesTransaction) {
        return new JwtAuthFilter(jwtService, userRepository, companyRepository, allCompaniesTransaction);
    }

    /**
     * Spring Boot automatically runs every filter it finds on every request.
     * We switch that off for our token filter, because it already runs inside
     * the security rules above - otherwise it would run twice.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> doNotRegisterJwtFilterTwice(
            JwtAuthFilter jwtAuthFilter) {
        var registration = new FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    /** Passwords are stored as one-way BCrypt "fingerprints", never as plain text. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Allows the React website (running on another address) to call this API from the browser.
     * (Spring Security finds this by its exact name "corsConfigurationSource".)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend-url}") String frontendUrl,
                                                           @Value("${app.extra-allowed-origins}") List<String> extraOrigins) {
        CorsConfiguration rules = new CorsConfiguration();
        // The website's own address, plus any extra ones (e.g. localhost while testing through a tunnel)
        List<String> allowedOrigins = new ArrayList<>(List.of(frontendUrl));
        extraOrigins.stream().filter(origin -> !origin.isBlank()).forEach(allowedOrigins::add);
        rules.setAllowedOrigins(allowedOrigins);
        rules.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        rules.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", rules);
        return source;
    }

    private static void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
