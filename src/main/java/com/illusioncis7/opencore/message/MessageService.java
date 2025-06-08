package com.illusioncis7.opencore.message;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads chat messages from messages.yml and applies color codes and placeholders.
 */
public class MessageService {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    private List<String> loadRaw(String key) {
        Object val = config.get(key);
        List<String> lines = new ArrayList<>();
        if (val instanceof Iterable) {
            for (Object o : (Iterable<?>) val) {
                lines.add(String.valueOf(o));
            }
        } else if (val != null) {
            lines.add(String.valueOf(val));
        }
        return lines;
    }

    public List<String> getMessage(String key, Map<String, String> placeholders) {
        List<String> lines = loadRaw(key);
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (placeholders != null) {
                for (Map.Entry<String, String> e : placeholders.entrySet()) {
                    line = line.replace("{" + e.getKey() + "}", e.getValue());
                    line = line.replace("%" + e.getKey() + "%", e.getValue());
                }
            }
            result.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return result;
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        for (String line : getMessage(key, placeholders)) {
            sender.sendMessage(line);
        }
    }
}
