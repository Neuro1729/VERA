package com.example.entitlements.service;

import com.example.entitlements.domain.TenantApiCredential;
import com.example.entitlements.persistence.TenantApiCredentialRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ApiKeyService {
    public static final String PREFIX = "vera_live_";
    private static final int PUBLIC_ID_BYTES = 8;
    private static final int SECRET_BYTES = 32;

    private final TenantApiCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(TenantApiCredentialRepository credentials, PasswordEncoder passwordEncoder, Clock clock) {
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public GeneratedApiKey create(String tenantId) {
        GeneratedApiKey generated = generate();
        Instant now = clock.instant();
        credentials.insert(new TenantApiCredential(
                UUID.randomUUID().toString(),
                tenantId,
                generated.publicId(),
                passwordEncoder.encode(generated.secret()),
                true,
                now,
                null));
        return generated;
    }

    public GeneratedApiKey rotate(String tenantId) {
        TenantApiCredential existing = credentials.findByTenantId(tenantId)
                .orElseThrow(() -> new NoSuchElementException("API credential not found"));
        GeneratedApiKey generated = generate();
        Instant now = clock.instant();
        credentials.replace(existing.rotated(generated.publicId(), passwordEncoder.encode(generated.secret()), now));
        return generated;
    }

    public TenantApiCredential metadata(String tenantId) {
        return credentials.findByTenantId(tenantId)
                .orElseThrow(() -> new NoSuchElementException("API credential not found"));
    }

    public static String displayPrefix(String publicId) {
        return PREFIX + publicId + "...";
    }

    public static ParsedKey parse(String raw) {
        if (raw == null || !raw.startsWith(PREFIX)) return null;
        String rest = raw.substring(PREFIX.length());
        int dot = rest.indexOf('.');
        if (dot <= 0 || dot == rest.length() - 1) return null;
        return new ParsedKey(rest.substring(0, dot), rest.substring(dot + 1));
    }

    private GeneratedApiKey generate() {
        String publicId = token(PUBLIC_ID_BYTES);
        String secret = token(SECRET_BYTES);
        return new GeneratedApiKey(publicId, secret, PREFIX + publicId + "." + secret);
    }

    private String token(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record ParsedKey(String publicId, String secret) {}

    public record GeneratedApiKey(String publicId, String secret, String rawKey) {}
}
