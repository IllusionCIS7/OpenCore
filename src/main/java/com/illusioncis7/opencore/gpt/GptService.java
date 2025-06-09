package com.illusioncis7.opencore.gpt;

import com.illusioncis7.opencore.database.Database;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

public class GptService {

    private final JavaPlugin plugin;
    private final Database database;
    private final PolicyService policyService;
    private final Queue<GptRequest> queue = new ConcurrentLinkedQueue<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = TimeUnit.MINUTES.toMillis(1);

    /** Duration of the last GPT response in milliseconds. */
    private volatile long lastResponseMs = -1;

    private String apiKey;
    private int intervalSeconds;
    private boolean enabled;
    private boolean processing;
    private String model;
    private double temperature;

    private BukkitTask task;

    public GptService(JavaPlugin plugin, Database database, PolicyService policyService) {
        this.plugin = plugin;
        this.database = database;
        this.policyService = policyService;
    }

    public synchronized void init() {
        File configFile = new File(plugin.getDataFolder(), "gpt.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        this.apiKey = config.getString("api-key", "");
        this.intervalSeconds = config.getInt("interval-seconds", 60);
        this.enabled = config.getBoolean("enabled", false);
        this.model = config.getString("model", "gpt-3.5-turbo");
        this.temperature = config.getDouble("temperature", 0.8);

        if (task != null) {
            task.cancel();
            task = null;
        }

        if (enabled) {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    processNext();
                }
            }.runTaskTimerAsynchronously(plugin, 0L, intervalSeconds * 20L);
        } else {
            plugin.getLogger().info("GPT service disabled via configuration.");
        }
    }

    public synchronized void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        queue.clear();
    }

    /** Reload configuration and restart the scheduler. */
    public void reload() {
        init();
    }

    /**
     * Duration of the most recent GPT request in milliseconds or -1 if none.
     */
    public long getLastResponseDuration() {
        return lastResponseMs;
    }

    /**
     * Build a prompt for the given module using the stored policy text.
     * Placeholders of the form %key% are replaced with the provided values.
     */
    public String buildPrompt(String module, Map<String, String> values) {
        String policy = policyService.getOrDefault(module);
        if (policy == null) {
            return null;
        }
        String result = policy;
        if (values != null) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                String ph = "%" + e.getKey() + "%";
                result = result.replace(ph, e.getValue());
            }
        }
        return result;
    }

    /**
     * Convenience to submit a request based on a policy module.
     */
    public void submitPolicyRequest(String module, Map<String, String> values, UUID playerUuid, Consumer<String> callback) {
        String prompt = buildPrompt(module, values);
        if (prompt == null) {
            plugin.getLogger().warning("No policy found for module " + module);
            if (callback != null) callback.accept(null);
            return;
        }
        plugin.getLogger().info("Submitting policy " + module + " for " + (playerUuid != null ? playerUuid : "system"));
        submitRequest(prompt, playerUuid, callback);
    }

    public void submitRequest(String prompt, UUID playerUuid, Consumer<String> callback) {
        if (!enabled) {
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        if (playerUuid != null) {
            Long last = cooldowns.get(playerUuid);
            if (last != null && System.currentTimeMillis() - last < COOLDOWN_MS) {
                plugin.getLogger().warning("GPT cooldown active for " + playerUuid);
                if (callback != null) {
                    callback.accept(null);
                }
                return;
            }
            if (activePlayers.contains(playerUuid)) {
                plugin.getLogger().warning("Player " + playerUuid + " already has an active GPT request");
                if (callback != null) {
                    callback.accept(null);
                }
                return;
            }
            activePlayers.add(playerUuid);
        }

        UUID requestId = UUID.randomUUID();
        GptRequest request = new GptRequest(requestId, prompt, playerUuid, callback);
        queue.add(request);
        if (playerUuid != null) {
            plugin.getLogger().info("Queued GPT request " + requestId + " for " + playerUuid);
        } else {
            plugin.getLogger().info("Queued GPT request " + requestId);
        }
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
        plugin.getLogger().info("Submitting GPT template " + category + " for " + (playerUuid != null ? playerUuid : "system"));
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
        sendRequest(request, 1);
    }

    private void sendRequest(GptRequest request, int attempt) {
        if (request.playerUuid != null) {
            plugin.getLogger().info("Processing GPT request " + request.requestId + " for " + request.playerUuid + " (attempt " + attempt + ")");
        } else {
            plugin.getLogger().info("Processing GPT request " + request.requestId + " (attempt " + attempt + ")");
        }
        long start = System.currentTimeMillis();
        logRequest(request);

        JSONObject payload = new JSONObject();
        payload.put("model", model);
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", request.prompt);
        messages.put(message);
        payload.put("messages", messages);
        payload.put("temperature", temperature);

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
                    boolean success = false;
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
                                success = true;
                            }
                        } catch (Exception e) {
                            plugin.getLogger().severe("Error parsing GPT response: " + e.getMessage());
                        }
                    } else {
                        plugin.getLogger().severe("GPT request returned status " + response.statusCode());
                    }

                    if (!success && attempt < 3) {
                        int delay = (int) Math.pow(2, attempt - 1);
                        plugin.getLogger().warning("Retrying GPT request " + request.requestId + " in " + delay + "s (attempt " + (attempt + 1) + ")");
                        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin,
                                () -> sendRequest(request, attempt + 1), delay * 20L);
                        return;
                    }

                    logResponse(request.requestId, answer);
                    if (success) {
                        plugin.getLogger().info("GPT request " + request.requestId + " answered in " + (System.currentTimeMillis() - start) + "ms");
                    }
                    lastResponseMs = System.currentTimeMillis() - start;
                    if (request.callback != null) {
                        request.callback.accept(answer);
                    }
                    if (request.playerUuid != null) {
                        activePlayers.remove(request.playerUuid);
                        cooldowns.put(request.playerUuid, System.currentTimeMillis());
                    }
                    processing = false;
                });
    }

    private void logRequest(GptRequest request) {
        if (!database.isConnected()) {
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
        if (!database.isConnected()) {
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
