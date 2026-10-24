package com.laundrygo.model;

/**
 * Order lifecycle. The CSS class names drive the colour coded status pills used in every table:
 * grey = pending, blue = in-progress, green = ready, red = overdue.
 */
public enum OrderStatus {
    PENDING("Pending", "pending"),
    IN_PROGRESS("In-Progress", "in-progress"),
    READY("Ready", "ready"),
    OVERDUE("Overdue", "overdue"),
    COMPLETED("Completed", "completed");

    private final String displayName;
    private final String cssClass;

    OrderStatus(String displayName, String cssClass) {
        this.displayName = displayName;
        this.cssClass = cssClass;
    }

    public String displayName() {
        return displayName;
    }

    public String cssClass() {
        return cssClass;
    }

    /** True while the order still belongs on the counter's active queue. */
    public boolean isOpen() {
        return this != COMPLETED;
    }

    public static OrderStatus fromDb(String value) {
        if (value == null) {
            return PENDING;
        }
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return PENDING;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
