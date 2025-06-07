package com.illusioncis7.opencore.gpt;

import com.illusioncis7.opencore.OpenCore;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

public class GPTService {
    private final OpenCore plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    public GPTService(OpenCore plugin) {
        this.plugin = plugin;
        executor.submit(this::processQueue);
        plugin.getLogger().info("GPTService initialisiert.");
    }

    private void processQueue() {
        while (!executor.isShutdown()) {
            try {
                String prompt = queue.take();
                plugin.getLogger().info("Verarbeite GPT-Prompt: " + prompt);
                // TODO: GPT-Anbindung
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void submitRequest(String prompt) {
        queue.offer(prompt);
        plugin.getLogger().info("Prompt eingereiht: " + prompt);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
