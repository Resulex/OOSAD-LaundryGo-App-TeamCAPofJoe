package com.laundrygo.model;

/** How a service type is billed. */
public enum PricingMode {
    PER_KG("Per kilogram"),
    FLAT("Flat rate / piece");

    private final String displayName;

    PricingMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static PricingMode fromDb(String value) {
        return "FLAT".equalsIgnoreCase(value) ? FLAT : PER_KG;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
