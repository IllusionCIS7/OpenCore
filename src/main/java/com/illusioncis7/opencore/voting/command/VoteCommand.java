package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.OpenCore;
import com.illusioncis7.opencore.voting.VotingService;
import com.illusioncis7.opencore.voting.Suggestion;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.HashMap;

import java.util.ArrayList;
import java.util.Collections;

public class VoteCommand implements TabExecutor {
    private final VotingService votingService;

    public VoteCommand(VotingService votingService) {
        this.votingService = votingService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("opencore.command.vote")) {
            OpenCore.getInstance().getMessageService().send(sender, "no_permission", null);
            return true;
        }
        if (!OpenCore.getInstance().isSuggestionsEnabled()) {
            OpenCore.getInstance().getMessageService().send(sender, "module_disabled", null);
            return true;
        }
        if (!(sender instanceof Player)) {
            OpenCore.getInstance().getMessageService().send(sender, "vote.players_only", null);
            return true;
        }
        if (args.length < 2) {
            OpenCore.getInstance().getMessageService().send(sender, "vote.usage", null);
            return true;
        }
        try {
            int id = Integer.parseInt(args[0]);
            boolean yes = args[1].equalsIgnoreCase("yes") || args[1].equalsIgnoreCase("y");
            Player player = (Player) sender;
            if (votingService.hasPlayerVoted(player.getUniqueId(), id)) {
                OpenCore.getInstance().getMessageService().send(sender, "vote.already", null);
                return true;
            }
            int rep = OpenCore.getInstance().getReputationService().getReputation(player.getUniqueId());
            if (rep < votingService.getMinVoteReputation()) {
                OpenCore.getInstance().getMessageService().send(sender, "vote.low_rep", null);
                return true;
            }
            boolean success = votingService.castVote(player.getUniqueId(), id, yes);
            if (!success) {
                OpenCore.getInstance().getMessageService().send(sender, "vote.unknown", null);
                OpenCore.getInstance().getLogger().warning("Invalid vote from " + sender.getName() + " for " + id);
                return true;
            }
            if (votingService.isSuggestionOpen(id)) {
                com.illusioncis7.opencore.voting.VotingService.VoteWeights w = votingService.getVoteWeights(id);
                int remaining = (int) Math.max(0, w.requiredWeight - w.yesWeight);
                if (remaining > 0) {
                    java.util.Map<String,String> ph = new HashMap<>();
                    ph.put("remaining", String.valueOf(remaining));
                    OpenCore.getInstance().getMessageService().send(sender, "vote.remaining", ph);
                } else {
                    OpenCore.getInstance().getMessageService().send(sender, "vote.quorum", null);
                }
                sender.sendMessage(votingService.buildVoteBar(w.yesWeight, w.noWeight));
            } else {
                OpenCore.getInstance().getMessageService().send(sender, "vote.concluded", null);
            }
        } catch (NumberFormatException e) {
            OpenCore.getInstance().getMessageService().send(sender, "vote.invalid_id", null);
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
