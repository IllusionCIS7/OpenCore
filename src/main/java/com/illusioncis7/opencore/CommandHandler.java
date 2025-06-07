package com.illusioncis7.opencore;

import com.illusioncis7.opencore.gpt.GPTService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Objects;

public class CommandHandler implements CommandExecutor {
    private final OpenCore plugin;
    private final GPTService gptService;

    public CommandHandler(OpenCore plugin, GPTService gptService) {
        this.plugin = plugin;
        this.gptService = gptService;
        plugin.getLogger().info("CommandHandler initialisiert.");
    }

    public void register() {
        Objects.requireNonNull(plugin.getCommand("gpttest")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("opencorereload")).setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("gpttest")) {
            gptService.submitRequest("Testprompt von " + sender.getName());
            sender.sendMessage("GPT-Test gestartet.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("opencorereload")) {
            plugin.reloadConfig();
            sender.sendMessage("OpenCore-Konfiguration neu geladen.");
            return true;
        }
        return false;
    }
}
