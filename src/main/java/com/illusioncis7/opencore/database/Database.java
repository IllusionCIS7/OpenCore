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
                    "min_value INT," +
                    "max_value INT," +
                    "recommended_range VARCHAR(255)," +
                    "editable BOOLEAN DEFAULT 0," +
                    "impact_category VARCHAR(50)," +
                    "impact_rating INT DEFAULT 5," +
                    "description TEXT," +
                    "value_type VARCHAR(20) DEFAULT 'STRING'," +
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

            String flagSql = "CREATE TABLE IF NOT EXISTS chat_reputation_flags (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "code VARCHAR(50) NOT NULL," +
                    "description TEXT," +
                    "min_change INT NOT NULL," +
                    "max_change INT NOT NULL," +
                    "active BOOLEAN DEFAULT 1," +
                    "last_updated TIMESTAMP NOT NULL" +
                    ")";
            stmt.executeUpdate(flagSql);

            String suggestionSql = "CREATE TABLE IF NOT EXISTS suggestions (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "parameter_id INT," +
                    "new_value VARCHAR(255)," +
                    "text TEXT," +
                    "created TIMESTAMP NOT NULL," +
                    "open BOOLEAN DEFAULT 1," +
                    "suggestion_type ENUM('CONFIG_CHANGE','RULE_CHANGE','MODERATION_REQUEST','FEATURE_REQUEST','BUG_REPORT','EVENT_PROPOSAL','OTHER')," +
                    "gpt_reasoning TEXT," +
                    "gpt_confidence FLOAT," +
                    "classified_at TIMESTAMP" +
                    ")";
            stmt.executeUpdate(suggestionSql);

            String voteSql = "CREATE TABLE IF NOT EXISTS votes (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "suggestion_id INT NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "vote_yes BOOLEAN NOT NULL," +
                    "weight INT NOT NULL," +
                    "UNIQUE KEY suggestion_player (suggestion_id, player_uuid)" +
                    ")";
            stmt.executeUpdate(voteSql);

            String promptSql = "CREATE TABLE IF NOT EXISTS gpt_prompts (" +
                    "category VARCHAR(50) PRIMARY KEY," +
                    "prompt TEXT NOT NULL" +
                    ")";
            stmt.executeUpdate(promptSql);

            String responseSql = "CREATE TABLE IF NOT EXISTS gpt_responses (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "module VARCHAR(50)," +
                    "response TEXT NOT NULL," +
                    "created TIMESTAMP NOT NULL," +
                    "delivered BOOLEAN DEFAULT 0" +
                    ")";
            stmt.executeUpdate(responseSql);

            String rulesSql = "CREATE TABLE IF NOT EXISTS server_rules (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "rule_text TEXT NOT NULL," +
                    "category VARCHAR(50)" +
                    ")";
            stmt.executeUpdate(rulesSql);

            String ruleLogSql = "CREATE TABLE IF NOT EXISTS rule_changes (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "rule_id INT NOT NULL," +
                    "old_text TEXT NOT NULL," +
                    "new_text TEXT NOT NULL," +
                    "changed_at TIMESTAMP NOT NULL," +
                    "changed_by VARCHAR(36)," +
                    "suggestion_id INT" +
                    ")";
            stmt.executeUpdate(ruleLogSql);

            String cfgHistSql = "CREATE TABLE IF NOT EXISTS config_change_history (" +
                    "change_id VARCHAR(36) PRIMARY KEY," +
                    "player_uuid VARCHAR(36)," +
                    "param_key VARCHAR(255) NOT NULL," +
                    "old_value TEXT," +
                    "new_value TEXT," +
                    "changed_at TIMESTAMP NOT NULL" +
                    ")";
            stmt.executeUpdate(cfgHistSql);

            String analysisSql = "CREATE TABLE IF NOT EXISTS chat_analysis_log (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "timestamp TIMESTAMP NOT NULL," +
                    "chatlog TEXT NOT NULL," +
                    "json TEXT NOT NULL," +
                    "betroffene_spieler TEXT" +
                    ")";
            stmt.executeUpdate(analysisSql);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public String getPrompt(String category) {
        if (connection == null) return null;
        String sql = "SELECT prompt FROM gpt_prompts WHERE category = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load GPT prompt: " + e.getMessage());
        }
        return null;
    }

    /**
     * Measure database ping time in milliseconds.
     */
    public long ping() {
        if (connection == null) return -1;
        long start = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1")) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB ping failed: " + e.getMessage());
            return -1;
        }
        return System.currentTimeMillis() - start;
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
