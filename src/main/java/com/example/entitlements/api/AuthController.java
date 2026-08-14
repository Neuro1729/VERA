package com.example.entitlements.api;

import com.example.entitlements.domain.TenantAdmin;
import com.example.entitlements.domain.TenantApiCredential;
import com.example.entitlements.request.ApiKeyMetadataResponse;
import com.example.entitlements.request.ApiKeyRotationResponse;
import com.example.entitlements.request.AuthMeResponse;
import com.example.entitlements.request.CompanySignupRequest;
import com.example.entitlements.request.CompanySignupResponse;
import com.example.entitlements.request.CsrfTokenResponse;
import com.example.entitlements.request.LoginRequest;
import com.example.entitlements.security.TenantAdminPrincipal;
import com.example.entitlements.service.AdminAuthenticationService;
import com.example.entitlements.service.ApiKeyService;
import com.example.entitlements.service.CompanySignupService;
import com.example.entitlements.service.TenantAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CompanySignupService signupService;
    private final AdminAuthenticationService authenticationService;
    private final ApiKeyService apiKeyService;
    private final TenantAccessService tenantAccessService;

    public AuthController(
            CompanySignupService signupService,
            AdminAuthenticationService authenticationService,
            ApiKeyService apiKeyService,
            TenantAccessService tenantAccessService
    ) {
        this.signupService = signupService;
        this.authenticationService = authenticationService;
        this.apiKeyService = apiKeyService;
        this.tenantAccessService = tenantAccessService;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            token = (CsrfToken) request.getAttribute("_csrf");
        }
        if (token == null) {
            throw new java.util.NoSuchElementException("CSRF is not enabled");
        }
        return new CsrfTokenResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanySignupResponse signup(
            @RequestBody CompanySignupRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        CompanySignupService.SignupResult result = signupService.signup(request);
        authenticationService.establishSession(result.admin(), httpRequest, httpResponse);
        return result.toResponse();
    }

    @PostMapping("/login")
    public AuthMeResponse login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        TenantAdmin admin = authenticationService.authenticate(request.email(), request.password());
        authenticationService.establishSession(admin, httpRequest, httpResponse);
        return new AuthMeResponse(true, admin.tenantId(), admin.email());
    }

    @GetMapping("/me")
    public AuthMeResponse me() {
        TenantAdminPrincipal admin = tenantAccessService.currentAdmin();
        return new AuthMeResponse(true, admin.tenantId(), admin.email());
    }

    @PostMapping("/logout")
    public AuthMeResponse logout(HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logout(request, response);
        return new AuthMeResponse(false, null, null);
    }

    @GetMapping("/api-key")
    public ApiKeyMetadataResponse apiKey() {
        TenantAdminPrincipal admin = tenantAccessService.currentAdmin();
        TenantApiCredential credential = apiKeyService.metadata(admin.tenantId());
        return new ApiKeyMetadataResponse(
                credential.publicId(),
                ApiKeyService.displayPrefix(credential.publicId()),
                credential.createdAt(),
                credential.rotatedAt());
    }

    @PostMapping("/api-key/rotate")
    public ApiKeyRotationResponse rotate() {
        TenantAdminPrincipal admin = tenantAccessService.currentAdmin();
        ApiKeyService.GeneratedApiKey generated = apiKeyService.rotate(admin.tenantId());
        TenantApiCredential credential = apiKeyService.metadata(admin.tenantId());
        return new ApiKeyRotationResponse(generated.rawKey(), generated.publicId(), credential.rotatedAt());
    }
}
