package com.illusioncis7.opencore.rules.command;

import com.illusioncis7.opencore.rules.RuleChange;
import com.illusioncis7.opencore.rules.RuleService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import com.illusioncis7.opencore.OpenCore;
import java.util.HashMap;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RuleHistoryCommand implements TabExecutor {
    private final RuleService ruleService;

    public RuleHistoryCommand(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            OpenCore.getInstance().getMessageService().send(sender, "rulehistory.usage", null);
            return true;
        }
        int id;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            OpenCore.getInstance().getMessageService().send(sender, "rulehistory.invalid_id", null);
            return true;
        }
        int page = 1;
        if (args.length >= 2) {
            try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignore) {}
        }
        List<RuleChange> hist = ruleService.getHistory(id);
        if (hist.isEmpty()) {
            OpenCore.getInstance().getMessageService().send(sender, "rulehistory.no_history", null);
            return true;
        }
        int pages = (hist.size() + 4) / 5;
        page = Math.max(1, Math.min(page, pages));
        int start = (page - 1) * 5;
        int end = Math.min(start + 5, hist.size());
        DateTimeFormatter fmt = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault());
        for (int i = start; i < end; i++) {
            RuleChange rc = hist.get(i);
            String time = fmt.format(rc.changedAt);
            String changer = rc.changedBy != null ? Bukkit.getOfflinePlayer(rc.changedBy).getName() : "system";
            java.util.Map<String, String> ph = new HashMap<>();
            ph.put("time", time);
            ph.put("changer", changer);
            ph.put("text", rc.newText);
            OpenCore.getInstance().getMessageService().send(sender, "rulehistory.entry", ph);
        }
        java.util.Map<String, String> ph = new HashMap<>();
        ph.put("page", String.valueOf(page));
        ph.put("pages", String.valueOf(pages));
        OpenCore.getInstance().getMessageService().send(sender, "rulehistory.page", ph);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<com.illusioncis7.opencore.rules.Rule> rules = ruleService.getRules();
            List<String> ids = new ArrayList<>();
            for (com.illusioncis7.opencore.rules.Rule r : rules) {
                ids.add(String.valueOf(r.id));
            }
            return ids;
        } else if (args.length == 2) {
            try {
                int id = Integer.parseInt(args[0]);
                List<RuleChange> hist = ruleService.getHistory(id);
                int pages = (hist.size() + 4) / 5;
                List<String> result = new ArrayList<>();
                for (int i = 1; i <= pages; i++) {
                    result.add(String.valueOf(i));
                }
                return result;
            } catch (NumberFormatException ignore) {
            }
        }
        return Collections.emptyList();
    }
}
