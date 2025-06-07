package com.illusioncis7.opencore.gpt;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

public class GptService {

    private final JavaPlugin plugin;
    private final Database database;
    private final Queue<GptRequest> queue = new ConcurrentLinkedQueue<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String apiKey;
    private int intervalSeconds;
    private boolean enabled;
    private boolean processing;

    public GptService(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void init() {
        File configFile = new File(plugin.getDataFolder(), "gpt.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        this.apiKey = config.getString("api-key", "");
        this.intervalSeconds = config.getInt("interval-seconds", 60);
        this.enabled = config.getBoolean("enabled", false);

        if (enabled) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    processNext();
                }
            }.runTaskTimerAsynchronously(plugin, 0L, intervalSeconds * 20L);
        } else {
            plugin.getLogger().info("GPT service disabled via configuration.");
        }
    }

    public void shutdown() {
        queue.clear();
    }

    public void submitRequest(String prompt, UUID playerUuid, Consumer<String> callback) {
        if (!enabled) {
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        UUID requestId = UUID.randomUUID();
        GptRequest request = new GptRequest(requestId, prompt, playerUuid, callback);
        queue.add(request);
        plugin.getLogger().info("Queued GPT request " + requestId);
    }

    public void submitTemplate(String category, String data, UUID playerUuid, Consumer<String> callback) {
        String template = database.getPrompt(category);
        if (template == null) {
            plugin.getLogger().warning("No GPT prompt found for category " + category);
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }
        String prompt = template.replace("{data}", data);
        submitRequest(prompt, playerUuid, callback);
    }

    private void processNext() {
        if (processing) {
            return;
        }
        GptRequest request = queue.poll();
        if (request == null) {
            return;
        }
        processing = true;
        sendRequest(request);
    }

    private void sendRequest(GptRequest request) {
        plugin.getLogger().info("Processing GPT request " + request.requestId);
        logRequest(request);

        JSONObject payload = new JSONObject();
        payload.put("model", "gpt-3.5-turbo");
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", request.prompt);
        messages.put(message);
        payload.put("messages", messages);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((response, throwable) -> {
                    String answer = null;
                    if (throwable != null) {
                        plugin.getLogger().severe("GPT request " + request.requestId + " failed: " + throwable.getMessage());
                    } else if (response.statusCode() == 200) {
                        try {
                            JSONObject json = new JSONObject(response.body());
                            JSONArray choices = json.getJSONArray("choices");
                            if (!choices.isEmpty()) {
                                JSONObject first = choices.getJSONObject(0);
                                JSONObject msg = first.getJSONObject("message");
                                answer = msg.getString("content");
                            }
                        } catch (Exception e) {
                            plugin.getLogger().severe("Error parsing GPT response: " + e.getMessage());
                        }
                    } else {
                        plugin.getLogger().severe("GPT request returned status " + response.statusCode());
                    }

                    logResponse(request.requestId, answer);
                    if (request.callback != null) {
                        request.callback.accept(answer);
                    }
                    processing = false;
                });
    }

    private void logRequest(GptRequest request) {
        if (database.getConnection() == null) {
            return;
        }
        String sql = "INSERT INTO gpt_log (request_uuid, player_uuid, request_time, prompt) VALUES (?, ?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, request.requestId.toString());
            if (request.playerUuid != null) {
                ps.setString(2, request.playerUuid.toString());
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setString(4, request.prompt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log GPT request: " + e.getMessage());
        }
    }

    private void logResponse(UUID requestId, String response) {
        if (database.getConnection() == null) {
            return;
        }
        String sql = "UPDATE gpt_log SET response = ?, response_time = ? WHERE request_uuid = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (response != null) {
                ps.setString(1, response);
            } else {
                ps.setNull(1, Types.LONGVARCHAR);
            }
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setString(3, requestId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log GPT response: " + e.getMessage());
        }
    }
}
