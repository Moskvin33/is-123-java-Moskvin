package solarsystem;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RedDbCommand {
    private final ConcurrentLinkedQueue<DbCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private final HikariDataSource dataSource;
    private volatile boolean running = true;

    public RedDbCommand(String host, int port, String dbPath, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:firebirdsql://%s:%d/%s", host, port, dbPath));
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("org.firebirdsql.jdbc.FBDriver");
        config.setMaximumPoolSize(2);
        config.setConnectionTimeout(10000);
        this.dataSource = new HikariDataSource(config);
        testConnection();
    }

    private void testConnection() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT rdb$get_context('SYSTEM', 'DB_NAME') FROM rdb$database");
            if (rs.next()) System.out.println(" RED Database connected: " + rs.getString(1));
        } catch (SQLException e) {
            System.err.println(" DB connection error: " + e.getMessage());
        }
    }

    public void startPolling(int intervalSeconds) {
        Thread poller = new Thread(() -> {
            while (running) {
                try {
                    pollNewCommands();
                    Thread.sleep(intervalSeconds * 1000L);
                } catch (InterruptedException | SQLException e) {
                    if (!(e instanceof InterruptedException))
                        System.err.println(" DB poll error: " + e.getMessage());
                }
            }
        }, "RedDb-Poller");
        poller.setDaemon(true);
        poller.start();
        System.out.println("📡 RedDb listener started (poll every " + intervalSeconds + "s)");
    }

    private void pollNewCommands() throws SQLException {
        String sql = "SELECT id, action, name, radius, mass, orbit_radius, color_hex " +
                "FROM solar_commands WHERE processed = FALSE ORDER BY created_at ROWS 10";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                DbCommand cmd = new DbCommand();
                cmd.dbId = rs.getInt("id");
                cmd.action = rs.getString("action");
                cmd.name = rs.getString("name");
                cmd.radius = rs.getDouble("radius");
                cmd.mass = rs.getDouble("mass");
                cmd.orbit = rs.getDouble("orbit_radius");
                cmd.colorHex = rs.getString("color_hex");
                commandQueue.add(cmd);
            }
        }
    }

    public void markCommandProcessed(int commandId) {
        new Thread(() -> {
            String sql = "UPDATE solar_commands SET processed = TRUE WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, commandId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println(" Failed to mark command: " + e.getMessage());
            }
        }).start();
    }

    public void processCommands(Main3D app) {
        DbCommand cmd;
        while ((cmd = commandQueue.poll()) != null) {
            try {
                app.handleDbCommand(cmd);
                markCommandProcessed(cmd.dbId);
            } catch (Exception e) {
                System.err.println(" Command error: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        if (dataSource != null) dataSource.close();
    }

    public static class DbCommand {
        public int dbId;
        public String action, name, colorHex;
        public double radius, mass, orbit;
    }
}