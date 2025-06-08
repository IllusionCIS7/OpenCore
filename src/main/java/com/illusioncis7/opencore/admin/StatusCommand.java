package com.illusioncis7.opencore.admin;

import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.gpt.GptQueueManager;
import com.illusioncis7.opencore.gpt.GptService;
import com.illusioncis7.opencore.voting.VotingService;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import com.illusioncis7.opencore.OpenCore;
import java.util.HashMap;

import java.util.Collections;

public class StatusCommand implements TabExecutor {
    private final GptQueueManager queueManager;
    private final VotingService votingService;
    private final Database database;
    private final GptService gptService;

    public StatusCommand(GptQueueManager queueManager, VotingService votingService,
                         Database database, GptService gptService) {
        this.queueManager = queueManager;
        this.votingService = votingService;
        this.database = database;
        this.gptService = gptService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int queue = queueManager.getQueueSize();
        int open = votingService.getOpenSuggestions().size();
        long ping = database.ping();
        long last = gptService.getLastResponseDuration();
        java.util.Map<String,String> ph = new HashMap<>();
        ph.put("queue", String.valueOf(queue));
        OpenCore.getInstance().getMessageService().send(sender, "status.queue", ph);
        ph = new HashMap<>();
        ph.put("open", String.valueOf(open));
        OpenCore.getInstance().getMessageService().send(sender, "status.open", ph);
        ph = new HashMap<>();
        ph.put("ping", ping >= 0 ? ping + " ms" : "n/a");
        OpenCore.getInstance().getMessageService().send(sender, "status.ping", ph);
        ph = new HashMap<>();
        ph.put("last", last >= 0 ? last + " ms" : "n/a");
        OpenCore.getInstance().getMessageService().send(sender, "status.last", ph);
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
