package de.freibausmp.opencore.gpt;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Zentrales Gateway für GPT-Anfragen.
 * Hält eine Warteschlange und verarbeitet alle X Sekunden einen Request.
 */
public class GptService {

    private final JavaPlugin plugin;
    private final BlockingQueue<QueuedRequest> queue = new LinkedBlockingQueue<>();

    private String apiKey;
    private int intervalSeconds;

    /**
     * Repräsentiert eine eingereihte Anfrage.
     */
    private record QueuedRequest(String prompt, Consumer<String> callback, UUID playerId) {
    }

    public GptService(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startWorker();
        plugin.getLogger().info("GptService initialisiert.");
    }

    /**
     * Reicht einen Prompt zur Verarbeitung ein.
     * Die Rückgabe erfolgt asynchron über den Callback.
     */
    public void submitPrompt(String prompt, Consumer<String> callback) {
        submitPrompt(prompt, callback, null);
    }

    /**
     * Variante mit Spieler-UUID für Logging.
     */
    public void submitPrompt(String prompt, Consumer<String> callback, UUID playerId) {
        queue.offer(new QueuedRequest(prompt, callback, playerId));
        plugin.getLogger().info("Prompt eingereiht: " + prompt);
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "gpt.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            plugin.saveResource("gpt.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        apiKey = cfg.getString("api-key", "");
        intervalSeconds = cfg.getInt("interval-seconds", 60);
        plugin.getLogger().info("GPT-Konfiguration geladen. Intervall: " + intervalSeconds + "s");
    }

    /**
     * Startet einen asynchronen Worker, der die Queue periodisch abarbeitet.
     */
    private void startWorker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                QueuedRequest req = queue.poll();
                if (req == null) {
                    return; // nichts zu tun
                }
                plugin.getLogger().info("Sende Prompt an GPT: " + req.prompt);
                long start = System.currentTimeMillis();
                String response = sendPrompt(req.prompt);
                long duration = System.currentTimeMillis() - start;

                if (response == null) {
                    response = "Fehler beim Abrufen der Antwort";
                }

                String logMsg = "Antwort erhalten (" + duration + "ms)" +
                        (req.playerId != null ? " Spieler: " + req.playerId : "");
                plugin.getLogger().info(logMsg);
                plugin.getLogger().info(response);

                final String finalResponse = response;
                Bukkit.getScheduler().runTask(plugin, () -> req.callback.accept(finalResponse));
            }
        }.runTaskTimerAsynchronously(plugin, 0L, intervalSeconds * 20L);
    }

    /**
     * Führt den eigentlichen HTTP-Request an die GPT-API aus.
     */
    private String sendPrompt(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            plugin.getLogger().warning("Kein API-Key für GPT gesetzt.");
            return null;
        }

        try {
            JSONObject message = new JSONObject()
                    .put("role", "user")
                    .put("content", prompt);
            JSONArray messages = new JSONArray().put(message);

            JSONObject body = new JSONObject()
                    .put("model", "gpt-3.5-turbo")
                    .put("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("GPT-Request fehlgeschlagen: " + response.statusCode());
                return null;
            }

            JSONObject json = new JSONObject(response.body());
            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim GPT-Request: " + e.getMessage());
            return null;
        }
    }
}
