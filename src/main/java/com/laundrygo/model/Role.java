package com.laundrygo.model;

/** The two operating roles of the LaundryGo desktop client. */
public enum Role {
    STAFF,
    ADMIN;

    public static Role fromDb(String value) {
        if (value == null) {
            return STAFF;
        }
        return switch (value.trim().toUpperCase()) {
            case "ADMIN", "OWNER" -> ADMIN;
            default -> STAFF;
        };
    }

    public String displayName() {
        return this == ADMIN ? "Shop owner" : "Frontline staff";
    }
}
