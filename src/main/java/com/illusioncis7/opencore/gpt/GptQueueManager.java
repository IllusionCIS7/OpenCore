package com.illusioncis7.opencore.gpt;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles GPT requests with a fixed rate to avoid API flooding.
 */
public class GptQueueManager {

    private final JavaPlugin plugin;
    private final GptService gptService;
    private final GptResponseHandler responseHandler;
    private final Queue<GptRequest> queue = new ConcurrentLinkedQueue<>();
    private BukkitTask task;
    private final Logger logger;
    private final int maxQueueSize;

    /**
     * Get current queue size.
     */
    public int getQueueSize() {
        return queue.size();
    }

    public GptQueueManager(JavaPlugin plugin, GptService gptService, GptResponseHandler responseHandler) {
        this(plugin, gptService, responseHandler, 100);
    }

    public GptQueueManager(JavaPlugin plugin, GptService gptService, GptResponseHandler responseHandler, int maxQueueSize) {
        this.plugin = plugin;
        this.gptService = gptService;
        this.responseHandler = responseHandler;
        this.logger = plugin.getLogger();
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * Start processing queued requests every 10 minutes.
     */
    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processNext, 0L, 10 * 60 * 20L);
    }

    /**
     * Stop processing and clear the queue.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        queue.clear();
    }

    /**
     * Queue a new GPT request.
     */
    public void submit(String module, String prompt, UUID player) {
        if (queue.size() >= maxQueueSize) {
            logger.log(Level.WARNING, "GPT queue full (" + queue.size() + "/" + maxQueueSize + ") – rejecting request from " + module);
            return;
        }
        GptRequest req = new GptRequest(UUID.randomUUID(), module, prompt, player, null);
        queue.add(req);
        logger.info("Queued GPT request " + req.requestId + " from " + module + " (queue=" + queue.size() + ")");
    }

    private void processNext() {
        logger.fine("GPT queue size: " + queue.size());
        GptRequest req = queue.poll();
        if (req == null) {
            return;
        }
        logger.info("Processing queued GPT request " + req.requestId);
        gptService.submitRequest(req.prompt, req.playerUuid, response -> {
            if (responseHandler != null) {
                responseHandler.handleResponse(req, response);
            }
        });
    }
}
