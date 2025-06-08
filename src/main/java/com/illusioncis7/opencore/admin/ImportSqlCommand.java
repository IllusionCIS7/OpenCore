package com.illusioncis7.opencore.admin;

import com.illusioncis7.opencore.OpenCore;
import com.illusioncis7.opencore.database.Database;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Imports SQL statements from a file into the active database.
 * The file must be located inside the plugin folder.
 */
public class ImportSqlCommand implements TabExecutor {

    private final Database database;

    public ImportSqlCommand(Database database) {
        this.database = database;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("opencore.command.importsql")) {
            OpenCore.getInstance().getMessageService().send(sender, "no_permission", null);
            return true;
        }
        if (args.length != 1) {
            OpenCore.getInstance().getMessageService().send(sender, "importsql.usage", null);
            return true;
        }

        File file = new File(OpenCore.getInstance().getDataFolder(), args[0]);
        boolean result = database.executeSqlFile(file);
        if (result) {
            OpenCore.getInstance().getMessageService().send(sender, "importsql.success", null);
        } else {
            OpenCore.getInstance().getMessageService().send(sender, "importsql.failed", null);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}

