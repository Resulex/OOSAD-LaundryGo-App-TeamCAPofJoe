package com.laundrygo;

import com.laundrygo.db.Database;
import com.laundrygo.model.Customer;
import com.laundrygo.model.Order;
import com.laundrygo.model.OrderStatus;
import com.laundrygo.model.PaymentMethod;
import com.laundrygo.model.ServiceType;
import com.laundrygo.model.User;
import com.laundrygo.service.AppServices;
import java.util.List;

/**
 * Headless verification of the business layer (no JavaFX toolkit required).
 *
 * <p>Run with a scratch database so the demo data stays untouched:</p>
 * <pre>
 * mvn -q compile exec:java -Dexec.mainClass=com.laundrygo.SmokeTest \
 *     -Dlaundrygo.db=/tmp/laundrygo-smoke.db
 * </pre>
 */
public final class SmokeTest {

    private static int checks;

    public static void main(String[] args) {
        Database.initialize();
        AppServices services = new AppServices();

        // --- authentication -------------------------------------------------
        User staff = services.auth().authenticate("staff", "staff123")
                .orElseThrow(() -> new AssertionError("staff login failed"));
        expect("staff role", staff.getRole().name().equals("STAFF"));
        expect("wrong password rejected", services.auth().authenticate("staff", "nope").isEmpty());
        expect("unknown user rejected", services.auth().authenticate("ghost", "x").isEmpty());
        User owner = services.auth().authenticate("admin", "admin123")
                .orElseThrow(() -> new AssertionError("admin login failed"));
        expect("admin role", owner.getRole().name().equals("ADMIN"));

        // --- seeded dashboard numbers ---------------------------------------
        double revenueBefore = services.reports().revenueToday();
        expect("seeded revenue today > 0", revenueBefore > 0);
        expect("seeded active orders > 0", services.reports().activeOrders() > 0);
        expect("seeded overdue orders > 0", services.reports().overdueOrders() > 0);

        // --- penalty sweep ---------------------------------------------------
        int flagged = services.orderService().flagOverdueOrders(staff.getUsername());
        System.out.println("penalty sweep flagged: " + flagged);

        // --- create order ----------------------------------------------------
        Customer customer = services.customers().findAll().get(0);
        ServiceType service = services.serviceTypes().findAll(true).get(0);
        Order order = services.orderService().createOrder(customer, service, 3.0, "smoke test", staff);
        double expectedBase = Math.round(service.price() * 3.0 * 100.0) / 100.0;
        expect("code generated", order.getCode() != null && order.getCode().startsWith("LG-"));
        expect("base cost computed", Math.abs(order.getBaseCost() - expectedBase) < 0.001);
        expect("due date in future", order.getDueAt().isAfter(java.time.LocalDateTime.now()));
        expect("starts pending", order.getStatus() == OrderStatus.PENDING);

        // --- status transitions ---------------------------------------------
        services.orderService().changeStatus(order, OrderStatus.IN_PROGRESS, staff);
        expect("status is in-progress", order.getStatus() == OrderStatus.IN_PROGRESS);
        services.orderService().changeStatus(order, OrderStatus.OVERDUE, staff);
        expect("overdue transition persisted", order.getStatus() == OrderStatus.OVERDUE);
        expect("no penalty before the due date", order.getLateFee() == 0);

        double policyFee = com.laundrygo.model.PenaltyPolicy.lateFee(
                java.time.LocalDateTime.now().minusDays(5),
                java.time.LocalDateTime.now(),
                500.0, 20.0, 1, 100.0);
        expect("policy: 5 days late, 1 grace day = 4 x 20", Math.abs(policyFee - 80.0) < 0.001);
        expect("seeded overdue order carries a fee", services.orderService().activeOrders().stream()
                .anyMatch(candidate -> candidate.getStatus() == OrderStatus.OVERDUE
                        && candidate.getLateFee() > 0));

        services.orderService().changeStatus(order, OrderStatus.READY, staff);
        expect("status is ready", order.getStatus() == OrderStatus.READY);

        // --- payment ----------------------------------------------------------
        double balance = order.getBalance();
        expect("balance equals base + fee", Math.abs(balance - (order.getBaseCost() + order.getLateFee())) < 0.001);
        services.orderService().processPayment(order, PaymentMethod.GCASH, balance, "GC-TEST-01", staff);
        expect("order completed", order.getStatus() == OrderStatus.COMPLETED);
        expect("balance cleared", order.getBalance() < 0.001);

        List<Order> active = services.orderService().activeOrders();
        expect("completed order left the queue",
                active.stream().noneMatch(candidate -> candidate.getCode().equals(order.getCode())));
        expect("revenue increased", services.reports().revenueToday() > revenueBefore);

        // --- catalogue + settings --------------------------------------------
        int serviceCount = services.serviceTypes().findAll(false).size();
        expect("service catalogue seeded", serviceCount >= 5);
        services.penalty().update(35, 2, 150);
        expect("penalty settings persist", Math.abs(services.penalty().perDay() - 35) < 0.001);
        services.penalty().update(20, 1, 100);

        System.out.println("revenue today: " + services.reports().revenueToday());
        System.out.println("active orders: " + services.reports().activeOrders());
        System.out.println("audit rows: " + services.reports().recentActivity(50).size());
        System.out.println("SMOKE OK — " + checks + " checks passed");
    }

    private static void expect(String description, boolean condition) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + description);
        }
        System.out.println("  ok · " + description);
    }
}
