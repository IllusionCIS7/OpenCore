package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.voting.Suggestion;
import com.illusioncis7.opencore.voting.VotingService;
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
            sender.sendMessage("#" + s.id + " -> param " + s.parameterId + " = " + s.newValue + " (" + s.text + ")");
        }
        return true;
    }
}
