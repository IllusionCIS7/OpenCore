package com.illusioncis7.opencore.reputation.command;

import com.illusioncis7.opencore.reputation.ChatReputationFlagService;
import com.illusioncis7.opencore.reputation.ReputationFlag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import com.illusioncis7.opencore.OpenCore;
import java.util.HashMap;

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
        if (!sender.hasPermission("opencore.command.chatflags")) {
            OpenCore.getInstance().getMessageService().send(sender, "no_permission", null);
            return true;
        }
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            List<ReputationFlag> list = service.listFlags();
            if (list.isEmpty()) {
                OpenCore.getInstance().getMessageService().send(sender, "chatflags.none", null);
                return true;
            }
            for (ReputationFlag f : list) {
                String act = f.active ? "active" : "inactive";
                java.util.Map<String,String> ph = new HashMap<>();
                ph.put("code", f.code);
                ph.put("min", String.valueOf(f.minChange));
                ph.put("max", String.valueOf(f.maxChange));
                ph.put("state", act);
                ph.put("desc", f.description);
                OpenCore.getInstance().getMessageService().send(sender, "chatflags.entry", ph);
            }
            return true;
        }
        if ("set".equalsIgnoreCase(args[0])) {
            if (args.length < 4) {
                OpenCore.getInstance().getMessageService().send(sender, "chatflags.usage_set", null);
                return true;
            }
            String code = args[1];
            try {
                int min = Integer.parseInt(args[2]);
                int max = Integer.parseInt(args[3]);
                if (service.setRange(code, min, max)) {
                    OpenCore.getInstance().getMessageService().send(sender, "chatflags.updated", null);
                } else {
                    OpenCore.getInstance().getMessageService().send(sender, "chatflags.update_failed", null);
                }
            } catch (NumberFormatException e) {
                OpenCore.getInstance().getMessageService().send(sender, "chatflags.invalid_numbers", null);
            }
            return true;
        }
        OpenCore.getInstance().getMessageService().send(sender, "chatflags.usage", null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
