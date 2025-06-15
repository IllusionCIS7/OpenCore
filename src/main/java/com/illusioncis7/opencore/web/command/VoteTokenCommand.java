package com.illusioncis7.opencore.web.command;

import com.illusioncis7.opencore.OpenCore;
import com.illusioncis7.opencore.web.WebTokenService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VoteTokenCommand implements TabExecutor {
    private final WebTokenService tokenService;

    public VoteTokenCommand(WebTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("opencore.command.vote")) {
            OpenCore.getInstance().getMessageService().send(sender, "no_permission", null);
            return true;
        }
        if (!(sender instanceof Player)) {
            OpenCore.getInstance().getMessageService().send(sender, "vote.players_only", null);
            return true;
        }
        UUID uuid = ((Player) sender).getUniqueId();
        String token = tokenService.issueToken(uuid, "vote");
        if (token == null) {
            OpenCore.getInstance().getMessageService().send(sender, "vote.unknown", null);
            return true;
        }
        String url = tokenService.getPublicUrl() + "/vote?token=" + token;
        Map<String,String> ph = new HashMap<>();
        ph.put("url", url);
        OpenCore.getInstance().getMessageService().send(sender, "webtoken.vote", ph);
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
