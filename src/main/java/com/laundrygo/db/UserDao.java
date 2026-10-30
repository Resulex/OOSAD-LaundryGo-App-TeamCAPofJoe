package com.laundrygo.db;

import com.laundrygo.model.Role;
import com.laundrygo.model.User;
import com.laundrygo.util.PasswordHasher;
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

/** Reads and writes staff accounts. */
public final class UserDao {

    /** Stored credentials for a single account — only the auth flow should touch this. */
    public record Credential(long id, String username, String fullName, Role role, boolean active,
                             String shift, String hash, String salt) {
    }

    public Optional<Credential> credentials(String username) {
        String sql = """
                SELECT id, username, full_name, role, active, shift, password_hash, salt
                  FROM users WHERE username = ?""";
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Credential(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        Role.fromDb(rs.getString("role")),
                        rs.getInt("active") == 1,
                        text(rs.getString("shift")),
                        rs.getString("password_hash"),
                        rs.getString("salt")));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read credentials", ex);
        }
    }

    public List<User> findAll() {
        String sql = """
                SELECT id, username, full_name, role, active, shift
                  FROM users ORDER BY role, full_name""";
        List<User> users = new ArrayList<>();
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(new User(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        Role.fromDb(rs.getString("role")),
                        rs.getInt("active") == 1,
                        text(rs.getString("shift"))));
            }
            return users;
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to list staff accounts", ex);
        }
    }

    /** Updates the profile fields the owner is allowed to change on an existing account. */
    public void updateProfile(long id, String fullName, Role role, String shift) {
        String sql = "UPDATE users SET full_name = ?, role = ?, shift = ? WHERE id = ?";
        try (Connection c = Database.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, role.name());
            ps.setString(3, shift == null ? "" : shift);
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to update the account", ex);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    public long create(String fullName, String username, String password, Role role) {
        return create(fullName, username, password, role, "");
    }

    public long create(String fullName, String username, String password, Role role, String shift) {
        String salt = PasswordHasher.newSalt();
        String sql = """
                INSERT INTO users(username, full_name, password_hash, salt, role, active, shift, created_at)
                VALUES (?,?,?,?,?,1,?,?)""";
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, fullName);
            ps.setString(3, PasswordHasher.hash(password, salt));
            ps.setString(4, salt);
            ps.setString(5, role.name());
            ps.setString(6, shift == null ? "" : shift);
            ps.setString(7, Times.stamp(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to create the account: " + ex.getMessage(), ex);
        }
    }

    public void setActive(long id, boolean active) {
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET active = ? WHERE id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to update the account", ex);
        }
    }

    public void resetPassword(long id, String password) {
        String salt = PasswordHasher.newSalt();
        try (Connection c = Database.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE users SET password_hash = ?, salt = ? WHERE id = ?")) {
            ps.setString(1, PasswordHasher.hash(password, salt));
            ps.setString(2, salt);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to reset the password", ex);
        }
    }
}
