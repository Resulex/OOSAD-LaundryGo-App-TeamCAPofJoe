package com.laundrygo.db;

import com.laundrygo.model.OrderStatus;
import com.laundrygo.model.PaymentMethod;
import com.laundrygo.model.PenaltyPolicy;
import com.laundrygo.model.PricingMode;
import com.laundrygo.model.Role;
import com.laundrygo.util.PasswordHasher;
import com.laundrygo.util.Times;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * SQLite bootstrap: creates the schema on first run and seeds a realistic demo dataset so the
 * dashboards, penalty sweep and revenue widgets all have something to show.
 *
 * <p>The database file defaults to {@code laundrygo.db} in the working directory. Override it with
 * {@code -Dlaundrygo.db=/absolute/path/to/file.db}.</p>
 */
public final class Database {

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS users (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                username      TEXT NOT NULL UNIQUE,
                full_name     TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                salt          TEXT NOT NULL,
                role          TEXT NOT NULL,
                active        INTEGER NOT NULL DEFAULT 1,
                shift         TEXT NOT NULL DEFAULT '',
                created_at    TEXT NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS customers (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                name       TEXT NOT NULL,
                phone      TEXT NOT NULL,
                archived   INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS service_types (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                name            TEXT NOT NULL,
                pricing_mode    TEXT NOT NULL,
                price           REAL NOT NULL,
                turnaround_days INTEGER NOT NULL DEFAULT 2,
                active          INTEGER NOT NULL DEFAULT 1
            )""",
            """
            CREATE TABLE IF NOT EXISTS orders (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                code            TEXT UNIQUE,
                customer_id     INTEGER NOT NULL REFERENCES customers(id),
                service_type_id INTEGER NOT NULL REFERENCES service_types(id),
                weight          REAL NOT NULL,
                unit_price      REAL NOT NULL,
                base_cost       REAL NOT NULL,
                late_fee        REAL NOT NULL DEFAULT 0,
                amount_paid     REAL NOT NULL DEFAULT 0,
                status          TEXT NOT NULL,
                created_at      TEXT NOT NULL,
                due_at          TEXT NOT NULL,
                completed_at    TEXT,
                notes           TEXT
            )""",
            """
            CREATE TABLE IF NOT EXISTS payments (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id    INTEGER NOT NULL REFERENCES orders(id),
                method      TEXT NOT NULL,
                amount      REAL NOT NULL,
                reference   TEXT,
                received_by TEXT,
                created_at  TEXT NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS audit_log (
                id     INTEGER PRIMARY KEY AUTOINCREMENT,
                at     TEXT NOT NULL,
                actor  TEXT NOT NULL,
                action TEXT NOT NULL,
                detail TEXT
            )""",
            """
            CREATE TABLE IF NOT EXISTS settings (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )""",
            "CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status)",
            "CREATE INDEX IF NOT EXISTS idx_payments_created ON payments(created_at)"
    };

    private static boolean ready;

    private Database() {
    }

    public static synchronized void initialize() {
        if (ready) {
            return;
        }
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            for (String ddl : SCHEMA) {
                statement.execute(ddl);
            }
            migrate(connection);
            seed(connection);
            ready = true;
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to initialise the LaundryGo database", ex);
        }
    }

    /**
     * Brings databases created by older builds up to date. {@code CREATE TABLE IF NOT EXISTS} never
     * touches an existing table, so new columns are added here instead.
     */
    private static void migrate(Connection c) throws SQLException {
        addColumnIfMissing(c, "customers", "archived", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(c, "users", "shift", "TEXT NOT NULL DEFAULT ''");
    }

    private static void addColumnIfMissing(Connection c, String table, String column, String definition)
            throws SQLException {
        boolean present = false;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    present = true;
                    break;
                }
            }
        }
        if (!present) {
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    public static Connection open() throws SQLException {
        String path = System.getProperty("laundrygo.db", "laundrygo.db");
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 4000");
        }
        return connection;
    }

    // ------------------------------------------------------------------ seeding

    private static void seed(Connection c) throws SQLException {
        if (count(c, "users") > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        insertUser(c, "admin", "Ana Reyes", "admin123", Role.ADMIN, "Flexible");
        insertUser(c, "staff", "Mika Santos", "staff123", Role.STAFF, "Morning");
        insertUser(c, "joel", "Joel Ramos", "staff123", Role.STAFF, "Afternoon");

        setSetting(c, "penalty_per_day", "20");
        setSetting(c, "grace_days", "1");
        setSetting(c, "penalty_cap_percent", "100");

        long wdf = insertService(c, "Wash-Dry-Fold", PricingMode.PER_KG, 65.0, 2);
        long wdp = insertService(c, "Wash-Dry-Press", PricingMode.PER_KG, 75.0, 2);
        long dry = insertService(c, "Dry Clean", PricingMode.FLAT, 120.0, 3);
        long iron = insertService(c, "Ironing Only", PricingMode.PER_KG, 45.0, 1);
        long bedding = insertService(c, "Comforter Set", PricingMode.FLAT, 250.0, 3);

        long maria = insertCustomer(c, "Maria Dela Cruz", "0917 555 0134");
        long jomar = insertCustomer(c, "Jomar Reyes", "0918 442 8801");
        long liza = insertCustomer(c, "Liza Fernandez", "0995 220 4417");
        long paolo = insertCustomer(c, "Paolo Gutierrez", "0921 883 2094");
        long ana = insertCustomer(c, "Ana Villanueva", "0917 300 7788");
        long mark = insertCustomer(c, "Mark Bautista", "0927 664 1120");
        long grace = insertCustomer(c, "Grace Lim", "0916 118 9032");
        long carlo = insertCustomer(c, "Carlo Mendoza", "0908 771 5523");
        long rachel = insertCustomer(c, "Rachel Ong", "0917 449 6602");
        long dennis = insertCustomer(c, "Dennis Tan", "0929 556 3311");

        // Active queue -------------------------------------------------------
        seedOrder(c, maria, wdf, 4.5, 65.0, OrderStatus.PENDING,
                now.minusHours(2), now.plusHours(46), null, 0, null, null);
        seedOrder(c, jomar, wdp, 3.0, 75.0, OrderStatus.IN_PROGRESS,
                now.minusHours(5), now.plusHours(43), null, 0, null, null);
        seedOrder(c, liza, dry, 2, 120.0, OrderStatus.READY,
                now.minusHours(26), now.plusHours(22), null, 0, null, null);
        seedOrder(c, paolo, wdf, 7.2, 65.0, OrderStatus.IN_PROGRESS,
                now.minusHours(8), now.plusHours(40), null, 0, null, null);
        seedOrder(c, grace, wdf, 2.4, 65.0, OrderStatus.READY,
                now.minusHours(30), now.plusHours(18), null, 0, null, null);
        seedOrder(c, ana, bedding, 1, 250.0, OrderStatus.OVERDUE,
                now.minusHours(96), now.minusHours(50), null, 0, null, null);
        seedOrder(c, mark, iron, 5.0, 45.0, OrderStatus.OVERDUE,
                now.minusHours(72), now.minusHours(70), null, 0, null, null);

        // Settled today (feeds the revenue widget) ---------------------------
        seedOrder(c, carlo, wdp, 6.0, 75.0, OrderStatus.COMPLETED,
                now.minusHours(30), now.minusHours(6), PaymentMethod.CASH, 450.0, null, now.minusMinutes(120));
        seedOrder(c, rachel, dry, 3, 120.0, OrderStatus.COMPLETED,
                now.minusHours(28), now.minusHours(4), PaymentMethod.GCASH, 360.0, "GC-882134", now.minusMinutes(64));
        seedOrder(c, dennis, wdf, 5.5, 65.0, OrderStatus.COMPLETED,
                now.minusHours(26), now.minusHours(2), PaymentMethod.CASH, 357.5, null, now.minusMinutes(28));

        audit(c, "admin", "SYSTEM", "Demo dataset created", now.minusDays(1));
        audit(c, "staff", "ORDER_CREATED", "LG-1001 · Maria Dela Cruz · ₱292.50", now.minusHours(2));
        audit(c, "staff", "SMS_SENT", "Ready-for-pickup alert sent for LG-1003", now.minusHours(1));
    }

    private static void seedOrder(Connection c,
                                  long customerId,
                                  long serviceId,
                                  double units,
                                  double unitPrice,
                                  OrderStatus status,
                                  LocalDateTime created,
                                  LocalDateTime due,
                                  PaymentMethod method,
                                  double paid,
                                  String reference,
                                  LocalDateTime paidAt) throws SQLException {
        double base = Math.round(units * unitPrice * 100.0) / 100.0;
        double lateFee = status == OrderStatus.OVERDUE
                ? PenaltyPolicy.lateFee(due, LocalDateTime.now(), base, 20, 1, 100)
                : 0;

        long orderId;
        String insert = """
                INSERT INTO orders(customer_id, service_type_id, weight, unit_price, base_cost, late_fee,
                                   amount_paid, status, created_at, due_at, completed_at, notes)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""";
        try (PreparedStatement ps = c.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, customerId);
            ps.setLong(2, serviceId);
            ps.setDouble(3, units);
            ps.setDouble(4, unitPrice);
            ps.setDouble(5, base);
            ps.setDouble(6, lateFee);
            ps.setDouble(7, paid);
            ps.setString(8, status.name());
            ps.setString(9, Times.stamp(created));
            ps.setString(10, Times.stamp(due));
            ps.setString(11, status == OrderStatus.COMPLETED ? Times.stamp(paidAt) : null);
            ps.setString(12, "");
            ps.executeUpdate();
            orderId = generatedKey(ps);
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE orders SET code = ? WHERE id = ?")) {
            ps.setString(1, "LG-" + (1000 + orderId));
            ps.setLong(2, orderId);
            ps.executeUpdate();
        }
        if (paid > 0 && method != null) {
            insertPayment(c, orderId, method, paid, reference, "staff", paidAt);
        }
    }

    private static long insertUser(Connection c, String username, String fullName, String password,
                                   Role role, String shift) throws SQLException {
        String salt = PasswordHasher.newSalt();
        String sql = """
                INSERT INTO users(username, full_name, password_hash, salt, role, active, shift, created_at)
                VALUES (?,?,?,?,?,1,?,?)""";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, fullName);
            ps.setString(3, PasswordHasher.hash(password, salt));
            ps.setString(4, salt);
            ps.setString(5, role.name());
            ps.setString(6, shift == null ? "" : shift);
            ps.setString(7, Times.stamp(LocalDateTime.now()));
            ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    private static long insertService(Connection c, String name, PricingMode mode, double price, int turnaround)
            throws SQLException {
        String sql = "INSERT INTO service_types(name, pricing_mode, price, turnaround_days, active) VALUES (?,?,?,?,1)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, mode.name());
            ps.setDouble(3, price);
            ps.setInt(4, turnaround);
            ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    private static long insertCustomer(Connection c, String name, String phone) throws SQLException {
        String sql = "INSERT INTO customers(name, phone, created_at) VALUES (?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, Times.stamp(LocalDateTime.now()));
            ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    private static void insertPayment(Connection c, long orderId, PaymentMethod method, double amount,
                                      String reference, String receivedBy, LocalDateTime at) throws SQLException {
        String sql = "INSERT INTO payments(order_id, method, amount, reference, received_by, created_at) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ps.setString(2, method.name());
            ps.setDouble(3, amount);
            ps.setString(4, reference);
            ps.setString(5, receivedBy);
            ps.setString(6, Times.stamp(at));
            ps.executeUpdate();
        }
    }

    private static void setSetting(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO settings(key, value) VALUES (?,?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private static void audit(Connection c, String actor, String action, String detail, LocalDateTime at)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO audit_log(at, actor, action, detail) VALUES (?,?,?,?)")) {
            ps.setString(1, Times.stamp(at));
            ps.setString(2, actor);
            ps.setString(3, action);
            ps.setString(4, detail);
            ps.executeUpdate();
        }
    }

    private static long count(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static long generatedKey(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
            throw new SQLException("Insert did not return a generated key");
        }
    }
}
