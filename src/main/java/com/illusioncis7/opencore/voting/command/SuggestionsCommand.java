package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.voting.Suggestion;
import com.illusioncis7.opencore.voting.VotingService;
import com.illusioncis7.opencore.voting.VotingService.VoteWeights;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import com.illusioncis7.opencore.OpenCore;
import org.bukkit.ChatColor;
import java.util.HashMap;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class SuggestionsCommand implements TabExecutor {
    private final VotingService votingService;

    public SuggestionsCommand(VotingService votingService) {
        this.votingService = votingService;
    }

    private static final int PAGE_SIZE = 5;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int page = 1;
        if (args.length > 0) {
            try { page = Integer.parseInt(args[0]); } catch (NumberFormatException ignore) {}
        }
        List<Suggestion> list = votingService.getOpenSuggestions();
        if (list.isEmpty()) {
            OpenCore.getInstance().getMessageService().send(sender, "suggestions.none", null);
            return true;
        }
        int pages = (list.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.max(1, Math.min(page, pages));
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, list.size());
        for (int i = start; i < end; i++) {
            Suggestion s = list.get(i);
            VoteWeights w = votingService.getVoteWeights(s.id);
            int remaining = (int) Math.max(0, w.requiredWeight - w.yesWeight);
            String title = (s.description != null && !s.description.isEmpty()) ? s.description : s.text;
            String progress = String.format("%.1f/%.1f yes", w.yesWeight, w.requiredWeight);
            if (remaining > 0) {
                java.util.Map<String,String> ph = new HashMap<>();
                ph.put("id", String.valueOf(s.id));
                ph.put("title", title);
                ph.put("progress", progress);
                ph.put("remaining", String.valueOf(remaining));
                OpenCore.getInstance().getMessageService().send(sender, "suggestions.entry_need", ph);
            } else {
                java.util.Map<String,String> ph = new HashMap<>();
                ph.put("id", String.valueOf(s.id));
                ph.put("title", title);
                ph.put("progress", progress);
                OpenCore.getInstance().getMessageService().send(sender, "suggestions.entry_quorum", ph);
            }
            sender.sendMessage(votingService.buildVoteBar(w.yesWeight, w.noWeight));
            long mins = votingService.getRemainingMinutes(s.created);
            sender.sendMessage(org.bukkit.ChatColor.GRAY + "Noch " + mins + " Minuten");
        }
        java.util.Map<String,String> ph = new HashMap<>();
        ph.put("page", String.valueOf(page));
        ph.put("pages", String.valueOf(pages));
        OpenCore.getInstance().getMessageService().send(sender, "suggestions.page", ph);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<Suggestion> list = votingService.getOpenSuggestions();
            int pages = (list.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            List<String> result = new ArrayList<>();
            for (int i = 1; i <= pages; i++) {
                result.add(String.valueOf(i));
            }
            return result;
        }
        return Collections.emptyList();
    }
}
