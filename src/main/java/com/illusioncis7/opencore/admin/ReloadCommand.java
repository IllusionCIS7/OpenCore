package com.illusioncis7.opencore.admin;

import com.illusioncis7.opencore.OpenCore;
import com.illusioncis7.opencore.reputation.ReputationService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.List;

/**
 * Reloads plugin configuration files.
 */
public class ReloadCommand implements TabExecutor {
    private final OpenCore plugin;

    public ReloadCommand(OpenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("opencore.command.reload")) {
            plugin.getMessageService().send(sender, "no_permission", null);
            return true;
        }
        try {
            plugin.getMessageService().reload();
            ReputationService rep = plugin.getReputationService();
            if (rep != null) {
                rep.reload();
            }
            OpenCore.getInstance().getCoreCommand().loadAliases();
            plugin.reloadModules();
            plugin.reloadPolicies();
            plugin.reloadGptService();
            plugin.reloadChatAnalyzer();
            plugin.getMessageService().send(sender, "reload.success", null);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to reload: " + e.getMessage());
            plugin.getMessageService().send(sender, "reload.failed", null);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
