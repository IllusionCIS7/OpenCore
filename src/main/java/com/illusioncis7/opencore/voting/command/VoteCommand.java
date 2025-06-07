package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.voting.VotingService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VoteCommand implements CommandExecutor {
    private final VotingService votingService;

    public VoteCommand(VotingService votingService) {
        this.votingService = votingService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /vote <id> <yes|no>");
            return true;
        }
        try {
            int id = Integer.parseInt(args[0]);
            boolean yes = args[1].equalsIgnoreCase("yes") || args[1].equalsIgnoreCase("y");
            votingService.castVote(((Player) sender).getUniqueId(), id, yes);
            sender.sendMessage("Vote recorded.");
        } catch (NumberFormatException e) {
            sender.sendMessage("Invalid id.");
        }
        return true;
    }
}
