package com.laundrygo.db;

import com.laundrygo.model.AuditEntry;
import com.laundrygo.util.Times;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Append-only activity trail shown on the owner dashboard. */
public final class AuditDao {

    public void log(String actor, String action, String detail) {
        String sql = "INSERT INTO audit_log(at, actor, action, detail) VALUES (?,?,?,?)";
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Times.stamp(LocalDateTime.now()));
            ps.setString(2, actor == null ? "system" : actor);
            ps.setString(3, action);
            ps.setString(4, detail);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to write the audit entry", ex);
        }
    }

    public List<AuditEntry> recent(int limit) {
        String sql = "SELECT id, at, actor, action, detail FROM audit_log ORDER BY at DESC, id DESC LIMIT ?";
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new AuditEntry(
                            rs.getLong("id"),
                            Times.parse(rs.getString("at")),
                            rs.getString("actor"),
                            rs.getString("action"),
                            rs.getString("detail")));
                }
            }
            return entries;
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to load the audit trail", ex);
        }
    }
}
