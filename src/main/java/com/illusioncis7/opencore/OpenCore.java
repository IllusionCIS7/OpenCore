package com.illusioncis7.opencore;

import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.logging.ChatLogger;
import com.illusioncis7.opencore.gpt.GptService;
import com.illusioncis7.opencore.gpt.GptQueueManager;
import com.illusioncis7.opencore.gpt.GptResponseHandler;
import com.illusioncis7.opencore.gpt.PolicyService;
import com.illusioncis7.opencore.config.ConfigService;
import com.illusioncis7.opencore.reputation.ReputationService;
import com.illusioncis7.opencore.reputation.PlayerJoinListener;
import com.illusioncis7.opencore.reputation.ChatReputationFlagService;
import com.illusioncis7.opencore.voting.VotingService;
import com.illusioncis7.opencore.voting.command.SuggestCommand;
import com.illusioncis7.opencore.voting.command.SuggestionsCommand;
import com.illusioncis7.opencore.voting.command.VoteCommand;
import com.illusioncis7.opencore.voting.command.VoteStatusCommand;
import com.illusioncis7.opencore.rules.RuleService;
import com.illusioncis7.opencore.rules.command.RulesCommand;
import com.illusioncis7.opencore.rules.command.EditRuleCommand;
import com.illusioncis7.opencore.config.command.RollbackConfigCommand;
import com.illusioncis7.opencore.config.command.ConfigListCommand;
import com.illusioncis7.opencore.admin.StatusCommand;
import com.illusioncis7.opencore.plan.PlanHook;
import com.illusioncis7.opencore.message.MessageService;
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
    private PolicyService policyService;
    private ReputationService reputationService;
    private ChatReputationFlagService chatFlagService;
    private VotingService votingService;
    private PlanHook planHook;
    private MessageService messageService;
    private com.illusioncis7.opencore.api.ApiServer apiServer;
    private com.illusioncis7.opencore.setup.SetupManager setupManager;

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
        saveResource("voting.yml", false);
        saveResource("api.yml", false);
        saveResource("messages.yml", false);
        saveResource("webpanel/index.html", false);

        database = new Database(this);
        database.connect();

        messageService = new MessageService(this);

        reputationService = new ReputationService(this, database);
        chatFlagService = new ChatReputationFlagService(this, database);

        configService = new ConfigService(this, database);
        configService.scanAndStore(new File(".").getAbsoluteFile());

        ruleService = new RuleService(this, database);

        policyService = new PolicyService(this, database);

        setupManager = new com.illusioncis7.opencore.setup.SetupManager(this, ruleService, configService);
        if (setupManager.isSetupActive()) {
            policyService.ensureDefaults();
        }

        gptService = new GptService(this, database, policyService);
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

        SuggestCommand suggestCmd = new SuggestCommand(votingService);
        Objects.requireNonNull(getCommand("suggest")).setExecutor(suggestCmd);
        getCommand("suggest").setTabCompleter(suggestCmd);

        SuggestionsCommand listCmd = new SuggestionsCommand(votingService);
        Objects.requireNonNull(getCommand("suggestions")).setExecutor(listCmd);
        getCommand("suggestions").setTabCompleter(listCmd);

        VoteCommand voteCmd = new VoteCommand(votingService);
        Objects.requireNonNull(getCommand("vote")).setExecutor(voteCmd);
        getCommand("vote").setTabCompleter(voteCmd);

        RulesCommand rulesCmd = new RulesCommand(ruleService);
        Objects.requireNonNull(getCommand("rules")).setExecutor(rulesCmd);
        getCommand("rules").setTabCompleter(rulesCmd);

        EditRuleCommand editRuleCmd = new EditRuleCommand(ruleService);
        Objects.requireNonNull(getCommand("editrule")).setExecutor(editRuleCmd);
        getCommand("editrule").setTabCompleter(editRuleCmd);

        com.illusioncis7.opencore.rules.command.RuleHistoryCommand ruleHistCmd = new com.illusioncis7.opencore.rules.command.RuleHistoryCommand(ruleService);
        Objects.requireNonNull(getCommand("rulehistory")).setExecutor(ruleHistCmd);
        getCommand("rulehistory").setTabCompleter(ruleHistCmd);

        RollbackConfigCommand rollCmd = new RollbackConfigCommand(configService);
        Objects.requireNonNull(getCommand("rollbackconfig")).setExecutor(rollCmd);
        getCommand("rollbackconfig").setTabCompleter(rollCmd);

        com.illusioncis7.opencore.reputation.command.MyRepCommand myRepCmd = new com.illusioncis7.opencore.reputation.command.MyRepCommand(reputationService);
        Objects.requireNonNull(getCommand("myrep")).setExecutor(myRepCmd);
        getCommand("myrep").setTabCompleter(myRepCmd);

        com.illusioncis7.opencore.gpt.command.GptLogCommand gptLogCmd = new com.illusioncis7.opencore.gpt.command.GptLogCommand(gptResponseHandler);
        Objects.requireNonNull(getCommand("gptlog")).setExecutor(gptLogCmd);
        getCommand("gptlog").setTabCompleter(gptLogCmd);

        com.illusioncis7.opencore.reputation.command.RepInfoCommand repInfoCmd = new com.illusioncis7.opencore.reputation.command.RepInfoCommand(reputationService);
        Objects.requireNonNull(getCommand("repinfo")).setExecutor(repInfoCmd);
        getCommand("repinfo").setTabCompleter(repInfoCmd);

        com.illusioncis7.opencore.reputation.command.RepChangeCommand repChangeCmd = new com.illusioncis7.opencore.reputation.command.RepChangeCommand(reputationService);
        Objects.requireNonNull(getCommand("repchange")).setExecutor(repChangeCmd);
        getCommand("repchange").setTabCompleter(repChangeCmd);

        com.illusioncis7.opencore.reputation.command.ChatFlagsCommand chatFlagsCmd = new com.illusioncis7.opencore.reputation.command.ChatFlagsCommand(chatFlagService);
        Objects.requireNonNull(getCommand("chatflags")).setExecutor(chatFlagsCmd);
        getCommand("chatflags").setTabCompleter(chatFlagsCmd);

        com.illusioncis7.opencore.admin.StatusCommand statusCmd = new com.illusioncis7.opencore.admin.StatusCommand(gptQueueManager, votingService, database, gptService);
        Objects.requireNonNull(getCommand("status")).setExecutor(statusCmd);
        getCommand("status").setTabCompleter(statusCmd);

        com.illusioncis7.opencore.config.command.ConfigListCommand cfgListCmd = new com.illusioncis7.opencore.config.command.ConfigListCommand(configService);
        Objects.requireNonNull(getCommand("configlist")).setExecutor(cfgListCmd);
        getCommand("configlist").setTabCompleter(cfgListCmd);

        com.illusioncis7.opencore.voting.command.VoteStatusCommand voteStatusCmd = new com.illusioncis7.opencore.voting.command.VoteStatusCommand(votingService);
        Objects.requireNonNull(getCommand("votestatus")).setExecutor(voteStatusCmd);
        getCommand("votestatus").setTabCompleter(voteStatusCmd);

        new com.illusioncis7.opencore.reputation.ChatAnalyzerTask(database, gptService, reputationService, chatFlagService, ruleService, getLogger())
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

        org.bukkit.configuration.file.FileConfiguration apiCfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new File(getDataFolder(), "api.yml"));
        int port = apiCfg.getInt("port", 0);
        boolean exposeRep = apiCfg.getBoolean("expose-reputations", true);
        try {
            apiServer = new com.illusioncis7.opencore.api.ApiServer(port, exposeRep, votingService,
                    reputationService, chatFlagService, ruleService, configService, setupManager, getLogger());
        } catch (Exception e) {
            getLogger().warning("Failed to start API server: " + e.getMessage());
        }
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
        if (apiServer != null) {
            apiServer.stop();
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

    public ChatReputationFlagService getChatFlagService() {
        return chatFlagService;
    }

    public VotingService getVotingService() {
        return votingService;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public com.illusioncis7.opencore.setup.SetupManager getSetupManager() {
        return setupManager;
    }
}
