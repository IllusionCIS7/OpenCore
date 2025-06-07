package com.illusioncis7.opencore.config.command;

import com.illusioncis7.opencore.config.ConfigParameter;
import com.illusioncis7.opencore.config.ConfigService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class ConfigListCommand implements CommandExecutor {
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
}
