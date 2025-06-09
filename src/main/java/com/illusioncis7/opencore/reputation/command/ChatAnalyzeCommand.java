package com.illusioncis7.opencore.reputation.command;

import com.illusioncis7.opencore.OpenCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.List;

/** Command to trigger an immediate chat analysis. */
public class ChatAnalyzeCommand implements TabExecutor {
    private final OpenCore plugin;

    public ChatAnalyzeCommand(OpenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("opencore.command.chatanalyze")) {
            plugin.getMessageService().send(sender, "no_permission", null);
            return true;
        }
        if (!plugin.isChatAnalyzerEnabled()) {
            plugin.getMessageService().send(sender, "module_disabled", null);
            return true;
        }
        plugin.runChatAnalysisNow();
        plugin.getMessageService().send(sender, "chatanalyze.started", null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
