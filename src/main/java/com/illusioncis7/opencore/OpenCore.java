package com.illusioncis7.opencore;

import org.bukkit.plugin.java.JavaPlugin;

public class OpenCore extends JavaPlugin {

    private ConfigManager configManager;
    private GPTService gptService;
    private ReputationManager reputationManager;
    private CommandHandler commandHandler;

    @Override
    public void onEnable() {
        getLogger().info("OpenCore wird gestartet...");

        configManager = new ConfigManager(this);
        gptService = new GPTService(this);
        reputationManager = new ReputationManager(this);
        commandHandler = new CommandHandler(this, gptService);
        commandHandler.register();

        getLogger().info("OpenCore wurde aktiviert!");
    }

    @Override
    public void onDisable() {
        if (gptService != null) {
            gptService.shutdown();
        }
        getLogger().info("OpenCore wurde deaktiviert!");
    }
}
