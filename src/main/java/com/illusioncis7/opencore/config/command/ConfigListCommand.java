package com.illusioncis7.opencore.config.command;

import com.illusioncis7.opencore.config.ConfigParameter;
import com.illusioncis7.opencore.config.ConfigService;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;
import com.illusioncis7.opencore.OpenCore;
import java.util.HashMap;

import java.util.List;

import java.util.Collections;

public class ConfigListCommand implements TabExecutor {
    private final ConfigService configService;

    public ConfigListCommand(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("opencore.command.configlist")) {
            OpenCore.getInstance().getMessageService().send(sender, "no_permission", null);
            return true;
        }
        List<ConfigParameter> list = configService.listParameters();
        if (list.isEmpty()) {
            OpenCore.getInstance().getMessageService().send(sender, "configlist.none", null);
            return true;
        }
        for (ConfigParameter p : list) {
            String value = p.getCurrentValue() != null ? p.getCurrentValue() : "null";
            java.util.Map<String, String> ph = new HashMap<>();
            ph.put("id", String.valueOf(p.getId()));
            ph.put("param", p.getYamlPath());
            ph.put("value", value);
            ph.put("editable", String.valueOf(p.isEditableByPlayers()));
            OpenCore.getInstance().getMessageService().send(sender, "configlist.entry", ph);
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
