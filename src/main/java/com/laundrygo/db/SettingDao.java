package com.laundrygo.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Key/value configuration (penalty rates and similar shop settings). */
public final class SettingDao {

    public String get(String key, String fallback) {
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : fallback;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read setting " + key, ex);
        }
    }

    public double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public int getInt(String key, int fallback) {
        try {
            return (int) Math.round(Double.parseDouble(get(key, String.valueOf(fallback))));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public void set(String key, String value) {
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO settings(key, value) VALUES (?,?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to save setting " + key, ex);
        }
    }
}
