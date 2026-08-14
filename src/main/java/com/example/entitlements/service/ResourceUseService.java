package com.example.entitlements.service;

import com.example.entitlements.domain.BooleanValue;
import com.example.entitlements.domain.EntitlementValue;
import com.example.entitlements.domain.QuantityValue;
import com.example.entitlements.domain.QuotaValue;
import com.example.entitlements.domain.RangeValue;
import com.example.entitlements.domain.RateLimitValue;
import com.example.entitlements.domain.Resource;
import com.example.entitlements.domain.Tenant;
import com.example.entitlements.domain.TextValue;
import com.example.entitlements.domain.UsageEvent;
import com.example.entitlements.request.EvaluationRequest;
import com.example.entitlements.request.EvaluationResult;
import com.example.entitlements.persistence.UsageHistoryRepository;
import com.example.entitlements.store.TenantRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ResourceUseService {
    private final TenantRegistry registry;
    private final EntitlementService entitlementService;
    private final UsageHistoryRepository historyStore;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ResourceUseService(
            TenantRegistry registry,
            EntitlementService entitlementService,
            UsageHistoryRepository historyStore,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.registry = registry;
        this.entitlementService = entitlementService;
        this.historyStore = historyStore;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public EvaluationResult commitUse(EvaluationRequest request) {
        Tenant tenant = registry.getRequired(request.tenantId());
        Resource resource = tenant.getResources().get(request.resourceId());
        if (resource == null) throw new NoSuchElementException("resource not found: " + request.resourceId());

        EvaluationResult result = entitlementService.evaluate(request);
        EntitlementValue value = result.value();
        if (value instanceof QuotaValue || value instanceof RateLimitValue) {
            throw new IllegalArgumentException(
                    "QUOTA and RATE_LIMIT committed use must use the dedicated consume endpoints");
        }
        if (!result.allowed()) return result;

        Instant occurredAt = Instant.now(clock);
        historyStore.appendEvent(new UsageEvent(
                UUID.randomUUID().toString(),
                tenant.getId(),
                resource.id(),
                resource.name(),
                resource.kind(),
                request.entitlementKey(),
                result.grantId(),
                result.source(),
                UsageHistorySnapshots.grantTargetName(tenant, result.source()),
                request.subjectId(),
                UsageHistorySnapshots.subjectName(tenant, request.subjectId()),
                usedValue(value, request.requestedValue()),
                occurredAt));
        return result;
    }

    private JsonNode usedValue(EntitlementValue value, JsonNode requested) {
        if (value instanceof RangeValue range) {
            ObjectNode node = objectMapper.createObjectNode();
            if (requested != null && requested.isNumber()) node.put("value", requested.decimalValue());
            else node.putNull("value");
            node.put("unit", range.unit());
            return node;
        }
        if (value instanceof QuantityValue quantity) {
            ObjectNode node = objectMapper.createObjectNode();
            if (requested != null && requested.isNumber()) node.put("value", requested.decimalValue());
            else node.put("value", quantity.value());
            node.put("unit", quantity.unit());
            return node;
        }
        if (value instanceof BooleanValue booleanValue) {
            if (requested != null && requested.isBoolean()) return BooleanNode.valueOf(requested.asBoolean());
            return BooleanNode.valueOf(booleanValue.value());
        }
        if (value instanceof TextValue textValue) {
            if (requested != null && requested.isTextual()) return TextNode.valueOf(requested.asText());
            return TextNode.valueOf(textValue.value());
        }
        if (requested == null || requested.isNull()) return NullNode.getInstance();
        if (requested.isTextual()) {
            ArrayNode array = objectMapper.createArrayNode();
            array.add(requested.asText());
            return array;
        }
        return requested.deepCopy();
    }
}
