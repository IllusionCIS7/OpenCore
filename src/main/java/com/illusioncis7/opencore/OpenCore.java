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
import org.bukkit.scheduler.BukkitTask;

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
    private com.illusioncis7.opencore.command.OpenCoreCommand coreCommand;
    private com.illusioncis7.opencore.api.ApiServer apiServer;
    private com.illusioncis7.opencore.setup.SetupManager setupManager;
    private com.illusioncis7.opencore.reputation.ChatAnalyzerTask chatAnalyzerTask;
    private org.bukkit.scheduler.BukkitTask chatAnalyzerTimer;

    private boolean moduleConfigGrabber = true;
    private boolean moduleSuggestions = true;
    private boolean moduleChatAnalyzer = true;

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
        saveResource("modules.yml", false);

        org.bukkit.configuration.file.FileConfiguration modCfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File(getDataFolder(), "modules.yml"));
        org.bukkit.configuration.ConfigurationSection mods = modCfg.getConfigurationSection("modules");
        if (mods != null) {
            moduleConfigGrabber = mods.getBoolean("config-grabber", true);
            moduleSuggestions = mods.getBoolean("suggestions", true);
            moduleChatAnalyzer = mods.getBoolean("chat-analyzer", true);
        }

        database = new Database(this);
        database.connect();

        messageService = new MessageService(this);

        reputationService = new ReputationService(this, database);
        chatFlagService = new ChatReputationFlagService(this, database);

        configService = new ConfigService(this, database);
        if (moduleConfigGrabber) {
            configService.scanAndStore(new File(".").getAbsoluteFile());
        } else {
            getLogger().info("ConfigGrabber disabled via modules.yml");
        }

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

        SuggestionsCommand listCmd = new SuggestionsCommand(votingService);

        VoteCommand voteCmd = new VoteCommand(votingService);
        if (!moduleSuggestions) {
            getLogger().info("Suggestions module disabled via modules.yml");
        }

        RulesCommand rulesCmd = new RulesCommand(ruleService);

        EditRuleCommand editRuleCmd = new EditRuleCommand(ruleService);

        com.illusioncis7.opencore.rules.command.RuleHistoryCommand ruleHistCmd = new com.illusioncis7.opencore.rules.command.RuleHistoryCommand(ruleService);

        RollbackConfigCommand rollCmd = new RollbackConfigCommand(configService);

        com.illusioncis7.opencore.reputation.command.MyRepCommand myRepCmd = new com.illusioncis7.opencore.reputation.command.MyRepCommand(reputationService);

        com.illusioncis7.opencore.gpt.command.GptLogCommand gptLogCmd = new com.illusioncis7.opencore.gpt.command.GptLogCommand(gptResponseHandler);

        com.illusioncis7.opencore.reputation.command.RepInfoCommand repInfoCmd = new com.illusioncis7.opencore.reputation.command.RepInfoCommand(reputationService);

        com.illusioncis7.opencore.reputation.command.RepChangeCommand repChangeCmd = new com.illusioncis7.opencore.reputation.command.RepChangeCommand(reputationService);

        com.illusioncis7.opencore.reputation.command.ChatFlagsCommand chatFlagsCmd = new com.illusioncis7.opencore.reputation.command.ChatFlagsCommand(chatFlagService);

        com.illusioncis7.opencore.reputation.command.ChatAnalyzeCommand chatAnalyzeCmd = new com.illusioncis7.opencore.reputation.command.ChatAnalyzeCommand(this);

        com.illusioncis7.opencore.admin.ReloadCommand reloadCmd = new com.illusioncis7.opencore.admin.ReloadCommand(this);

        com.illusioncis7.opencore.admin.StatusCommand statusCmd = new com.illusioncis7.opencore.admin.StatusCommand(gptQueueManager, votingService, database, gptService);

        com.illusioncis7.opencore.admin.ImportSqlCommand importSqlCmd = new com.illusioncis7.opencore.admin.ImportSqlCommand(database);

        com.illusioncis7.opencore.config.command.ConfigListCommand cfgListCmd = new com.illusioncis7.opencore.config.command.ConfigListCommand(configService);

        com.illusioncis7.opencore.voting.command.VoteStatusCommand voteStatusCmd = new com.illusioncis7.opencore.voting.command.VoteStatusCommand(votingService);

        coreCommand = new com.illusioncis7.opencore.command.OpenCoreCommand(this, messageService);
        coreCommand.register("suggest", suggestCmd);
        coreCommand.register("suggestions", listCmd);
        coreCommand.register("vote", voteCmd);
        coreCommand.register("rules", rulesCmd);
        coreCommand.register("rollbackconfig", rollCmd);
        coreCommand.register("myrep", myRepCmd);
        coreCommand.register("gptlog", gptLogCmd);
        coreCommand.register("repinfo", repInfoCmd);
        coreCommand.register("repchange", repChangeCmd);
        coreCommand.register("status", statusCmd);
        coreCommand.register("configlist", cfgListCmd);
        coreCommand.register("votestatus", voteStatusCmd);
        coreCommand.register("editrule", editRuleCmd);
        coreCommand.register("rulehistory", ruleHistCmd);
        coreCommand.register("chatflags", chatFlagsCmd);
        coreCommand.register("reload", reloadCmd);
        coreCommand.register("importsql", importSqlCmd);
        coreCommand.register("chatanalyze", chatAnalyzeCmd);

        Objects.requireNonNull(getCommand("opencore")).setExecutor(coreCommand);
        getCommand("opencore").setTabCompleter(coreCommand);

        if (moduleChatAnalyzer) {
            startChatAnalyzer();
        } else {
            getLogger().info("ChatAnalyzer disabled via modules.yml");
        }

        if (moduleSuggestions) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    votingService.checkOpenSuggestions();
                }
            }.runTaskTimerAsynchronously(this, 0L, 30 * 60 * 20L);
        }

        getServer().getPluginManager().registerEvents(new ChatLogger(database, getLogger()), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(reputationService, getLogger(), planHook, messageService), this);
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

    public com.illusioncis7.opencore.command.OpenCoreCommand getCoreCommand() {
        return coreCommand;
    }

    /** Reload GPT service configuration. */
    public void reloadGptService() {
        if (gptService != null) {
            gptService.reload();
        }
    }

    /** Reload module flags from modules.yml. */
    public void reloadModules() {
        org.bukkit.configuration.file.FileConfiguration modCfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File(getDataFolder(), "modules.yml"));
        org.bukkit.configuration.ConfigurationSection mods = modCfg.getConfigurationSection("modules");
        if (mods != null) {
            moduleConfigGrabber = mods.getBoolean("config-grabber", true);
            moduleSuggestions = mods.getBoolean("suggestions", true);
            moduleChatAnalyzer = mods.getBoolean("chat-analyzer", true);
        }
    }

    /** Start the periodic chat analyzer using the configured interval. */
    private void startChatAnalyzer() {
        int ticks = reputationService.getAnalysisIntervalMinutes() * 60 * 20;
        chatAnalyzerTask = new com.illusioncis7.opencore.reputation.ChatAnalyzerTask(database, gptService, reputationService, chatFlagService, ruleService, getLogger());
        chatAnalyzerTimer = chatAnalyzerTask.runTaskTimerAsynchronously(this, 0L, ticks);
    }

    /** Restart the analyzer timer after manual execution or config reload. */
    private void restartChatAnalyzer() {
        if (chatAnalyzerTimer != null) {
            chatAnalyzerTimer.cancel();
        }
        int ticks = reputationService.getAnalysisIntervalMinutes() * 60 * 20;
        chatAnalyzerTask = new com.illusioncis7.opencore.reputation.ChatAnalyzerTask(
                database, gptService, reputationService, chatFlagService, ruleService, getLogger());
        chatAnalyzerTimer = chatAnalyzerTask.runTaskTimerAsynchronously(this, ticks, ticks);
    }

    /** Trigger an immediate chat analysis and reset the interval. */
    public void runChatAnalysisNow() {
        if (!moduleChatAnalyzer || chatAnalyzerTask == null) {
            return;
        }
        // schedule the existing BukkitRunnable using a simple Runnable wrapper
        // to avoid IllegalStateException from rescheduling the same instance
        getServer().getScheduler().runTaskAsynchronously(this, chatAnalyzerTask::run);
        restartChatAnalyzer();
    }

    /** Apply configuration changes to the analyzer schedule. */
    public void reloadChatAnalyzer() {
        if (!moduleChatAnalyzer) {
            return;
        }
        if (chatAnalyzerTask == null) {
            startChatAnalyzer();
        } else {
            restartChatAnalyzer();
        }
    }

    public com.illusioncis7.opencore.setup.SetupManager getSetupManager() {
        return setupManager;
    }

    public boolean isConfigGrabberEnabled() {
        return moduleConfigGrabber;
    }

    public boolean isSuggestionsEnabled() {
        return moduleSuggestions;
    }

    public boolean isChatAnalyzerEnabled() {
        return moduleChatAnalyzer;
    }
}
