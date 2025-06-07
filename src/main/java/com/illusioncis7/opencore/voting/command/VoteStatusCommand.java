package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.voting.Suggestion;
import com.illusioncis7.opencore.voting.VotingService;
import com.illusioncis7.opencore.voting.VotingService.VoteWeights;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class VoteStatusCommand implements CommandExecutor {
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
}
