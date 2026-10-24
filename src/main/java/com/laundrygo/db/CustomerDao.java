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

/** Client directory. */
public final class CustomerDao {

    public List<Customer> findAll() {
        String sql = "SELECT id, name, phone, created_at FROM customers ORDER BY name COLLATE NOCASE";
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

    private Customer map(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("phone"),
                Times.parse(rs.getString("created_at")));
    }
}
