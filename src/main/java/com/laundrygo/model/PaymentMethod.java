package com.laundrygo.model;

/** Settlement channel. */
public enum PaymentMethod {
    CASH("Cash"),
    GCASH("GCash");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static PaymentMethod fromDb(String value) {
        return "GCASH".equalsIgnoreCase(value) ? GCASH : CASH;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
