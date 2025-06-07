package com.illusioncis7.opencore;

import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.logging.ChatLogger;
import com.illusioncis7.opencore.gpt.GptService;
import org.bukkit.plugin.java.JavaPlugin;

public class OpenCore extends JavaPlugin {

    private static OpenCore instance;
    private Database database;
    private GptService gptService;

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

        gptService = new GptService(this, database);
        gptService.init();

        getServer().getPluginManager().registerEvents(new ChatLogger(database, getLogger()), this);
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.disconnect();
        }
        if (gptService != null) {
            gptService.shutdown();
        }
    }

    public Database getDatabase() {
        return database;
    }

    public GptService getGptService() {
        return gptService;
    }
}
