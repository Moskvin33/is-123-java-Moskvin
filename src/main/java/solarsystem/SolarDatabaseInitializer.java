package solarsystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class SolarDatabaseInitializer {
    private static final String DB_NAME = "solar_system.db";
    private static String DB_PATH;
    private static String DB_URL;

    static {
        String userHome = System.getProperty("user.home");
        String separator = System.getProperty("file.separator");
        DB_PATH = userHome + separator + "SolarSystem" + separator + "database" + separator;
        DB_URL = "jdbc:sqlite:" + DB_PATH + DB_NAME;
    }

    private static final String CREATE_TABLES_SQL = """
        CREATE TABLE IF NOT EXISTS SOLAR_COMMANDS (
            ID INTEGER PRIMARY KEY AUTOINCREMENT,
            ACTION TEXT,
            NAME TEXT,
            RADIUS REAL,
            MASS REAL,
            ORBIT_RADIUS REAL,
            COLOR_HEX TEXT,
            CREATED_AT DATETIME DEFAULT CURRENT_TIMESTAMP
        );
        CREATE TABLE IF NOT EXISTS SIMULATION_LOGS (
            ID INTEGER PRIMARY KEY AUTOINCREMENT,
            EVENT TEXT,
            DETAILS TEXT,
            TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP
        );
        """;

    public static void ensureDatabaseExists() {
        Path dbDir = Paths.get(DB_PATH);
        try {
            if (!Files.exists(dbDir)) {
                Files.createDirectories(dbDir);
            }

            boolean dbExists = Files.exists(Paths.get(DB_PATH + DB_NAME));
            if (!dbExists) {
                System.out.println(" Создание новой БД SQLite для Солнечной системы...");
                try (Connection conn = DriverManager.getConnection(DB_URL);
                     Statement stmt = conn.createStatement()) {

                    stmt.execute("PRAGMA journal_mode=WAL;");
                    stmt.execute("PRAGMA foreign_keys = ON;");

                    for (String sql : CREATE_TABLES_SQL.split(";")) {
                        String trimmed = sql.trim();
                        if (!trimmed.isEmpty()) stmt.execute(trimmed);
                    }
                    System.out.println(" БД успешно создана: " + DB_PATH + DB_NAME);
                }
            } else {
                System.out.println("ℹ БД уже существует: " + DB_PATH + DB_NAME);
            }
        } catch (Exception e) {
            System.err.println(" Ошибка инициализации БД: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getDatabaseUrl() {
        return DB_URL;
    }
}