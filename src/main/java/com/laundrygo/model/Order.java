package com.laundrygo.model;

import java.time.LocalDateTime;

/**
 * An order row, denormalised with the customer and service names so the queue table can render
 * without extra lookups.
 */
public final class Order {

    private final long id;
    private final String code;
    private final long customerId;
    private final String customerName;
    private final String customerPhone;
    private final long serviceTypeId;
    private final String serviceName;
    private final PricingMode pricingMode;
    private final double weight;
    private final double unitPrice;
    private final double baseCost;
    private final LocalDateTime createdAt;
    private final LocalDateTime dueAt;
    private final String notes;

    private OrderStatus status;
    private double lateFee;
    private double amountPaid;
    private LocalDateTime completedAt;

    public Order(long id,
                 String code,
                 long customerId,
                 String customerName,
                 String customerPhone,
                 long serviceTypeId,
                 String serviceName,
                 PricingMode pricingMode,
                 double weight,
                 double unitPrice,
                 double baseCost,
                 OrderStatus status,
                 double lateFee,
                 double amountPaid,
                 LocalDateTime createdAt,
                 LocalDateTime dueAt,
                 LocalDateTime completedAt,
                 String notes) {
        this.id = id;
        this.code = code;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.serviceTypeId = serviceTypeId;
        this.serviceName = serviceName;
        this.pricingMode = pricingMode;
        this.weight = weight;
        this.unitPrice = unitPrice;
        this.baseCost = baseCost;
        this.status = status;
        this.lateFee = lateFee;
        this.amountPaid = amountPaid;
        this.createdAt = createdAt;
        this.dueAt = dueAt;
        this.completedAt = completedAt;
        this.notes = notes;
    }

    public long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public long getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public long getServiceTypeId() {
        return serviceTypeId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public PricingMode getPricingMode() {
        return pricingMode;
    }

    public double getWeight() {
        return weight;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getBaseCost() {
        return baseCost;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getNotes() {
        return notes;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public double getLateFee() {
        return lateFee;
    }

    public void setLateFee(double lateFee) {
        this.lateFee = lateFee;
    }

    public double getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(double amountPaid) {
        this.amountPaid = amountPaid;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    /** Base cost plus any accrued penalty. */
    public double getTotal() {
        return baseCost + lateFee;
    }

    /** What the client still owes. */
    public double getBalance() {
        return Math.max(0, getTotal() - amountPaid);
    }

    public boolean isOverdue() {
        return status.isOpen() && dueAt != null && dueAt.isBefore(LocalDateTime.now());
    }

    public String getWeightLabel() {
        String unit = pricingMode == PricingMode.PER_KG ? "kg" : "pc";
        String value = weight == Math.rint(weight)
                ? String.valueOf((long) weight)
                : String.format("%.2f", weight);
        return value + " " + unit;
    }
}
