package com.illusioncis7.opencore.gpt;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Loads GPT policy texts from files inside the plugin data folder.
 * The directory <code>gpt_policies</code> contains one <code>.txt</code> file per
 * policy. The filename without extension is used as policy name.
 * Missing files are created empty during plugin startup.
 */
public class PolicyService {
    private final JavaPlugin plugin;
    private final File policyDir;
    private final Map<String, String> policies = new HashMap<>();

    // policies referenced in the code base
    private static final Set<String> REQUIRED = Set.of(
            "chat_analysis",
            "suggest_classify",
            "rule_map"
    );

    public PolicyService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.policyDir = new File(plugin.getDataFolder(), "gpt_policies");
        if (!policyDir.exists() && !policyDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create policy directory: " + policyDir);
        }
        ensureFiles();
        load();
    }

    /** Reload all policy files from disk. */
    public void reload() {
        ensureFiles();
        load();
    }

    /** Get the text of a loaded policy or {@code null} if unavailable. */
    public String getPolicy(String name) {
        return policies.get(name);
    }

    /** List the names of all loaded policies. */
    public List<String> listPolicies() {
        return new ArrayList<>(policies.keySet());
    }

    /** Check whether a policy with the given name is loaded. */
    public boolean isDefined(String name) {
        return policies.containsKey(name);
    }

    // ensure required policy files exist (empty) so admins can fill them
    private void ensureFiles() {
        for (String name : REQUIRED) {
            File f = new File(policyDir, name + ".txt");
            if (!f.exists()) {
                try {
                    if (!f.createNewFile()) {
                        plugin.getLogger().warning("Could not create policy file " + f.getName());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to create policy file " + f.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void load() {
        policies.clear();
        File[] files = policyDir.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            try {
                String text = Files.readString(f.toPath(), StandardCharsets.UTF_8).trim();
                if (text.isEmpty()) {
                    plugin.getLogger().warning("Policy file " + f.getName() + " is empty – ignored");
                    continue;
                }
                String name = f.getName().substring(0, f.getName().length() - 4);
                policies.put(name, text);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load policy " + f.getName() + ": " + e.getMessage());
            }
        }
    }
}
