package com.illusioncis7.opencore.reputation.command;

import com.illusioncis7.opencore.reputation.ReputationEvent;
import com.illusioncis7.opencore.reputation.ReputationService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;

public class RepInfoCommand implements TabExecutor {
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
            sender.sendMessage("Usage: /repinfo <player> [page]");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }
        UUID uuid = target.getUniqueId();
        int rep = reputationService.getReputation(uuid);
        int page = 1;
        if (args.length >= 2) {
            try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignore) {}
        }

        sender.sendMessage(target.getName() + " reputation: " + rep);
        List<ReputationEvent> hist = reputationService.getHistory(uuid);
        int pages = (hist.size() + 4) / 5;
        page = Math.max(1, Math.min(page, pages));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        int start = (page - 1) * 5;
        int end = Math.min(start + 5, hist.size());
        for (int i = start; i < end; i++) {
            ReputationEvent ev = hist.get(i);
            String time = fmt.format(ev.timestamp);
            sender.sendMessage(time + " - " + ev.reasonSummary + " (" + (ev.change >= 0 ? "+" : "") + ev.change + ")");
        }
        sender.sendMessage("Page " + page + "/" + pages);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names;
        } else if (args.length == 2) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                List<ReputationEvent> hist = reputationService.getHistory(target.getUniqueId());
                int pages = (hist.size() + 4) / 5;
                List<String> result = new ArrayList<>();
                for (int i = 1; i <= pages; i++) {
                    result.add(String.valueOf(i));
                }
                return result;
            }
        }
        return Collections.emptyList();
    }
}
