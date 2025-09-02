package com.lostinbedrock.discordmod;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

public class LibDiscordModeration extends JavaPlugin {
    private static LibDiscordModeration instance;
    private DataStore dataStore;
    private DiscordBotManager botManager;
    private int sweeperTaskId = -1;

    public static LibDiscordModeration getInstance() {
        return instance;
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    public DiscordBotManager getBotManager() {
        return botManager;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Prepare data store
        File dataFile = new File(getDataFolder(), "data.yml");
        dataStore = new DataStore(dataFile);
        try {
            dataStore.load();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load data.yml", e);
        }

        // Start Discord bot
        botManager = new DiscordBotManager(this);
        botManager.startAsync();

        // Periodic sweeper for temp bans / mutes
        long intervalSeconds = getConfig().getLong("sweeper_interval_seconds", 60L);
        sweeperTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            try {
                botManager.sweepExpirations();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Sweeper error", e);
            }
        }, 20L * intervalSeconds, 20L * intervalSeconds);

        getLogger().info("LibDiscordModeration enabled.");
    }

    @Override
    public void onDisable() {
        if (sweeperTaskId != -1) {
            Bukkit.getScheduler().cancelTask(sweeperTaskId);
        }
        if (botManager != null) {
            botManager.shutdown();
        }
        if (dataStore != null) {
            try {
                dataStore.save();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to save data.yml", e);
            }
        }
        getLogger().info("LibDiscordModeration disabled.");
    }
}
