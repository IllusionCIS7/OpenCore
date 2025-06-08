package com.illusioncis7.opencore.config.command;

import com.illusioncis7.opencore.config.ConfigService;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import com.illusioncis7.opencore.OpenCore;

import java.util.UUID;

/**
 * Command to rollback a configuration change by id.
 */
import java.util.Collections;

public class RollbackConfigCommand implements TabExecutor {

    private final ConfigService configService;

    public RollbackConfigCommand(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            OpenCore.getInstance().getMessageService().send(sender, "rollbackconfig.usage", null);
            return true;
        }
        try {
            UUID id = UUID.fromString(args[0]);
            if (configService.rollbackChange(id)) {
                OpenCore.getInstance().getMessageService().send(sender, "rollbackconfig.success", null);
            } else {
                OpenCore.getInstance().getMessageService().send(sender, "rollbackconfig.failed", null);
            }
        } catch (IllegalArgumentException e) {
            OpenCore.getInstance().getMessageService().send(sender, "rollbackconfig.invalid_id", null);
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
