package com.laundrygo.service;

import com.laundrygo.db.AuditDao;
import com.laundrygo.db.CustomerDao;
import com.laundrygo.db.OrderDao;
import com.laundrygo.db.PaymentDao;
import com.laundrygo.db.ServiceTypeDao;
import com.laundrygo.model.Customer;
import com.laundrygo.model.Order;
import com.laundrygo.model.OrderStatus;
import com.laundrygo.model.PaymentMethod;
import com.laundrygo.model.ServiceType;
import com.laundrygo.model.User;
import com.laundrygo.util.Money;
import java.time.LocalDateTime;
import java.util.List;

/** Use cases around the order lifecycle. */
public final class OrderService {

    private final OrderDao orders;
    private final PaymentDao payments;
    private final AuditDao audit;
    private final PenaltyService penalty;

    public OrderService(OrderDao orders, PaymentDao payments, AuditDao audit, PenaltyService penalty,
                        CustomerDao customers, ServiceTypeDao serviceTypes) {
        this.orders = orders;
        this.payments = payments;
        this.audit = audit;
        this.penalty = penalty;
    }

    public List<Order> activeOrders() {
        return orders.findActive();
    }

    public List<Order> allOrders() {
        return orders.findAll();
    }

    public Order createOrder(Customer customer, ServiceType service, double units, String notes, User actor) {
        if (customer == null) {
            throw new IllegalArgumentException("A client is required.");
        }
        if (service == null) {
            throw new IllegalArgumentException("A service type is required.");
        }
        if (units <= 0) {
            throw new IllegalArgumentException("Weight / piece count must be greater than zero.");
        }
        double base = Money.round2(service.price() * units);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due = now.plusDays(Math.max(0, service.turnaroundDays()));
        long id = orders.insert(customer.id(), service.id(), units, service.price(), base,
                OrderStatus.PENDING, now, due, notes);
        Order order = orders.findById(id)
                .orElseThrow(() -> new IllegalStateException("Order was saved but cannot be reloaded"));
        audit.log(actor.getUsername(), "ORDER_CREATED",
                order.getCode() + " · " + customer.name() + " · " + Money.format(base));
        return order;
    }

    /** Flags late orders and applies penalties. Returns how many orders changed. */
    public int flagOverdueOrders(String actor) {
        int flagged = orders.flagOverdue(LocalDateTime.now(), penalty.perDay(), penalty.graceDays(),
                penalty.capPercent());
        if (flagged > 0) {
            audit.log(actor, "PENALTY_RUN", flagged + " order(s) flagged overdue and penalised");
        }
        return flagged;
    }

    public void changeStatus(Order order, OrderStatus target, User actor) {
        if (order.getStatus() == target) {
            return;
        }
        if (target == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Settle the balance from the payment panel to complete an order.");
        }
        if (target == OrderStatus.OVERDUE) {
            double fee = penalty.calculateLateFee(order.getDueAt(), LocalDateTime.now(), order.getBaseCost());
            orders.updateStatusAndFee(order.getId(), target, fee);
            order.setLateFee(fee);
        } else {
            orders.updateStatus(order.getId(), target);
        }
        order.setStatus(target);
        audit.log(actor.getUsername(), "STATUS_CHANGED", order.getCode() + " → " + target.displayName());
    }

    /** Records the settlement, completes the order and drops it from the active queue. */
    public void processPayment(Order order, PaymentMethod method, double amount, String reference, User actor) {
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("This order is already settled.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        if (amount + 0.005 < order.getBalance()) {
            throw new IllegalArgumentException("Payment does not cover the outstanding balance.");
        }
        LocalDateTime now = LocalDateTime.now();
        payments.insert(order.getId(), method, amount, reference, actor.getUsername(), now);
        orders.complete(order.getId(), amount, now);
        order.setAmountPaid(order.getAmountPaid() + amount);
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(now);
        audit.log(actor.getUsername(), "PAYMENT",
                order.getCode() + " · " + method.displayName() + " · " + Money.format(amount)
                        + (reference == null || reference.isBlank() ? "" : " · ref " + reference));
    }
}
