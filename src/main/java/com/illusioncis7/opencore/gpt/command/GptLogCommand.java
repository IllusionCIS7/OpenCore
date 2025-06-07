package com.illusioncis7.opencore.gpt.command;

import com.illusioncis7.opencore.gpt.GptResponseHandler;
import com.illusioncis7.opencore.gpt.GptResponseRecord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class GptLogCommand implements CommandExecutor {
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
}
