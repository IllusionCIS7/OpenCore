package com.illusioncis7.opencore.rules.command;

import com.illusioncis7.opencore.rules.Rule;
import com.illusioncis7.opencore.rules.RuleService;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class RulesCommand implements TabExecutor {
    private final RuleService ruleService;

    public RulesCommand(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("id")) {
            try {
                int id = Integer.parseInt(args[1]);
                Rule r = ruleService.getRule(id);
                if (r == null) {
                    sender.sendMessage("Rule not found.");
                } else {
                    sender.sendMessage("#" + r.id + " [" + r.category + "]: " + r.text);
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("Invalid id.");
            }
            return true;
        }

        int page = 1;
        if (args.length >= 1) {
            try { page = Integer.parseInt(args[0]); } catch (NumberFormatException ignore) {}
        }

        List<Rule> rules = ruleService.getRules();
        if (rules.isEmpty()) {
            sender.sendMessage("No rules defined.");
            return true;
        }
        int pages = (rules.size() + 4) / 5;
        page = Math.max(1, Math.min(page, pages));
        int start = (page - 1) * 5;
        int end = Math.min(start + 5, rules.size());
        for (int i = start; i < end; i++) {
            Rule r = rules.get(i);
            sender.sendMessage("#" + r.id + " [" + r.category + "]: " + r.text);
        }
        sender.sendMessage("Page " + page + "/" + pages);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<Rule> rules = ruleService.getRules();
        if (args.length == 1) {
            int pages = (rules.size() + 4) / 5;
            List<String> result = new ArrayList<>();
            result.add("id");
            for (int i = 1; i <= pages; i++) {
                result.add(String.valueOf(i));
            }
            return result;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("id")) {
            List<String> ids = new ArrayList<>();
            for (Rule r : rules) {
                ids.add(String.valueOf(r.id));
            }
            return ids;
        }
        return Collections.emptyList();
    }
}
