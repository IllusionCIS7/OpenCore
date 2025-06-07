package com.illusioncis7.opencore.rules.command;

import com.illusioncis7.opencore.rules.Rule;
import com.illusioncis7.opencore.rules.RuleService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class RulesCommand implements CommandExecutor {
    private final RuleService ruleService;

    public RulesCommand(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<Rule> rules = ruleService.getRules();
        if (rules.isEmpty()) {
            sender.sendMessage("No rules defined.");
            return true;
        }
        for (Rule r : rules) {
            sender.sendMessage("#" + r.id + ": " + r.text);
        }
        return true;
    }
}
