package com.example.entitlements.domain;

import java.math.BigDecimal;
import java.time.Instant;

public class Usage {
    private final String grantId;
    private BigDecimal consumed;
    private Instant periodStart;
    private Instant periodEnd;

    public Usage(String grantId, BigDecimal consumed, Instant periodStart, Instant periodEnd) {
        this.grantId = grantId;
        this.consumed = consumed;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public String getGrantId() { return grantId; }
    public BigDecimal getConsumed() { return consumed; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }

    public void add(BigDecimal amount) { consumed = consumed.add(amount); }
    public void reset(Instant start, Instant end) {
        consumed = BigDecimal.ZERO;
        periodStart = start;
        periodEnd = end;
    }
}
