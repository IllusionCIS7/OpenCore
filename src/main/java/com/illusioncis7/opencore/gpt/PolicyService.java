package com.illusioncis7.opencore.gpt;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Central storage and retrieval of GPT policies.
 */
public class PolicyService {
    private final JavaPlugin plugin;
    private final Database database;
    private final Map<String, String> defaults = new HashMap<>();

    public PolicyService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        loadDefaults();
        initTable();
    }

    private void initTable() {
        if (database.getConnection() == null) return;
        String sql = "CREATE TABLE IF NOT EXISTS gpt_policies (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(50) NOT NULL," +
                "policy_text TEXT NOT NULL," +
                "version INT NOT NULL," +
                "active BOOLEAN DEFAULT 1," +
                "last_updated TIMESTAMP NOT NULL" +
                ")";
        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create gpt_policies: " + e.getMessage());
        }
    }

    private void loadDefaults() {
        defaults.put("suggest_classify", String.join("\n",
                "Ziel:",
                "Bewerte, ob ein Spieler-Vorschlag sinnvoll, redundant, unausgereift oder schädlich ist – unter Berücksichtigung der aktuellen Serverregeln und -ziele.",
                "",
                "Kontext:",
                "* Der Vorschlag stammt von einem Spieler (%s).",
                "* Dies sind die aktuell gültigen Serverregeln: %rules%",
                "* Bitte achte auf Originalität, Fairness, technische Umsetzbarkeit und Serverbalance.",
                "",
                "Anweisung:",
                "Antworte ausschließlich im folgenden JSON-Format (keine Kommentare, kein zusätzlicher Text):",
                "",
                "json:{",
                "\"type\": \"rule\" | \"config\" | \"other\",",
                "\"quality\": \"high\" | \"medium\" | \"low\",",
                "\"redundant\": true | false,",
                "\"impact\": 1-5,",
                "\"reason\": \"kurze sachliche Begründung\"",
                "}"
        ));

        defaults.put("chat_analysis", String.join("\n",
                "Ziel:",
                "Analysiere einen Ingame-Chatverlauf (%message%) und bewerte das Verhalten des Spielers im Kontext der Serveretikette.",
                "",
                "Kontext:",
                "* Regeln für Verhalten lauten unter anderem: %rules%",
                "* Es geht um Respekt, Konstruktivität und Communityförderung.",
                "",
                "Anweisung:",
                "Antworte ausschließlich im folgenden JSON-Format:",
                "",
                "json:{",
                "\"tone\": \"friendly\" | \"neutral\" | \"toxic\",",
                "\"intention\": \"supportive\" | \"ironic\" | \"provocative\" | \"harmful\",",
                "\"violation\": true | false,",
                "\"confidence\": 1-5,",
                "\"reason\": \"kurze Einschätzung\"",
                "}"
        ));

        defaults.put("rule_map", String.join("\n",
                "Ziel:",
                "Strukturiere einen neuen Regeltext so, dass er eindeutig, kategorisiert und vollständig ist.",
                "",
                "Kontext:",
                "* Vorschlagstext: %s",
                "* Aktuelle Regeln: %rules%",
                "* Die Regel muss einer von 3 Hauptkategorien zugewiesen werden: \"Verhalten\", \"Technik\", \"PvP\"",
                "",
                "Anweisung:",
                "Antworte ausschließlich im folgenden JSON-Format:",
                "",
                "json:{",
                "\"category\": \"Verhalten\" | \"Technik\" | \"PvP\",",
                "\"rule_text\": \"fertig formulierter Regeltext\",",
                "\"conflict_with_existing\": true | false,",
                "\"reason\": \"optional, falls Konflikt erkannt\"",
                "}"
        ));
    }

    /** Retrieve the active policy text for the given module. */
    public String getPolicy(String name) {
        if (database.getConnection() == null) return null;
        String sql = "SELECT policy_text FROM gpt_policies WHERE name = ? AND active = 1 ORDER BY version DESC LIMIT 1";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load policy for " + name + ": " + e.getMessage());
        }
        return defaults.get(name);
    }

    /** Insert a new version of a policy. */
    public void setPolicy(String name, String text) {
        if (database.getConnection() == null) return;
        try (Connection conn = database.getConnection()) {
            int version = 1;
            try (PreparedStatement ps = conn.prepareStatement("SELECT MAX(version) FROM gpt_policies WHERE name = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        version = rs.getInt(1) + 1;
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE gpt_policies SET active = 0 WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO gpt_policies (name, policy_text, version, active, last_updated) VALUES (?,?,?,?,?)")) {
                ps.setString(1, name);
                ps.setString(2, text);
                ps.setInt(3, version);
                ps.setBoolean(4, true);
                ps.setTimestamp(5, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to store policy " + name + ": " + e.getMessage());
        }
    }

    /** List all policy names. */
    public List<String> listPolicies() {
        List<String> list = new ArrayList<>();
        if (database.getConnection() == null) return list;
        String sql = "SELECT DISTINCT name FROM gpt_policies";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString(1));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to list policies: " + e.getMessage());
        }
        if (list.isEmpty()) {
            list.addAll(defaults.keySet());
        }
        return list;
    }

    /** Check whether a policy with the given name exists. */
    public boolean isDefined(String name) {
        if (database.getConnection() == null) return defaults.containsKey(name);
        String sql = "SELECT COUNT(*) FROM gpt_policies WHERE name = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to check policy: " + e.getMessage());
        }
        return defaults.containsKey(name);
    }

    /** Get the active policy or the built-in default. */
    public String getOrDefault(String name) {
        String p = getPolicy(name);
        return p != null ? p : defaults.get(name);
    }

    /** Ensure all default policies exist in the database. */
    public void ensureDefaults() {
        for (Map.Entry<String, String> e : defaults.entrySet()) {
            if (!isDefined(e.getKey())) {
                setPolicy(e.getKey(), e.getValue());
            }
        }
    }
}
