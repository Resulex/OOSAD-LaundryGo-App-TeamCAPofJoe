package com.laundrygo.model;

import java.time.LocalDateTime;

/** A laundry client. */
public record Customer(long id, String name, String phone, LocalDateTime createdAt) {

    public String displayLabel() {
        return name + "  ·  " + phone;
    }

    @Override
    public String toString() {
        return displayLabel();
    }
}
