package com.illusioncis7.opencore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandHandler implements CommandExecutor {
    private final OpenCore plugin;
    private final GPTService gptService;

    public CommandHandler(OpenCore plugin, GPTService gptService) {
        this.plugin = plugin;
        this.gptService = gptService;
        plugin.getLogger().info("CommandHandler initialisiert.");
    }

    public void register() {
        plugin.getCommand("gpttest").setExecutor(this);
        plugin.getCommand("opencorereload").setExecutor(this);
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
