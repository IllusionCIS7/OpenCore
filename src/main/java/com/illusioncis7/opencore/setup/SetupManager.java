package com.illusioncis7.opencore.setup;

import com.illusioncis7.opencore.config.ConfigService;
import com.illusioncis7.opencore.rules.RuleService;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

/**
 * Tracks whether the plugin is in setup mode. The state is persisted to
 * <code>setup_status.json</code> inside the plugin data folder so the web
 * interface can work offline.
 */
public class SetupManager {
    private final JavaPlugin plugin;
    private final File stateFile;
    private boolean setupActive;

    public SetupManager(JavaPlugin plugin, RuleService ruleService, ConfigService configService) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "setup_status.json");
        loadState(ruleService, configService);
    }

    private void loadState(RuleService ruleService, ConfigService configService) {
        if (stateFile.exists()) {
            try (FileReader fr = new FileReader(stateFile, StandardCharsets.UTF_8)) {
                char[] buf = new char[(int) stateFile.length()];
                int len = fr.read(buf);
                if (len > 0) {
                    JSONObject obj = new JSONObject(new String(buf, 0, len));
                    setupActive = obj.optBoolean("setupActive", false);
                    return;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to read setup state: " + e.getMessage());
            }
        }
        int rules = ruleService.getRuleCount();
        int params = configService.countParametersWithValue();
        setupActive = rules < 5 || params < 3;
        saveState();
    }

    public boolean isSetupActive() {
        return setupActive;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public File getWebDirectory() {
        return new File(plugin.getDataFolder(), "webpanel");
    }

    public void deactivate() {
        this.setupActive = false;
        saveState();
    }

    private void saveState() {
        try (FileWriter fw = new FileWriter(stateFile, StandardCharsets.UTF_8)) {
            JSONObject obj = new JSONObject();
            obj.put("setupActive", setupActive);
            fw.write(obj.toString());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save setup state: " + e.getMessage());
        }
    }
}
