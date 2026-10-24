package com.laundrygo.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Pure penalty arithmetic, shared by the seeder, the nightly sweep and the UI preview so every
 * number in the app agrees with every other number.
 *
 * <p>Rule: once an order is past its due date by more than the grace period, each additional
 * calendar day adds the configured per-day penalty, capped at a percentage of the base cost.</p>
 */
public final class PenaltyPolicy {

    private PenaltyPolicy() {
    }

    /** Days that actually attract a penalty (grace period already deducted). */
    public static long chargeableDays(LocalDateTime dueAt, LocalDateTime now, int graceDays) {
        if (dueAt == null || now == null) {
            return 0;
        }
        long daysLate = ChronoUnit.DAYS.between(dueAt.toLocalDate(), now.toLocalDate());
        return Math.max(0, daysLate - Math.max(0, graceDays));
    }

    public static double lateFee(LocalDateTime dueAt,
                                 LocalDateTime now,
                                 double baseCost,
                                 double perDay,
                                 int graceDays,
                                 double capPercent) {
        long chargeable = chargeableDays(dueAt, now, graceDays);
        if (chargeable <= 0 || perDay <= 0) {
            return 0;
        }
        double raw = chargeable * perDay;
        double cap = baseCost * (Math.max(0, capPercent) / 100.0);
        double fee = cap > 0 ? Math.min(raw, cap) : raw;
        return Math.round(fee * 100.0) / 100.0;
    }
}
