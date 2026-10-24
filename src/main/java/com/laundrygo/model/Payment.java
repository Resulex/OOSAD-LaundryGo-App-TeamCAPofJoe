package com.laundrygo.model;

import java.time.LocalDateTime;

/** A settled payment, used by the revenue report and the owner's transaction table. */
public record Payment(long id,
                      LocalDateTime at,
                      String orderCode,
                      String customerName,
                      PaymentMethod method,
                      double amount,
                      String reference,
                      String receivedBy) {
}
