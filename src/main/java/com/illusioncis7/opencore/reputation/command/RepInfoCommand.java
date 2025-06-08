package com.illusioncis7.opencore.reputation.command;

import com.illusioncis7.opencore.reputation.ReputationEvent;
import com.illusioncis7.opencore.reputation.ReputationService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import com.illusioncis7.opencore.OpenCore;
import java.util.HashMap;
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
        if (!sender.hasPermission("opencore.command.repinfo")) {
            OpenCore.getInstance().getMessageService().send(sender, "no_permission", null);
            return true;
        }
        if (args.length < 1) {
            OpenCore.getInstance().getMessageService().send(sender, "reputation.repinfo_usage", null);
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            OpenCore.getInstance().getMessageService().send(sender, "reputation.player_not_found", null);
            return true;
        }
        UUID uuid = target.getUniqueId();
        int rep = reputationService.getReputation(uuid);
        int page = 1;
        if (args.length >= 2) {
            try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignore) {}
        }

        java.util.Map<String,String> head = new HashMap<>();
        head.put("player", target.getName());
        head.put("rep", String.valueOf(rep));
        OpenCore.getInstance().getMessageService().send(sender, "reputation.player_rep", head);
        List<ReputationEvent> hist = reputationService.getHistory(uuid);
        int pages = (hist.size() + 4) / 5;
        page = Math.max(1, Math.min(page, pages));
        DateTimeFormatter fmt = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault());
        int start = (page - 1) * 5;
        int end = Math.min(start + 5, hist.size());
        for (int i = start; i < end; i++) {
            ReputationEvent ev = hist.get(i);
            String time = fmt.format(ev.timestamp);
            java.util.Map<String,String> ph = new HashMap<>();
            ph.put("time", time);
            ph.put("reason", ev.reasonSummary);
            ph.put("change", (ev.change >= 0 ? "+" : "") + String.valueOf(ev.change));
            OpenCore.getInstance().getMessageService().send(sender, "reputation.event", ph);
        }
        java.util.Map<String,String> pmap = new HashMap<>();
        pmap.put("page", String.valueOf(page));
        pmap.put("pages", String.valueOf(pages));
        OpenCore.getInstance().getMessageService().send(sender, "reputation.page", pmap);
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
