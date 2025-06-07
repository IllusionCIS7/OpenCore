package com.illusioncis7.opencore.reputation.command;

import com.illusioncis7.opencore.reputation.ReputationEvent;
import com.illusioncis7.opencore.reputation.ReputationService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;

import java.util.UUID;
import java.util.Collections;

/**
 * Admin command to inspect a specific reputation change event.
 */
public class RepChangeCommand implements TabExecutor {
    private final ReputationService reputationService;

    public RepChangeCommand(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("Admins only.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("Usage: /repchange <eventId>");
            return true;
        }
        try {
            UUID id = UUID.fromString(args[0]);
            ReputationEvent ev = reputationService.getEvent(id);
            if (ev == null) {
                sender.sendMessage("Change not found.");
                return true;
            }
            OfflinePlayer p = Bukkit.getOfflinePlayer(ev.playerUuid);
            String name = p != null ? p.getName() : ev.playerUuid.toString();
            sender.sendMessage("Player: " + name);
            sender.sendMessage("Value: " + (ev.change >= 0 ? "+" : "") + ev.change);
            sender.sendMessage("Reason: " + ev.reasonSummary);
            sender.sendMessage("GPT-Kategorie: " + ev.sourceModule);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Invalid ID format.");
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
