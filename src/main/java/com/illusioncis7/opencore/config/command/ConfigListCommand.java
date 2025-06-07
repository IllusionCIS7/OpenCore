package com.illusioncis7.opencore.config.command;

import com.illusioncis7.opencore.config.ConfigParameter;
import com.illusioncis7.opencore.config.ConfigService;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

import java.util.Collections;

public class ConfigListCommand implements TabExecutor {
    private final ConfigService configService;

    public ConfigListCommand(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<ConfigParameter> list = configService.listParameters();
        if (list.isEmpty()) {
            sender.sendMessage("No parameters found.");
            return true;
        }
        for (ConfigParameter p : list) {
            String value = p.getCurrentValue() != null ? p.getCurrentValue() : "null";
            sender.sendMessage("#" + p.getId() + " " + p.getParameterPath() + " = " + value +
                    " editable=" + p.isEditable() + " impact=" + p.getImpactRating());
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
