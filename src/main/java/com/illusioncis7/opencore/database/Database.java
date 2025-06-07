package com.illusioncis7.opencore.database;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;

public class Database {

    private final JavaPlugin plugin;
    private Connection connection;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }
            File configFile = new File(plugin.getDataFolder(), "database.yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String host = config.getString("host", "localhost");
            int port = config.getInt("port", 3306);
            String database = config.getString("database", "minecraft");
            String username = config.getString("username", "root");
            String password = config.getString("password", "");

            String url = "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=false";
            connection = DriverManager.getConnection(url, username, password);

            setupTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not connect to the database: " + e.getMessage());
        }
    }

    private void setupTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            String chatSql = "CREATE TABLE IF NOT EXISTS chat_log (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "message_time TIMESTAMP NOT NULL," +
                    "message TEXT NOT NULL" +
                    ")";
            stmt.executeUpdate(chatSql);

            String gptSql = "CREATE TABLE IF NOT EXISTS gpt_log (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "request_uuid VARCHAR(36) NOT NULL," +
                    "player_uuid VARCHAR(36)," +
                    "request_time TIMESTAMP NOT NULL," +
                    "prompt TEXT NOT NULL," +
                    "response TEXT," +
                    "response_time TIMESTAMP" +
                    ")";
            stmt.executeUpdate(gptSql);

            String cfgSql = "CREATE TABLE IF NOT EXISTS config_params (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "path VARCHAR(255) NOT NULL," +
                    "parameter_path VARCHAR(255) NOT NULL," +
                    "min_value VARCHAR(255)," +
                    "max_value VARCHAR(255)," +
                    "recommended_range VARCHAR(255)," +
                    "editable BOOLEAN DEFAULT 0," +
                    "impact_category VARCHAR(50)," +
                    "impact_rating INT DEFAULT 5," +
                    "UNIQUE KEY path_param (path, parameter_path)" +
                    ")";
            stmt.executeUpdate(cfgSql);

            String playerSql = "CREATE TABLE IF NOT EXISTS player_registry (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "alias_id VARCHAR(36) NOT NULL," +
                    "reputation_score INT DEFAULT 0," +
                    "reputation_rank VARCHAR(50)" +
                    ")";
            stmt.executeUpdate(playerSql);

            String eventSql = "CREATE TABLE IF NOT EXISTS reputation_events (" +
                    "id VARCHAR(36) PRIMARY KEY," +
                    "timestamp TIMESTAMP NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "change INT NOT NULL," +
                    "reason_summary VARCHAR(255)," +
                    "source_module VARCHAR(50)," +
                    "details TEXT" +
                    ")";
            stmt.executeUpdate(eventSql);

            String guideSql = "CREATE TABLE IF NOT EXISTS reputation_guidelines (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "module VARCHAR(50) NOT NULL," +
                    "rule VARCHAR(255) NOT NULL," +
                    "points INT NOT NULL," +
                    "description TEXT" +
                    ")";
            stmt.executeUpdate(guideSql);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
            }
        }
    }
}
