package com.job.portal.monitoring;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * Makes sure every table, column and index the monitoring / engagement features
 * need actually exists — whatever the deployment does with Hibernate DDL or SQL
 * init scripts. Runs once at startup, before scheduled jobs, using JDBC metadata,
 * and only ever adds (never drops or alters existing columns).
 *
 * Supports PostgreSQL, H2 and MySQL/MariaDB. Results are shown on the admin
 * Health page so schema drift is visible instead of surfacing as 500s.
 */
@Slf4j
@Component
public class MonitoringSchemaGuard {

    record Col(String name, String type, boolean notNull) {
    }

    record Table(String name, boolean identity, List<Col> cols, List<List<String>> unique, List<String> indexes, String primaryKey) {
    }

    private static Col c(String name, String type) {
        return new Col(name, type, false);
    }

    private static Col nn(String name, String type) {
        return new Col(name, type, true);
    }

    static final List<Table> TABLES = List.of(
            new Table("monitor_logs", true, List.of(
                    nn("created_at", "TS"), nn("kind", "VARCHAR(16)"), c("method", "VARCHAR(10)"), c("path", "VARCHAR(500)"),
                    c("query", "VARCHAR(500)"), c("status", "INTEGER"), c("duration_ms", "BIGINT"), c("ip", "VARCHAR(64)"),
                    c("username", "VARCHAR(120)"), c("user_email", "VARCHAR(160)"), c("visitor_id", "VARCHAR(64)"),
                    c("session_id", "VARCHAR(64)"), c("user_agent", "VARCHAR(400)"), c("browser", "VARCHAR(40)"),
                    c("os", "VARCHAR(40)"), c("device", "VARCHAR(20)"), c("country", "VARCHAR(80)"), c("city", "VARCHAR(120)"),
                    c("label", "VARCHAR(500)"), c("detail", "VARCHAR(1000)"), c("app_version", "VARCHAR(40)")),
                    List.of(), List.of("created_at", "kind", "visitor_id"), null),
            new Table("monitor_visitor_days", true, List.of(nn("visit_day", "DATE"), nn("visitor_id", "VARCHAR(64)")),
                    List.of(List.of("visit_day", "visitor_id")), List.of(), null),
            new Table("daily_challenge_results", true, List.of(
                    nn("challenge_day", "INTEGER"), nn("learner_id", "VARCHAR(36)"), nn("score", "INTEGER"),
                    nn("answers", "VARCHAR(8)"), nn("duration_ms", "BIGINT"), nn("created_at", "TS")),
                    List.of(List.of("challenge_day", "learner_id")), List.of("challenge_day"), null),
            new Table("visitor_profiles", true, List.of(
                    nn("visitor_id", "VARCHAR(64)"), c("first_seen", "TS"), c("last_seen", "TS"), c("visits", "INTEGER"),
                    c("page_views", "INTEGER"), c("landing_page", "VARCHAR(300)"), c("last_page", "VARCHAR(300)"),
                    c("source", "VARCHAR(200)"), c("first_referrer", "VARCHAR(500)"), c("utm_medium", "VARCHAR(120)"),
                    c("utm_campaign", "VARCHAR(120)"), c("country", "VARCHAR(80)"), c("country_code", "VARCHAR(8)"),
                    c("region", "VARCHAR(120)"), c("city", "VARCHAR(120)"), c("timezone", "VARCHAR(60)"), c("isp", "VARCHAR(160)"),
                    c("ip", "VARCHAR(64)"), c("browser", "VARCHAR(60)"), c("os", "VARCHAR(60)"), c("device", "VARCHAR(20)"),
                    c("language", "VARCHAR(20)"), c("screen", "VARCHAR(20)"), c("username", "VARCHAR(120)"),
                    c("email", "VARCHAR(160)"), c("bot", "BOOLEAN"), c("last_notified_at", "TS"), c("details", "VARCHAR(4000)"),
                    c("name", "VARCHAR(160)"), c("identity_source", "VARCHAR(20)"), c("identified_at", "TS")),
                    List.of(List.of("visitor_id")), List.of("last_seen", "first_seen"), null),
            new Table("monitor_settings", false, List.of(nn("setting_key", "VARCHAR(80)"), c("setting_value", "VARCHAR(2000)")),
                    List.of(), List.of(), "setting_key"),
            new Table("newsletter_subscribers", true, List.of(
                    c("email", "VARCHAR(255)"), c("subscribed_at", "TSL"), c("name", "VARCHAR(120)"), c("visitor_id", "VARCHAR(64)"),
                    c("source", "VARCHAR(60)"), c("wants_daily", "BOOLEAN"), c("wants_weekly", "BOOLEAN"), c("active", "BOOLEAN"),
                    c("token", "VARCHAR(48)"), c("last_sent_at", "TS"), c("unsubscribed_at", "TS")),
                    List.of(), List.of("token"), null),
            new Table("user_progress", false, List.of(
                    nn("username", "VARCHAR(120)"), c("email", "VARCHAR(160)"), c("data", "BIGTEXT"), c("updated_at", "TS")),
                    List.of(), List.of(), "username"),
            new Table("certificates", false, List.of(
                    nn("code", "VARCHAR(16)"), nn("kind", "VARCHAR(40)"), nn("title", "VARCHAR(200)"), c("description", "VARCHAR(500)"),
                    nn("recipient_name", "VARCHAR(120)"), c("username", "VARCHAR(120)"), c("email", "VARCHAR(160)"), nn("issued_at", "TS")),
                    List.of(), List.of("username"), "code"),
            new Table("push_subscriptions", true, List.of(
                    nn("endpoint", "VARCHAR(600)"), nn("p256dh", "VARCHAR(200)"), nn("auth", "VARCHAR(100)"), c("visitor_id", "VARCHAR(64)"),
                    c("created_at", "TS"), c("last_success_at", "TS"), c("failures", "INTEGER")),
                    List.of(List.of("endpoint")), List.of(), null));

    private final DataSource dataSource;
    private final List<String> actions = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private volatile String database = "unknown";
    private volatile long checkedAt;

    public MonitoringSchemaGuard(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void ensureSchema() {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            DatabaseMetaData md = conn.getMetaData();
            database = md.getDatabaseProductName();
            String product = database.toLowerCase(Locale.ROOT);
            boolean mysql = product.contains("mysql") || product.contains("mariadb");
            String catalog = conn.getCatalog();
            String schema = mysql ? null : conn.getSchema();

            Map<String, String> existing = new HashMap<>(); // lower-case name → stored name
            try (ResultSet rs = md.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) existing.put(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT), rs.getString("TABLE_NAME"));
            }

            for (Table t : TABLES) {
                try {
                    if (!existing.containsKey(t.name())) {
                        exec(conn, createSql(t, mysql));
                        actions.add("Created table " + t.name());
                    } else {
                        Set<String> cols = new HashSet<>();
                        try (ResultSet rs = md.getColumns(catalog, schema, existing.get(t.name()), "%")) {
                            while (rs.next()) cols.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                        }
                        for (Col col : t.cols()) {
                            if (!cols.contains(col.name())) {
                                // Added as nullable: existing rows have no value for it.
                                exec(conn, "ALTER TABLE " + t.name() + " ADD COLUMN " + col.name() + " " + type(col.type(), mysql));
                                actions.add("Added column " + t.name() + "." + col.name());
                            }
                        }
                    }
                    ensureIndexes(conn, md, catalog, schema, t, mysql);
                } catch (Exception e) {
                    String msg = t.name() + ": " + MonitorLogWriter.rootMessage(e);
                    problems.add(msg);
                    log.error("Schema guard could not prepare {}", t.name(), e);
                }
            }
        } catch (Exception e) {
            problems.add("Schema check failed: " + MonitorLogWriter.rootMessage(e));
            log.error("Schema guard failed", e);
        }
        checkedAt = System.currentTimeMillis();
        if (!actions.isEmpty()) log.info("Schema guard applied: {}", actions);
    }

    private void ensureIndexes(Connection conn, DatabaseMetaData md, String catalog, String schema, Table t, boolean mysql) throws Exception {
        if (t.indexes().isEmpty()) return;
        Set<String> indexed = new HashSet<>();
        String stored = storedName(md, catalog, schema, t.name());
        try (ResultSet rs = md.getIndexInfo(catalog, schema, stored, false, true)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col != null && rs.getShort("ORDINAL_POSITION") == 1) indexed.add(col.toLowerCase(Locale.ROOT));
            }
        }
        for (String col : t.indexes()) {
            if (indexed.contains(col)) continue;
            String name = "idx_" + t.name() + "_" + col;
            exec(conn, mysql ? "CREATE INDEX " + name + " ON " + t.name() + " (" + col + ")"
                    : "CREATE INDEX IF NOT EXISTS " + name + " ON " + t.name() + " (" + col + ")");
            actions.add("Created index " + name);
        }
    }

    private static String storedName(DatabaseMetaData md, String catalog, String schema, String name) throws Exception {
        try (ResultSet rs = md.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                if (rs.getString("TABLE_NAME").equalsIgnoreCase(name)) return rs.getString("TABLE_NAME");
            }
        }
        return name;
    }

    static String createSql(Table t, boolean mysql) {
        List<String> parts = new ArrayList<>();
        if (t.identity()) {
            parts.add(mysql ? "id BIGINT AUTO_INCREMENT PRIMARY KEY" : "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY");
        }
        for (Col col : t.cols()) {
            String def = col.name() + " " + type(col.type(), mysql) + (col.notNull() ? " NOT NULL" : "");
            if (col.name().equals(t.primaryKey())) def += " PRIMARY KEY";
            parts.add(def);
        }
        int i = 0;
        for (List<String> u : t.unique()) {
            parts.add("CONSTRAINT uk_" + t.name() + "_" + (i++) + " UNIQUE (" + String.join(", ", u) + ")");
        }
        return "CREATE TABLE IF NOT EXISTS " + t.name() + " (" + String.join(", ", parts) + ")";
    }

    static String type(String token, boolean mysql) {
        return switch (token) {
            case "TS" -> mysql ? "DATETIME(6)" : "TIMESTAMP(6) WITH TIME ZONE";
            case "TSL" -> mysql ? "DATETIME(6)" : "TIMESTAMP(6)";
            case "BIGTEXT" -> mysql ? "MEDIUMTEXT" : "VARCHAR(100000)";
            default -> token;
        };
    }

    private static void exec(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    /** For the admin Health page. */
    public Map<String, Object> report() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("database", database);
        m.put("checkedAt", checkedAt);
        m.put("actions", List.copyOf(actions));
        m.put("problems", List.copyOf(problems));
        m.put("ok", problems.isEmpty());
        return m;
    }
}
