package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.OpenCore;
import com.illusioncis7.opencore.voting.VotingService;
import com.illusioncis7.opencore.voting.Suggestion;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;

public class VoteCommand implements TabExecutor {
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
            Player player = (Player) sender;
            if (votingService.hasPlayerVoted(player.getUniqueId(), id)) {
                sender.sendMessage("Du hast bereits abgestimmt.");
                return true;
            }
            boolean success = votingService.castVote(player.getUniqueId(), id, yes);
            if (!success) {
                sender.sendMessage("Unknown or closed suggestion.");
                OpenCore.getInstance().getLogger().warning("Invalid vote from " + sender.getName() + " for " + id);
                return true;
            }
            if (votingService.isSuggestionOpen(id)) {
                com.illusioncis7.opencore.voting.VotingService.VoteWeights w = votingService.getVoteWeights(id);
                int remaining = Math.max(0, w.requiredWeight - w.yesWeight);
                if (remaining > 0) {
                    sender.sendMessage("Noch " + remaining + " Stimmen nötig.");
                } else {
                    sender.sendMessage("Quorum erreicht – Voting endet");
                }
            } else {
                sender.sendMessage("Voting concluded.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("Invalid id.");
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            java.util.List<String> ids = new ArrayList<>();
            for (Suggestion s : votingService.getOpenSuggestions()) {
                ids.add(String.valueOf(s.id));
            }
            return ids;
        } else if (args.length == 2) {
            java.util.List<String> opts = new ArrayList<>();
            opts.add("yes");
            opts.add("no");
            return opts;
        }
        return Collections.emptyList();
    }
}
