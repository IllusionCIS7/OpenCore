package com.illusioncis7.opencore.reputation.command;

import com.illusioncis7.opencore.reputation.ReputationEvent;
import com.illusioncis7.opencore.reputation.ReputationService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class MyRepCommand implements CommandExecutor {
    private final ReputationService reputationService;

    public MyRepCommand(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        int rep = reputationService.getReputation(uuid);
        sender.sendMessage("Your reputation: " + rep);
        List<ReputationEvent> hist = reputationService.getHistory(uuid);
        int start = Math.max(0, hist.size() - 3);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (int i = hist.size() - 1; i >= start; i--) {
            ReputationEvent ev = hist.get(i);
            String time = fmt.format(ev.timestamp);
            sender.sendMessage(time + " - " + ev.reasonSummary + " (" + (ev.change >= 0 ? "+" : "") + ev.change + ")");
        }
        return true;
    }
}
