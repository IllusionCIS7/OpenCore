package com.illusioncis7.opencore;

import org.bukkit.plugin.java.JavaPlugin;

public class OpenCore extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("OpenCore wurde aktiviert!");
    }

    @Override
    public void onDisable() {
        getLogger().info("OpenCore wurde deaktiviert!");
    }
}
