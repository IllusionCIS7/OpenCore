package com.illusioncis7.opencore.rules.command;

import com.illusioncis7.opencore.rules.RuleService;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Command for editing rules directly. */
public class EditRuleCommand implements TabExecutor {
    private final RuleService ruleService;

    public EditRuleCommand(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /editrule <id> <new text>");
            return true;
        }
        int id;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Invalid id.");
            return true;
        }
        String newText = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        UUID changer = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        if (ruleService.updateRule(id, newText, changer, null)) {
            sender.sendMessage("Rule updated.");
        } else {
            sender.sendMessage("Failed to update rule.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<com.illusioncis7.opencore.rules.Rule> rules = ruleService.getRules();
            List<String> ids = new java.util.ArrayList<>();
            for (com.illusioncis7.opencore.rules.Rule r : rules) {
                ids.add(String.valueOf(r.id));
            }
            return ids;
        }
        return Collections.emptyList();
    }
}
