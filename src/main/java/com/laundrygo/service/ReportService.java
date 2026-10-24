package com.laundrygo.service;

import com.laundrygo.db.AuditDao;
import com.laundrygo.db.OrderDao;
import com.laundrygo.db.PaymentDao;
import com.laundrygo.model.AuditEntry;
import com.laundrygo.model.OrderStatus;
import com.laundrygo.model.Payment;
import java.time.LocalDate;
import java.util.List;

/** Aggregates for the owner dashboard and the revenue report. */
public final class ReportService {

    private final OrderDao orders;
    private final PaymentDao payments;
    private final AuditDao audit;

    public ReportService(OrderDao orders, PaymentDao payments, AuditDao audit) {
        this.orders = orders;
        this.payments = payments;
        this.audit = audit;
    }

    public double revenueToday() {
        return payments.sumBetween(LocalDate.now(), LocalDate.now());
    }

    public int activeOrders() {
        return orders.countActive();
    }

    public int overdueOrders() {
        return orders.countByStatus(OrderStatus.OVERDUE);
    }

    public int readyOrders() {
        return orders.countByStatus(OrderStatus.READY);
    }

    public List<Payment> paymentsBetween(LocalDate from, LocalDate to) {
        return payments.findBetween(from, to);
    }

    public List<Payment> todayPayments() {
        return payments.findBetween(LocalDate.now(), LocalDate.now());
    }

    public List<AuditEntry> recentActivity(int limit) {
        return audit.recent(limit);
    }
}
