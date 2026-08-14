package com.example.entitlements.service;

import com.example.entitlements.domain.Tenant;
import com.example.entitlements.domain.TenantAdmin;
import com.example.entitlements.persistence.TenantAdminRepository;
import com.example.entitlements.request.AdminRegistrationInput;
import com.example.entitlements.request.CompanySignupRequest;
import com.example.entitlements.request.CompanySignupResponse;
import com.example.entitlements.security.VeraAuthorities;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class CompanySignupService {
    private final CompanyRegistrationService companyRegistrationService;
    private final TenantAdminRepository adminRepository;
    private final ApiKeyService apiKeyService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public CompanySignupService(
            CompanyRegistrationService companyRegistrationService,
            TenantAdminRepository adminRepository,
            ApiKeyService apiKeyService,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.companyRegistrationService = companyRegistrationService;
        this.adminRepository = adminRepository;
        this.apiKeyService = apiKeyService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public SignupResult signup(CompanySignupRequest request) {
        AdminRegistrationInput adminInput = validate(request);
        String email = TenantAdmin.displayEmail(adminInput.email());
        String normalizedEmail = TenantAdmin.normalizeEmail(adminInput.email());
        if (adminRepository.existsByNormalizedEmail(normalizedEmail)) {
            throw new IllegalStateException("email already registered");
        }

        Tenant tenant = companyRegistrationService.register(request.registration());
        TenantAdmin admin = new TenantAdmin(
                UUID.randomUUID().toString(),
                tenant.getId(),
                email,
                normalizedEmail,
                passwordEncoder.encode(adminInput.password()),
                clock.instant());
        adminRepository.insert(admin);
        ApiKeyService.GeneratedApiKey apiKey = apiKeyService.create(tenant.getId());
        return new SignupResult(admin, apiKey.rawKey());
    }

    private static AdminRegistrationInput validate(CompanySignupRequest request) {
        if (request == null || request.admin() == null) {
            throw new IllegalArgumentException("admin is required");
        }
        if (request.registration() == null) {
            throw new IllegalArgumentException("registration is required");
        }
        AdminRegistrationInput admin = request.admin();
        TenantAdmin.normalizeEmail(admin.email());
        if (admin.password() == null || admin.password().isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        if (admin.password().length() < VeraAuthorities.MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("password must be at least 12 characters");
        }
        return admin;
    }

    public record SignupResult(TenantAdmin admin, String apiKey) {
        public CompanySignupResponse toResponse() {
            return new CompanySignupResponse(
                    admin.tenantId(),
                    new CompanySignupResponse.AdminView(admin.email()),
                    apiKey);
        }
    }
}
