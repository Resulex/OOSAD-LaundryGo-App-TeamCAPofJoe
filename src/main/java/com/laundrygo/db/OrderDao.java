package com.laundrygo.db;

import com.laundrygo.model.Order;
import com.laundrygo.model.OrderStatus;
import com.laundrygo.model.PenaltyPolicy;
import com.laundrygo.model.PricingMode;
import com.laundrygo.util.Times;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Order persistence, including the penalty sweep used by the dashboard. */
public final class OrderDao {

    private static final String BASE_SELECT = """
            SELECT o.id, o.code, o.customer_id, c.name AS customer_name, c.phone AS customer_phone,
                   o.service_type_id, s.name AS service_name, s.pricing_mode,
                   o.weight, o.unit_price, o.base_cost, o.late_fee, o.amount_paid, o.status,
                   o.created_at, o.due_at, o.completed_at, o.notes
            FROM orders o
            JOIN customers c ON c.id = o.customer_id
            JOIN service_types s ON s.id = o.service_type_id
            """;

    public List<Order> findActive() {
        return query(BASE_SELECT + """
                 WHERE o.status <> 'COMPLETED'
                ORDER BY CASE o.status
                            WHEN 'OVERDUE' THEN 0
                            WHEN 'READY' THEN 1
                            WHEN 'IN_PROGRESS' THEN 2
                            ELSE 3
                         END,
                         o.due_at ASC
                """);
    }

    public List<Order> findAll() {
        return query(BASE_SELECT + " ORDER BY o.created_at DESC");
    }

    public Optional<Order> findById(long id) {
        return query(BASE_SELECT + " WHERE o.id = ?", id).stream().findFirst();
    }

    public long insert(long customerId,
                       long serviceTypeId,
                       double weight,
                       double unitPrice,
                       double baseCost,
                       OrderStatus status,
                       LocalDateTime createdAt,
                       LocalDateTime dueAt,
                       String notes) {
        String sql = """
                INSERT INTO orders(customer_id, service_type_id, weight, unit_price, base_cost, late_fee,
                                   amount_paid, status, created_at, due_at, completed_at, notes)
                VALUES (?,?,?,?,?,0,0,?,?,?,NULL,?)""";
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, customerId);
            ps.setLong(2, serviceTypeId);
            ps.setDouble(3, weight);
            ps.setDouble(4, unitPrice);
            ps.setDouble(5, baseCost);
            ps.setString(6, status.name());
            ps.setString(7, Times.stamp(createdAt));
            ps.setString(8, Times.stamp(dueAt));
            ps.setString(9, notes == null ? "" : notes.trim());
            ps.executeUpdate();

            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                id = keys.next() ? keys.getLong(1) : -1;
            }
            try (PreparedStatement update = c.prepareStatement("UPDATE orders SET code = ? WHERE id = ?")) {
                update.setString(1, "LG-" + (1000 + id));
                update.setLong(2, id);
                update.executeUpdate();
            }
            return id;
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to save the order", ex);
        }
    }

    public void updateStatus(long id, OrderStatus status) {
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement("UPDATE orders SET status = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to update the order status", ex);
        }
    }

    public void updateStatusAndFee(long id, OrderStatus status, double lateFee) {
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE orders SET status = ?, late_fee = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setDouble(2, lateFee);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to apply the penalty", ex);
        }
    }

    public void complete(long id, double paidAmount, LocalDateTime completedAt) {
        String sql = """
                UPDATE orders
                   SET status = 'COMPLETED',
                       amount_paid = amount_paid + ?,
                       completed_at = ?
                 WHERE id = ?""";
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, paidAmount);
            ps.setString(2, Times.stamp(completedAt));
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to settle the order", ex);
        }
    }

    /**
     * Flags every open order whose due date has passed and records the accrued penalty.
     *
     * @return how many orders were flagged
     */
    public int flagOverdue(LocalDateTime now, double perDay, int graceDays, double capPercent) {
        String select = """
                SELECT id, base_cost, due_at
                  FROM orders
                 WHERE status IN ('PENDING', 'IN_PROGRESS', 'READY')
                   AND due_at < ?""";
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(select)) {
            ps.setString(1, Times.stamp(now));
            List<long[]> pending = new ArrayList<>();
            List<Double> fees = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime dueAt = Times.parse(rs.getString("due_at"));
                    double fee = PenaltyPolicy.lateFee(dueAt, now, rs.getDouble("base_cost"), perDay, graceDays, capPercent);
                    pending.add(new long[]{rs.getLong("id")});
                    fees.add(fee);
                }
            }
            if (pending.isEmpty()) {
                return 0;
            }
            try (PreparedStatement update = c.prepareStatement(
                    "UPDATE orders SET status = 'OVERDUE', late_fee = ? WHERE id = ?")) {
                for (int i = 0; i < pending.size(); i++) {
                    update.setDouble(1, fees.get(i));
                    update.setLong(2, pending.get(i)[0]);
                    update.addBatch();
                }
                update.executeBatch();
            }
            return pending.size();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to run the penalty sweep", ex);
        }
    }

    public int countActive() {
        return count("SELECT COUNT(*) FROM orders WHERE status <> 'COMPLETED'");
    }

    public int countByStatus(OrderStatus status) {
        return count("SELECT COUNT(*) FROM orders WHERE status = '" + status.name() + "'");
    }

    private int count(String sql) {
        try (Connection c = Database.open();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to count orders", ex);
        }
    }

    private List<Order> query(String sql, Object... params) {
        List<Order> orders = new ArrayList<>();
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(map(rs));
                }
            }
            return orders;
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to load orders", ex);
        }
    }

    private Order map(ResultSet rs) throws SQLException {
        return new Order(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getLong("customer_id"),
                rs.getString("customer_name"),
                rs.getString("customer_phone"),
                rs.getLong("service_type_id"),
                rs.getString("service_name"),
                PricingMode.fromDb(rs.getString("pricing_mode")),
                rs.getDouble("weight"),
                rs.getDouble("unit_price"),
                rs.getDouble("base_cost"),
                OrderStatus.fromDb(rs.getString("status")),
                rs.getDouble("late_fee"),
                rs.getDouble("amount_paid"),
                Times.parse(rs.getString("created_at")),
                Times.parse(rs.getString("due_at")),
                Times.parse(rs.getString("completed_at")),
                rs.getString("notes"));
    }
}
