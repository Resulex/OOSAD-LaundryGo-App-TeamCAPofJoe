package com.laundrygo.db;

import com.laundrygo.model.PricingMode;
import com.laundrygo.model.ServiceType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Service catalogue and the prices used to compute base cost. */
public final class ServiceTypeDao {

    public List<ServiceType> findAll(boolean activeOnly) {
        String sql = "SELECT id, name, pricing_mode, price, turnaround_days, active FROM service_types"
                + (activeOnly ? " WHERE active = 1" : "")
                + " ORDER BY name COLLATE NOCASE";
        List<ServiceType> services = new ArrayList<>();
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                services.add(map(rs));
            }
            return services;
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to load service types", ex);
        }
    }

    public long insert(String name, PricingMode mode, double price, int turnaroundDays) {
        String sql = "INSERT INTO service_types(name, pricing_mode, price, turnaround_days, active) VALUES (?,?,?,?,1)";
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, mode.name());
            ps.setDouble(3, price);
            ps.setInt(4, turnaroundDays);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to add the service type", ex);
        }
    }

    public void update(long id, String name, PricingMode mode, double price, int turnaroundDays, boolean active) {
        String sql = "UPDATE service_types SET name = ?, pricing_mode = ?, price = ?, turnaround_days = ?, active = ? WHERE id = ?";
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, mode.name());
            ps.setDouble(3, price);
            ps.setInt(4, turnaroundDays);
            ps.setInt(5, active ? 1 : 0);
            ps.setLong(6, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to update the service type", ex);
        }
    }

    private ServiceType map(ResultSet rs) throws SQLException {
        return new ServiceType(
                rs.getLong("id"),
                rs.getString("name"),
                PricingMode.fromDb(rs.getString("pricing_mode")),
                rs.getDouble("price"),
                rs.getInt("turnaround_days"),
                rs.getInt("active") == 1);
    }
}
