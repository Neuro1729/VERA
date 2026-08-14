package com.example.entitlements.persistence;

import com.example.entitlements.domain.Target;
import com.example.entitlements.domain.UsageBucket;
import com.example.entitlements.domain.UsageEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface UsageHistoryRepository {
    void appendEvent(UsageEvent event);

    void addToBucket(
            String tenantId,
            String subjectId,
            String subjectNameAtTime,
            String resourceId,
            String resourceNameAtTime,
            String resourceKindAtTime,
            String entitlementKey,
            String grantId,
            Target grantTarget,
            String grantTargetNameAtTime,
            BigDecimal amount,
            Instant occurredAt
    );

    List<UsageEvent> findEventsByResource(String tenantId, String resourceId);

    List<UsageBucket> findBucketsByResource(String tenantId, String resourceId);

    boolean hasHistory(String tenantId, String resourceId);

    void clear();
}
