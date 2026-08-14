package com.example.entitlements.service;

import com.example.entitlements.domain.TenantAdmin;
import com.example.entitlements.persistence.TenantAdminRepository;
import com.example.entitlements.security.TenantAdminPrincipal;
import com.example.entitlements.security.VeraAuthorities;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminAuthenticationService {
    private final TenantAdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;

    public AdminAuthenticationService(
            TenantAdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            SecurityContextRepository securityContextRepository
    ) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
    }

    public TenantAdmin authenticate(String email, String password) {
        if (password == null || password.isBlank()) {
            throw new BadCredentialsException("invalid credentials");
        }
        String normalized;
        try {
            normalized = TenantAdmin.normalizeEmail(email);
        } catch (IllegalArgumentException ex) {
            throw new BadCredentialsException("invalid credentials");
        }
        TenantAdmin admin = adminRepository.findByNormalizedEmail(normalized)
                .orElseThrow(() -> new BadCredentialsException("invalid credentials"));
        if (!passwordEncoder.matches(password, admin.passwordHash())) {
            throw new BadCredentialsException("invalid credentials");
        }
        return admin;
    }

    public void establishSession(TenantAdmin admin, HttpServletRequest request, HttpServletResponse response) {
        TenantAdminPrincipal principal = new TenantAdminPrincipal(admin.id(), admin.tenantId(), admin.email());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(VeraAuthorities.ADMIN)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true);
        request.changeSessionId();
        securityContextRepository.saveContext(context, request, response);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("JSESSIONID", "");
        cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }
}
