package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.voting.VotingService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SuggestCommand implements CommandExecutor {
    private final VotingService votingService;

    public SuggestCommand(VotingService votingService) {
        this.votingService = votingService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /suggest <text>");
            return true;
        }
        String text = String.join(" ", args);
        votingService.submitSuggestion(((Player) sender).getUniqueId(), text);
        sender.sendMessage("Suggestion submitted.");
        return true;
    }
}
