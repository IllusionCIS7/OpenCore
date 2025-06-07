package com.illusioncis7.opencore.reputation.command;

import com.illusioncis7.opencore.reputation.ChatReputationFlagService;
import com.illusioncis7.opencore.reputation.ReputationFlag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.List;

/** Command to list or update chat reputation flags. */
public class ChatFlagsCommand implements TabExecutor {
    private final ChatReputationFlagService service;

    public ChatFlagsCommand(ChatReputationFlagService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            List<ReputationFlag> list = service.listFlags();
            if (list.isEmpty()) {
                sender.sendMessage("No flags defined.");
                return true;
            }
            for (ReputationFlag f : list) {
                String act = f.active ? "active" : "inactive";
                sender.sendMessage(f.code + ": " + f.minChange + ".." + f.maxChange + " (" + act + ") - " + f.description);
            }
            return true;
        }
        if ("set".equalsIgnoreCase(args[0])) {
            if (!sender.isOp()) {
                sender.sendMessage("Admins only.");
                return true;
            }
            if (args.length < 4) {
                sender.sendMessage("Usage: /chatflags set <CODE> <min> <max>");
                return true;
            }
            String code = args[1];
            try {
                int min = Integer.parseInt(args[2]);
                int max = Integer.parseInt(args[3]);
                if (service.setRange(code, min, max)) {
                    sender.sendMessage("Flag updated.");
                } else {
                    sender.sendMessage("Update failed.");
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("Invalid numbers.");
            }
            return true;
        }
        sender.sendMessage("Usage: /chatflags list | set <CODE> <min> <max>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
