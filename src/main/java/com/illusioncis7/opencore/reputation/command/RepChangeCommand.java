package com.illusioncis7.opencore.reputation.command;

import com.illusioncis7.opencore.reputation.ReputationEvent;
import com.illusioncis7.opencore.reputation.ReputationService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import com.illusioncis7.opencore.OpenCore;
import java.util.HashMap;

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
            OpenCore.getInstance().getMessageService().send(sender, "reputation.admin_only", null);
            return true;
        }
        if (args.length < 1) {
            OpenCore.getInstance().getMessageService().send(sender, "reputation.repchange_usage", null);
            return true;
        }
        try {
            UUID id = UUID.fromString(args[0]);
            ReputationEvent ev = reputationService.getEvent(id);
            if (ev == null) {
                OpenCore.getInstance().getMessageService().send(sender, "reputation.change_not_found", null);
                return true;
            }
            OfflinePlayer p = Bukkit.getOfflinePlayer(ev.playerUuid);
            String name = p != null ? p.getName() : ev.playerUuid.toString();
            java.util.Map<String,String> ph = new HashMap<>();
            ph.put("player", name);
            OpenCore.getInstance().getMessageService().send(sender, "reputation.change_player", ph);
            ph = new HashMap<>();
            ph.put("value", (ev.change >= 0 ? "+" : "") + String.valueOf(ev.change));
            OpenCore.getInstance().getMessageService().send(sender, "reputation.change_value", ph);
            ph = new HashMap<>();
            ph.put("reason", ev.reasonSummary);
            OpenCore.getInstance().getMessageService().send(sender, "reputation.change_reason", ph);
            ph = new HashMap<>();
            ph.put("module", ev.sourceModule);
            OpenCore.getInstance().getMessageService().send(sender, "reputation.change_module", ph);
        } catch (IllegalArgumentException e) {
            OpenCore.getInstance().getMessageService().send(sender, "reputation.invalid_id", null);
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
