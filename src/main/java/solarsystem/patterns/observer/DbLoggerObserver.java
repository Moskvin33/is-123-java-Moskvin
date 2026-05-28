package solarsystem.patterns.observer;

import solarsystem.SolarDatabaseInitializer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;

public class DbLoggerObserver implements SimulationObserver {
    @Override
    public void onEvent(String eventType, Map<String, Object> data) {
        String details = data.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "; " + b).orElse("");

        try (Connection conn = DriverManager.getConnection(SolarDatabaseInitializer.getDatabaseUrl());
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO SIMULATION_LOGS (EVENT, DETAILS) VALUES (?, ?)")) {
            stmt.setString(1, eventType);
            stmt.setString(2, details);
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("❌ DB Log error: " + e.getMessage());
        }
    }

    @Override
    public String getObserverName() {
        return "DbLoggerObserver";
    }
}