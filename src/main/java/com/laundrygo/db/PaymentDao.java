package com.laundrygo.db;

import com.laundrygo.model.Payment;
import com.laundrygo.model.PaymentMethod;
import com.laundrygo.util.Times;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Payment ledger — the source of truth for revenue reporting. */
public final class PaymentDao {

    private static final String BASE_SELECT = """
            SELECT p.id, p.created_at, p.method, p.amount, p.reference, p.received_by,
                   o.code AS order_code, c.name AS customer_name
            FROM payments p
            JOIN orders o ON o.id = p.order_id
            JOIN customers c ON c.id = o.customer_id
            """;

    public void insert(long orderId, PaymentMethod method, double amount, String reference,
                       String receivedBy, LocalDateTime at) {
        String sql = """
                INSERT INTO payments(order_id, method, amount, reference, received_by, created_at)
                VALUES (?,?,?,?,?,?)""";
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ps.setString(2, method.name());
            ps.setDouble(3, amount);
            ps.setString(4, reference);
            ps.setString(5, receivedBy);
            ps.setString(6, Times.stamp(at));
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to record the payment", ex);
        }
    }

    public List<Payment> findBetween(LocalDate from, LocalDate to) {
        String sql = BASE_SELECT + " WHERE date(p.created_at) BETWEEN date(?) AND date(?) ORDER BY p.created_at DESC";
        List<Payment> payments = new ArrayList<>();
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payments.add(new Payment(
                            rs.getLong("id"),
                            Times.parse(rs.getString("created_at")),
                            rs.getString("order_code"),
                            rs.getString("customer_name"),
                            PaymentMethod.fromDb(rs.getString("method")),
                            rs.getDouble("amount"),
                            rs.getString("reference"),
                            rs.getString("received_by")));
                }
            }
            return payments;
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to load payments", ex);
        }
    }

    /** Latest settlement recorded against an order — used when reprinting a receipt. */
    public Optional<Payment> findForOrder(long orderId) {
        String sql = BASE_SELECT + " WHERE p.order_id = ? ORDER BY p.id DESC LIMIT 1";
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Payment(
                        rs.getLong("id"),
                        Times.parse(rs.getString("created_at")),
                        rs.getString("order_code"),
                        rs.getString("customer_name"),
                        PaymentMethod.fromDb(rs.getString("method")),
                        rs.getDouble("amount"),
                        rs.getString("reference"),
                        rs.getString("received_by")));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to load the payment", ex);
        }
    }

    public double sumBetween(LocalDate from, LocalDate to) {        String sql = "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE date(created_at) BETWEEN date(?) AND date(?)";
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to total revenue", ex);
        }
    }
}
