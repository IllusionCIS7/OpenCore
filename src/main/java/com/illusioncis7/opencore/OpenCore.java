package com.illusioncis7.opencore;

import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.logging.ChatLogger;
import org.bukkit.plugin.java.JavaPlugin;

public class OpenCore extends JavaPlugin {

    private static OpenCore instance;
    private Database database;

    public static OpenCore getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("gpt.yml", false);
        saveResource("database.yml", false);

        database = new Database(this);
        database.connect();

        getServer().getPluginManager().registerEvents(new ChatLogger(database, getLogger()), this);
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.disconnect();
        }
    }

    public Database getDatabase() {
        return database;
    }
}
