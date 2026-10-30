package com.laundrygo.db;

import com.laundrygo.model.Customer;
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

/** Client directory. Archived clients are kept for financial history but hidden from pickers. */
public final class CustomerDao {

    public List<Customer> findAll() {
        return findAll(false);
    }

    public List<Customer> findAll(boolean includeArchived) {
        String sql = """
                SELECT id, name, phone, created_at
                  FROM customers
                """ + (includeArchived ? "" : " WHERE archived = 0")
                + " ORDER BY name COLLATE NOCASE";
        List<Customer> customers = new ArrayList<>();
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                customers.add(map(rs));
            }
            return customers;
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to load clients", ex);
        }
    }

    public Optional<Customer> findById(long id) {
        String sql = "SELECT id, name, phone, created_at FROM customers WHERE id = ?";
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to load the client", ex);
        }
    }

    /** Case-insensitive lookup across name and phone number. */
    public List<Customer> search(String query) {
        String like = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        String sql = """
                SELECT id, name, phone, created_at
                  FROM customers
                 WHERE archived = 0
                   AND (LOWER(name) LIKE ? OR REPLACE(phone, ' ', '') LIKE ?)
                ORDER BY name COLLATE NOCASE""";
        List<Customer> customers = new ArrayList<>();
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like.replace(" ", ""));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    customers.add(map(rs));
                }
            }
            return customers;
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to search clients", ex);
        }
    }

    public Customer insert(String name, String phone) {
        String sql = "INSERT INTO customers(name, phone, created_at) VALUES (?,?,?)";
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, Times.stamp(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : -1;
                return new Customer(id, name, phone, LocalDateTime.now());
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to save the client", ex);
        }
    }

    /** Updates the editable profile fields of an existing client. */
    public void update(long id, String name, String phone) {
        String sql = "UPDATE customers SET name = ?, phone = ? WHERE id = ?";
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to update the client", ex);
        }
    }

    /** Soft delete — the client disappears from the directory but their orders stay auditable. */
    public void archive(long id) {
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement("UPDATE customers SET archived = 1 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to remove the client", ex);
        }
    }

    private Customer map(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("phone"),
                Times.parse(rs.getString("created_at")));
    }
}
