package com.example.entitlements.testutil;

import com.example.entitlements.cache.GrantResolutionCache;
import com.example.entitlements.cache.ResolutionCacheInvalidator;
import com.example.entitlements.service.BulkSyncPlanner;
import com.example.entitlements.service.BulkSyncService;
import com.example.entitlements.service.CommandService;
import com.example.entitlements.service.CompanyRegistrationService;
import com.example.entitlements.service.ConfigurationValidationService;
import com.example.entitlements.service.EntitlementHistoryService;
import com.example.entitlements.service.EntitlementResolver;
import com.example.entitlements.service.RateLimitService;
import com.example.entitlements.service.RegistrationService;
import com.example.entitlements.service.UsageService;
import com.example.entitlements.store.EntitlementHistoryStore;
import com.example.entitlements.store.TenantRegistry;
import com.example.entitlements.store.UsageHistoryStore;
import com.example.entitlements.store.UsageStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;

public final class EntitlementEngineHarness {
    public final TenantRegistry registry;
    public final UsageStore usageStore;
    public final UsageHistoryStore usageHistoryStore;
    public final EntitlementHistoryStore historyStore;
    public final GrantResolutionCache cache;
    public final ResolutionCacheInvalidator invalidator;
    public final CommandService commands;
    public final RegistrationService registration;
    public final CompanyRegistrationService companyRegistration;
    public final BulkSyncService bulkSync;
    public final UsageService usage;
    public final ObjectMapper mapper;

    private EntitlementEngineHarness(
            TenantRegistry registry,
            UsageStore usageStore,
            UsageHistoryStore usageHistoryStore,
            EntitlementHistoryStore historyStore,
            GrantResolutionCache cache,
            ResolutionCacheInvalidator invalidator,
            CommandService commands,
            RegistrationService registration,
            CompanyRegistrationService companyRegistration,
            BulkSyncService bulkSync,
            UsageService usage,
            ObjectMapper mapper
    ) {
        this.registry = registry;
        this.usageStore = usageStore;
        this.usageHistoryStore = usageHistoryStore;
        this.historyStore = historyStore;
        this.cache = cache;
        this.invalidator = invalidator;
        this.commands = commands;
        this.registration = registration;
        this.companyRegistration = companyRegistration;
        this.bulkSync = bulkSync;
        this.usage = usage;
        this.mapper = mapper;
    }

    public static EntitlementEngineHarness create() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TenantRegistry registry = new TenantRegistry();
        UsageStore usageStore = new UsageStore();
        UsageHistoryStore usageHistoryStore = new UsageHistoryStore();
        EntitlementHistoryStore historyStore = new EntitlementHistoryStore();
        GrantResolutionCache cache = new GrantResolutionCache();
        ResolutionCacheInvalidator invalidator = new ResolutionCacheInvalidator(cache);
        EntitlementResolver resolver = new EntitlementResolver(cache);
        Clock clock = Clock.systemUTC();
        RateLimitService rateLimitService = new RateLimitService(registry, resolver, usageHistoryStore, clock);
        EntitlementHistoryService historyService = new EntitlementHistoryService(registry, historyStore, clock);
        CommandService commands = new CommandService(
                registry, usageStore, mapper, cache, invalidator, rateLimitService, historyService);
        RegistrationService registration = new RegistrationService(registry, historyService);
        ConfigurationValidationService validation = new ConfigurationValidationService();
        CompanyRegistrationService companyRegistration = new CompanyRegistrationService(registration, validation);
        BulkSyncService bulkSync = new BulkSyncService(
                registry, new BulkSyncPlanner(mapper), validation, commands, invalidator);
        UsageService usage = new UsageService(registry, usageStore, resolver, usageHistoryStore, clock);
        return new EntitlementEngineHarness(
                registry, usageStore, usageHistoryStore, historyStore, cache, invalidator,
                commands, registration, companyRegistration, bulkSync, usage, mapper);
    }
}
