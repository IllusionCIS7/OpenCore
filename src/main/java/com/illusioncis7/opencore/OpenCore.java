package com.illusioncis7.opencore;

import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.logging.ChatLogger;
import com.illusioncis7.opencore.gpt.GptService;
import com.illusioncis7.opencore.gpt.GptQueueManager;
import com.illusioncis7.opencore.gpt.GptResponseHandler;
import com.illusioncis7.opencore.config.ConfigService;
import com.illusioncis7.opencore.reputation.ReputationService;
import com.illusioncis7.opencore.reputation.PlayerJoinListener;
import com.illusioncis7.opencore.voting.VotingService;
import com.illusioncis7.opencore.voting.command.SuggestCommand;
import com.illusioncis7.opencore.voting.command.SuggestionsCommand;
import com.illusioncis7.opencore.voting.command.VoteCommand;
import com.illusioncis7.opencore.voting.command.VoteStatusCommand;
import com.illusioncis7.opencore.rules.RuleService;
import com.illusioncis7.opencore.rules.command.RulesCommand;
import com.illusioncis7.opencore.config.command.RollbackConfigCommand;
import com.illusioncis7.opencore.config.command.ConfigListCommand;
import com.illusioncis7.opencore.admin.StatusCommand;
import com.illusioncis7.opencore.plan.PlanHook;
import java.io.File;
import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class OpenCore extends JavaPlugin {

    private static OpenCore instance;
    private Database database;
    private GptService gptService;
    private GptQueueManager gptQueueManager;
    private GptResponseHandler gptResponseHandler;
    private ConfigService configService;
    private RuleService ruleService;
    private ReputationService reputationService;
    private VotingService votingService;
    private PlanHook planHook;

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
        saveResource("reputation.yml", false);

        database = new Database(this);
        database.connect();

        reputationService = new ReputationService(this, database);

        configService = new ConfigService(this, database);
        configService.scanAndStore(new File(".").getAbsoluteFile());

        ruleService = new RuleService(this, database);

        gptService = new GptService(this, database);
        gptService.init();
        gptResponseHandler = new GptResponseHandler(this, database);
        gptQueueManager = new GptQueueManager(this, gptService, gptResponseHandler);
        gptQueueManager.start();

        planHook = new PlanHook();
        if (planHook.hook()) {
            getLogger().info("Hooked into Plan API");
        } else {
            getLogger().warning("Plan not found or QUERY_API unavailable");
        }

        votingService = new VotingService(this, database, gptService, configService, ruleService, reputationService, planHook);
        Objects.requireNonNull(getCommand("suggest")).setExecutor(new SuggestCommand(votingService));
        Objects.requireNonNull(getCommand("suggestions")).setExecutor(new SuggestionsCommand(votingService));
        Objects.requireNonNull(getCommand("vote")).setExecutor(new VoteCommand(votingService));
        Objects.requireNonNull(getCommand("rules")).setExecutor(new RulesCommand(ruleService));
        Objects.requireNonNull(getCommand("rollbackconfig")).setExecutor(new RollbackConfigCommand(configService));
        Objects.requireNonNull(getCommand("myrep")).setExecutor(new com.illusioncis7.opencore.reputation.command.MyRepCommand(reputationService));
        Objects.requireNonNull(getCommand("gptlog")).setExecutor(new com.illusioncis7.opencore.gpt.command.GptLogCommand(gptResponseHandler));
        Objects.requireNonNull(getCommand("repinfo")).setExecutor(new com.illusioncis7.opencore.reputation.command.RepInfoCommand(reputationService));
        Objects.requireNonNull(getCommand("repchange")).setExecutor(new com.illusioncis7.opencore.reputation.command.RepChangeCommand(reputationService));
        Objects.requireNonNull(getCommand("status")).setExecutor(new com.illusioncis7.opencore.admin.StatusCommand(gptQueueManager, votingService, database, gptService));
        Objects.requireNonNull(getCommand("configlist")).setExecutor(new com.illusioncis7.opencore.config.command.ConfigListCommand(configService));
        Objects.requireNonNull(getCommand("votestatus")).setExecutor(new com.illusioncis7.opencore.voting.command.VoteStatusCommand(votingService));

        new com.illusioncis7.opencore.reputation.ChatAnalyzerTask(database, gptService, reputationService, getLogger())
                .runTaskTimerAsynchronously(this, 0L, 30 * 60 * 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                votingService.checkOpenSuggestions();
            }
        }.runTaskTimer(this, 0L, 30 * 60 * 20L);

        getServer().getPluginManager().registerEvents(new ChatLogger(database, getLogger()), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(reputationService, getLogger(), planHook), this);
        getServer().getPluginManager().registerEvents(gptResponseHandler, this);
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.disconnect();
        }
        if (gptService != null) {
            gptService.shutdown();
        }
        if (gptQueueManager != null) {
            gptQueueManager.stop();
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
