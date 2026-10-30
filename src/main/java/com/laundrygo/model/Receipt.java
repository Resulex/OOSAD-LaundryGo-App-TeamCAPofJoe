package com.laundrygo.model;

import java.time.LocalDateTime;

/** Everything a printed receipt needs, captured at settlement time. */
public record Receipt(String orderCode,
                      String clientName,
                      String clientPhone,
                      String serviceName,
                      String units,
                      double unitPrice,
                      double baseCost,
                      double lateFee,
                      double total,
                      double paid,
                      double change,
                      PaymentMethod method,
                      String reference,
                      String cashier,
                      LocalDateTime settledAt) {
}
