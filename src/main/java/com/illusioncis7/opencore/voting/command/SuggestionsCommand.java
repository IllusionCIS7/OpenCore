package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.voting.Suggestion;
import com.illusioncis7.opencore.voting.VotingService;
import com.illusioncis7.opencore.voting.VotingService.VoteWeights;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;

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
            sender.sendMessage("No open suggestions.");
            return true;
        }
        int pages = (list.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.max(1, Math.min(page, pages));
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, list.size());
        for (int i = start; i < end; i++) {
            Suggestion s = list.get(i);
            VoteWeights w = votingService.getVoteWeights(s.id);
            int remaining = Math.max(0, w.requiredWeight - w.yesWeight);
            String title = (s.description != null && !s.description.isEmpty()) ? s.description : s.text;
            String progress = w.yesWeight + "/" + w.requiredWeight + " yes";
            if (remaining > 0) {
                sender.sendMessage("#" + s.id + " - " + title + " [" + progress + "] " + remaining + " votes needed");
            } else {
                sender.sendMessage("#" + s.id + " - " + title + " [" + progress + "] quorum reached");
            }
        }
        sender.sendMessage("Page " + page + "/" + pages);
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
