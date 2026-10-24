package com.laundrygo.service;

import com.laundrygo.db.SettingDao;
import com.laundrygo.model.PenaltyPolicy;
import java.time.LocalDateTime;

/** Penalty configuration + arithmetic. Settings are read live so owner changes apply instantly. */
public final class PenaltyService {

    private final SettingDao settings;

    public PenaltyService(SettingDao settings) {
        this.settings = settings;
    }

    public double perDay() {
        return settings.getDouble("penalty_per_day", 20.0);
    }

    public int graceDays() {
        return settings.getInt("grace_days", 1);
    }

    public double capPercent() {
        return settings.getDouble("penalty_cap_percent", 100.0);
    }

    public void update(double perDay, int graceDays, double capPercent) {
        settings.set("penalty_per_day", String.valueOf(perDay));
        settings.set("grace_days", String.valueOf(graceDays));
        settings.set("penalty_cap_percent", String.valueOf(capPercent));
    }

    public double calculateLateFee(LocalDateTime dueAt, LocalDateTime now, double baseCost) {
        return PenaltyPolicy.lateFee(dueAt, now, baseCost, perDay(), graceDays(), capPercent());
    }

    public long chargeableDays(LocalDateTime dueAt, LocalDateTime now) {
        return PenaltyPolicy.chargeableDays(dueAt, now, graceDays());
    }
}
