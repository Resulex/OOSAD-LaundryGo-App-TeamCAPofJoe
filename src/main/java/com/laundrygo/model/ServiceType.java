package com.laundrygo.model;

/** A billable service offered at the counter. */
public record ServiceType(long id, String name, PricingMode mode, double price, int turnaroundDays, boolean active) {

    /** Unit shown next to the weight input: kilograms for per-kg work, pieces for flat rates. */
    public String unitLabel() {
        return mode == PricingMode.PER_KG ? "kg" : "pc";
    }

    public String priceLabel() {
        return String.format("₱%,.2f / %s", price, unitLabel());
    }

    @Override
    public String toString() {
        return name;
    }
}
