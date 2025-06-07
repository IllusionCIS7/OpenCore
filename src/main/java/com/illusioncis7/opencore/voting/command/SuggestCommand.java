package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.OpenCore;
import com.illusioncis7.opencore.voting.VotingService;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;

public class SuggestCommand implements TabExecutor {
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
        String text = String.join(" ", args).trim();
        if (text.length() < 5) {
            sender.sendMessage("Suggestion too short.");
            OpenCore.getInstance().getLogger().warning("Rejected short suggestion from " + sender.getName());
            return true;
        }
        if (votingService.isSimilarSuggestionRecent(text, java.time.Duration.ofHours(24), 10)) {
            sender.sendMessage("Ähnlicher Vorschlag existiert bereits.");
            return true;
        }
        int id = votingService.submitSuggestion(((Player) sender).getUniqueId(), text);
        if (id == -1) {
            sender.sendMessage("Failed to submit suggestion.");
        } else {
            sender.sendMessage("Suggestion submitted with ID " + id + ".");
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
