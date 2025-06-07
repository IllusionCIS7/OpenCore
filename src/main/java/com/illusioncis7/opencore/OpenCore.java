package com.illusioncis7.opencore;

import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.logging.ChatLogger;
import com.illusioncis7.opencore.gpt.GptService;
import com.illusioncis7.opencore.config.ConfigService;
import com.illusioncis7.opencore.reputation.ReputationService;
import com.illusioncis7.opencore.reputation.PlayerJoinListener;
import com.illusioncis7.opencore.voting.VotingService;
import com.illusioncis7.opencore.voting.command.SuggestCommand;
import com.illusioncis7.opencore.voting.command.SuggestionsCommand;
import com.illusioncis7.opencore.voting.command.VoteCommand;
import com.illusioncis7.opencore.rules.RuleService;
import com.illusioncis7.opencore.rules.command.RulesCommand;
import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

public class OpenCore extends JavaPlugin {

    private static OpenCore instance;
    private Database database;
    private GptService gptService;
    private ConfigService configService;
    private RuleService ruleService;
    private ReputationService reputationService;
    private VotingService votingService;

    public static OpenCore getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("gpt.yml", false);
        saveResource("database.yml", false);
        saveResource("config-scan.yml", false);

        database = new Database(this);
        database.connect();

        reputationService = new ReputationService(this, database);

        configService = new ConfigService(this, database);
        configService.scanAndStore(new File(".").getAbsoluteFile());

        ruleService = new RuleService(this, database);

        gptService = new GptService(this, database);
        gptService.init();

        votingService = new VotingService(this, database, gptService, configService, ruleService, reputationService);
        getCommand("suggest").setExecutor(new SuggestCommand(votingService));
        getCommand("suggestions").setExecutor(new SuggestionsCommand(votingService));
        getCommand("vote").setExecutor(new VoteCommand(votingService));
        getCommand("rules").setExecutor(new RulesCommand(ruleService));

        new com.illusioncis7.opencore.reputation.ChatAnalyzerTask(database, gptService, reputationService, getLogger())
                .runTaskTimerAsynchronously(this, 0L, 30 * 60 * 20L);

        getServer().getPluginManager().registerEvents(new ChatLogger(database, getLogger()), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(reputationService, getLogger()), this);
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

    public ConfigService getConfigService() {
        return configService;
    }

    public RuleService getRuleService() {
        return ruleService;
    }

    public ReputationService getReputationService() {
        return reputationService;
    }

    public VotingService getVotingService() {
        return votingService;
    }
}
