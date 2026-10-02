package com.pharmacy.config;

import com.pharmacy.common.PasswordPolicy;
import com.pharmacy.common.PermissionCode;
import com.pharmacy.user.PermissionRepository;
import com.pharmacy.user.Role;
import com.pharmacy.user.RoleRepository;
import com.pharmacy.user.UserAccount;
import com.pharmacy.user.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final PasswordEncoder passwordEncoder;
    private final PharmacyProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> stored = permissions.findAll().stream().map(permission -> permission.getCode()).collect(Collectors.toSet());
        for (PermissionCode code : PermissionCode.values()) {
            if (!stored.contains(code.name())) {
                throw new IllegalStateException("Database is missing permission " + code.name() + ". Check Flyway seed data.");
            }
        }
        String username = properties.getAdmin().getUsername().trim().toLowerCase();
        if (users.existsByUsername(username)) {
            return;
        }
        String password = properties.getAdmin().getPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Set PHARMACY_ADMIN_PASSWORD before the first start so the administrator account can be created.");
        }
        PasswordPolicy.validate(password, 8);
        Role adminRole = roles.findByName("ADMINISTRATOR")
                .orElseThrow(() -> new IllegalStateException("ADMINISTRATOR role is missing."));
        UserAccount admin = new UserAccount();
        admin.setUsername(username);
        admin.setEmail(properties.getAdmin().getEmail().trim().toLowerCase());
        admin.setFullName(properties.getAdmin().getFullName().trim());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setActive(true);
        admin.setMustChangePassword(properties.getAdmin().isMustChangePassword());
        admin.getRoles().add(adminRole);
        users.save(admin);
    }
}
