package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.voting.Suggestion;
import com.illusioncis7.opencore.voting.VotingService;
import com.illusioncis7.opencore.voting.VotingService.VoteWeights;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Collections;

public class VoteStatusCommand implements TabExecutor {
    private final VotingService votingService;

    public VoteStatusCommand(VotingService votingService) {
        this.votingService = votingService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<Suggestion> list = votingService.getClosedSuggestions();
        if (list.isEmpty()) {
            sender.sendMessage("Keine abgeschlossenen Vorschläge.");
            return true;
        }
        for (Suggestion s : list) {
            VoteWeights w = votingService.getVoteWeights(s.id);
            boolean accepted = w.yesWeight > w.noWeight && w.yesWeight >= w.requiredWeight;
            String title = (s.description != null && !s.description.isEmpty()) ? s.description : s.text;
            String result = accepted ? "angenommen" : "abgelehnt";
            String change = s.newValue != null ? s.newValue : "";
            sender.sendMessage("#" + s.id + " - " + title + " -> " + result + (accepted && !change.isEmpty() ? ": " + change : ""));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
