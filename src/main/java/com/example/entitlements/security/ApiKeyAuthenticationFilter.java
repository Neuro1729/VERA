package com.example.entitlements.security;

import com.example.entitlements.domain.TenantApiCredential;
import com.example.entitlements.persistence.TenantApiCredentialRepository;
import com.example.entitlements.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public final class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final TenantApiCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyAuthenticationFilter(TenantApiCredentialRepository credentials, PasswordEncoder passwordEncoder) {
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String raw = request.getHeader(VeraAuthorities.API_KEY_HEADER);
        if (raw != null && !raw.isBlank()) {
            authenticate(raw.trim());
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String raw) {
        ApiKeyService.ParsedKey parsed = ApiKeyService.parse(raw);
        if (parsed == null) return;
        TenantApiCredential credential = credentials.findByPublicId(parsed.publicId()).orElse(null);
        if (credential == null || !credential.enabled()) return;
        if (!passwordEncoder.matches(parsed.secret(), credential.secretHash())) return;
        ApiKeyPrincipal principal = new ApiKeyPrincipal(credential.id(), credential.tenantId());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(VeraAuthorities.GATEWAY)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
