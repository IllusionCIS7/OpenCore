package com.illusioncis7.opencore.gpt.command;

import com.illusioncis7.opencore.gpt.GptResponseHandler;
import com.illusioncis7.opencore.gpt.GptResponseRecord;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        UUID uuid = ((Player) sender).getUniqueId();
        List<GptResponseRecord> list = handler.getRecentResponses(uuid, 5);
        if (list.isEmpty()) {
            sender.sendMessage("No GPT responses found.");
            return true;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (GptResponseRecord rec : list) {
            String time = fmt.format(rec.timestamp);
            String prefix = rec.module != null ? "[" + rec.module + "] " : "";
            sender.sendMessage(time + " " + prefix + rec.response);
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
