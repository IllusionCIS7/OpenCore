package com.illusioncis7.opencore.voting.command;

import com.illusioncis7.opencore.OpenCore;
import com.illusioncis7.opencore.voting.VotingService;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.time.Instant;
import java.time.Duration;

public class SuggestCommand implements TabExecutor {
    private final VotingService votingService;
    private final Map<java.util.UUID, Instant> lastSubmission = new HashMap<>();

    public SuggestCommand(VotingService votingService) {
        this.votingService = votingService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player player = (Player) sender;
        java.util.UUID uuid = player.getUniqueId();
        Instant now = Instant.now();
        Instant last = lastSubmission.get(uuid);
        if (last != null && Duration.between(last, now).compareTo(Duration.ofMinutes(5)) < 0) {
            sender.sendMessage("You can submit only one suggestion every 5 minutes.");
            OpenCore.getInstance().getLogger().info("Rate limit hit for " + sender.getName());
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
        lastSubmission.put(uuid, now);
        OpenCore.getInstance().getLogger().info("Suggestion from " + sender.getName() + " at " + now + ": " + text);
        int id = votingService.submitSuggestion(uuid, text);
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
