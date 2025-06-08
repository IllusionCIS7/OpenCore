package com.illusioncis7.opencore.gpt.command;

import com.illusioncis7.opencore.gpt.GptResponseHandler;
import com.illusioncis7.opencore.gpt.GptResponseRecord;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.illusioncis7.opencore.OpenCore;
import java.util.HashMap;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import java.util.Collections;

public class GptLogCommand implements TabExecutor {
    private final GptResponseHandler handler;

    public GptLogCommand(GptResponseHandler handler) {
        this.handler = handler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("opencore.command.gptlog")) {
            OpenCore.getInstance().getMessageService().send(sender, "no_permission", null);
            return true;
        }
        if (!(sender instanceof Player)) {
            OpenCore.getInstance().getMessageService().send(sender, "gptlog.players_only", null);
            return true;
        }
        UUID uuid = ((Player) sender).getUniqueId();
        List<GptResponseRecord> list = handler.getRecentResponses(uuid, 5);
        if (list.isEmpty()) {
            OpenCore.getInstance().getMessageService().send(sender, "gptlog.none", null);
            return true;
        }
        DateTimeFormatter fmt = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault());
        for (GptResponseRecord rec : list) {
            String time = fmt.format(rec.timestamp);
            java.util.Map<String,String> ph = new HashMap<>();
            ph.put("time", time);
            ph.put("prefix", rec.module != null ? "[" + rec.module + "] " : "");
            ph.put("response", rec.response);
            OpenCore.getInstance().getMessageService().send(sender, "gptlog.entry", ph);
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
