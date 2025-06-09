package com.illusioncis7.opencore.command;

import com.illusioncis7.opencore.OpenCore;
import com.illusioncis7.opencore.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * Root command that dispatches subcommands and supports configurable aliases.
 */
public class OpenCoreCommand implements TabExecutor {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final Map<String, TabExecutor> executors = new HashMap<>();
    private final Map<String, String> aliasMap = new HashMap<>();

    public OpenCoreCommand(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
        loadAliases();
    }

    /** Reload alias configuration from command-aliases.yml. */
    public void loadAliases() {
        aliasMap.clear();
        File file = new File(plugin.getDataFolder(), "command-aliases.yml");
        if (!file.exists()) {
            plugin.saveResource("command-aliases.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("aliases");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                List<String> aliases = sec.getStringList(key);
                for (String a : aliases) {
                    aliasMap.put(a.toLowerCase(Locale.ROOT), key.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    public void register(String name, TabExecutor exec) {
        executors.put(name.toLowerCase(Locale.ROOT), exec);
    }

    private String map(String input) {
        return aliasMap.getOrDefault(input.toLowerCase(Locale.ROOT), input.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "core.usage", null);
            return true;
        }
        String sub = map(args[0]);
        TabExecutor exec = executors.get(sub);
        if (exec == null) {
            Map<String, String> ph = Collections.singletonMap("sub", args[0]);
            messages.send(sender, "core.unknown", ph);
            return true;
        }
        String[] newArgs = Arrays.copyOfRange(args, 1, args.length);
        return exec.onCommand(sender, command, sub, newArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            Set<String> opts = new HashSet<>();
            opts.addAll(executors.keySet());
            opts.addAll(aliasMap.keySet());
            List<String> res = new ArrayList<>();
            for (String o : opts) {
                if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    res.add(o);
                }
            }
            Collections.sort(res);
            return res;
        } else if (args.length > 1) {
            String sub = map(args[0]);
            TabExecutor exec = executors.get(sub);
            if (exec != null) {
                String[] newArgs = Arrays.copyOfRange(args, 1, args.length);
                return exec.onTabComplete(sender, command, sub, newArgs);
            }
        }
        return Collections.emptyList();
    }
}
