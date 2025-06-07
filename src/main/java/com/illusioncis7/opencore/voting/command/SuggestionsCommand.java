package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.voting.Suggestion;
import com.illusioncis7.opencore.voting.VotingService;
import com.illusioncis7.opencore.voting.VotingService.VoteWeights;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class SuggestionsCommand implements CommandExecutor {
    private final VotingService votingService;

    public SuggestionsCommand(VotingService votingService) {
        this.votingService = votingService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<Suggestion> list = votingService.getOpenSuggestions();
        if (list.isEmpty()) {
            sender.sendMessage("No open suggestions.");
            return true;
        }
        for (Suggestion s : list) {
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
        return true;
    }
}
