package com.kayogx.eventcard.config;

import com.kayogx.eventcard.model.User;
import com.kayogx.eventcard.model.UserRole;
import com.kayogx.eventcard.repository.UserRepository;
import com.kayogx.eventcard.security.CurrentTenant;
import com.kayogx.eventcard.service.AllCompaniesTransaction;
import com.kayogx.eventcard.util.EmailAddresses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * When the app starts: if there is no platform admin yet, create one
 * from the settings "app.platform-admin.*" (environment variables PLATFORM_ADMIN_...).
 */
@Component
@Slf4j
public class PlatformAdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AllCompaniesTransaction allCompaniesTransaction;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminFullName;

    public PlatformAdminSeeder(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               AllCompaniesTransaction allCompaniesTransaction,
                               @Value("${app.platform-admin.email}") String adminEmail,
                               @Value("${app.platform-admin.password}") String adminPassword,
                               @Value("${app.platform-admin.full-name}") String adminFullName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.allCompaniesTransaction = allCompaniesTransaction;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminFullName = adminFullName;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        allCompaniesTransaction.run(() -> {
            if (userRepository.existsByRole(UserRole.PLATFORM_ADMIN)) {
                return null;
            }
            User admin = new User();
            // Platform admins don't belong to one company - they see all of them
            admin.setCompanyId(CurrentTenant.ALL_COMPANIES);
            admin.setFullName(adminFullName);
            admin.setEmail(EmailAddresses.normalize(adminEmail));
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole(UserRole.PLATFORM_ADMIN);
            userRepository.save(admin);
            log.info("Created the first platform admin: {}", admin.getEmail());
            return null;
        });
    }
}
