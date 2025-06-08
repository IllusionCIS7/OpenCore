package com.illusioncis7.opencore.rules.command;

import com.illusioncis7.opencore.rules.RuleService;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import com.illusioncis7.opencore.OpenCore;
import java.util.HashMap;
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
        if (!sender.hasPermission("opencore.command.editrule")) {
            OpenCore.getInstance().getMessageService().send(sender, "no_permission", null);
            return true;
        }
        if (args.length < 2) {
            OpenCore.getInstance().getMessageService().send(sender, "editrule.usage", null);
            return true;
        }
        int id;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            OpenCore.getInstance().getMessageService().send(sender, "editrule.invalid_id", null);
            return true;
        }
        String newText = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        UUID changer = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        if (ruleService.updateRule(id, newText, changer, null)) {
            OpenCore.getInstance().getMessageService().send(sender, "editrule.updated", null);
        } else {
            OpenCore.getInstance().getMessageService().send(sender, "editrule.failed", null);
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
