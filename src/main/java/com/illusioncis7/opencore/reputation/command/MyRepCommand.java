package com.illusioncis7.opencore.reputation.command;

import com.illusioncis7.opencore.reputation.ReputationEvent;
import com.illusioncis7.opencore.reputation.ReputationService;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import com.illusioncis7.opencore.OpenCore;
import java.util.HashMap;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import java.util.Collections;

public class MyRepCommand implements TabExecutor {
    private final ReputationService reputationService;

    public MyRepCommand(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            OpenCore.getInstance().getMessageService().send(sender, "reputation.players_only", null);
            return true;
        }
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        int rep = reputationService.getReputation(uuid);
        java.util.Map<String,String> ph = new HashMap<>();
        ph.put("rep", String.valueOf(rep));
        OpenCore.getInstance().getMessageService().send(sender, "reputation.your_rep", ph);
        List<ReputationEvent> hist = reputationService.getHistory(uuid);
        int start = Math.max(0, hist.size() - 3);
        DateTimeFormatter fmt = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault());
        for (int i = hist.size() - 1; i >= start; i--) {
            ReputationEvent ev = hist.get(i);
            String time = fmt.format(ev.timestamp);
            java.util.Map<String,String> line = new HashMap<>();
            line.put("time", time);
            line.put("reason", ev.reasonSummary);
            line.put("module", ev.sourceModule);
            line.put("change", (ev.change >= 0 ? "+" : "") + String.valueOf(ev.change));
            OpenCore.getInstance().getMessageService().send(sender, "reputation.event", line);
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
