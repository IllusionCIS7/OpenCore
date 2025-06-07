package com.illusioncis7.opencore.config.command;

import com.illusioncis7.opencore.config.ConfigService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.UUID;

/**
 * Command to rollback a configuration change by id.
 */
public class RollbackConfigCommand implements CommandExecutor {

    private final ConfigService configService;

    public RollbackConfigCommand(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("Usage: /rollbackconfig <changeId>");
            return true;
        }
        try {
            UUID id = UUID.fromString(args[0]);
            if (configService.rollbackChange(id)) {
                sender.sendMessage("Rollback executed.");
            } else {
                sender.sendMessage("Rollback failed.");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Invalid id.");
        }
        return true;
    }
}
