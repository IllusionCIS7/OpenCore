package com.illusioncis7.opencore.reputation.command;

import com.illusioncis7.opencore.reputation.ReputationEvent;
import com.illusioncis7.opencore.reputation.ReputationService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class RepInfoCommand implements CommandExecutor {
    private final ReputationService reputationService;

    public RepInfoCommand(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("Admins only.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("Usage: /repinfo <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }
        UUID uuid = target.getUniqueId();
        int rep = reputationService.getReputation(uuid);
        sender.sendMessage(target.getName() + " reputation: " + rep);
        List<ReputationEvent> hist = reputationService.getHistory(uuid);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (ReputationEvent ev : hist) {
            String time = fmt.format(ev.timestamp);
            sender.sendMessage(time + " - " + ev.reasonSummary + " (" + (ev.change >= 0 ? "+" : "") + ev.change + ")");
        }
        return true;
    }
}
