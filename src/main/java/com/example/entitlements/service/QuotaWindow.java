package com.example.entitlements.service;

import com.example.entitlements.domain.QuotaPeriod;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

record QuotaWindow(Instant start, Instant end) {
    static QuotaWindow forInstant(Instant now, QuotaPeriod period) {
        ZonedDateTime zdt = now.atZone(ZoneOffset.UTC);
        ZonedDateTime start;
        ZonedDateTime end;
        switch (period) {
            case DAILY -> {
                start = zdt.toLocalDate().atStartOfDay(ZoneOffset.UTC);
                end = start.plusDays(1);
            }
            case WEEKLY -> {
                LocalDate monday = zdt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                start = monday.atStartOfDay(ZoneOffset.UTC);
                end = start.plusWeeks(1);
            }
            case MONTHLY -> {
                LocalDate first = zdt.toLocalDate().withDayOfMonth(1);
                start = first.atStartOfDay(ZoneOffset.UTC);
                end = start.plusMonths(1);
            }
            case YEARLY -> {
                LocalDate first = LocalDate.of(zdt.getYear(), 1, 1);
                start = first.atStartOfDay(ZoneOffset.UTC);
                end = start.plusYears(1);
            }
            default -> throw new IllegalStateException("unsupported quota period: " + period);
        }
        return new QuotaWindow(start.toInstant(), end.toInstant());
    }
}
