package net.scape.project.communitygoals;

import net.milkbowl.vault.economy.Economy;
import net.scape.project.communitygoals.commands.CGCommand;
import net.scape.project.communitygoals.goal.GoalManager;
import net.scape.project.communitygoals.listeners.CraftingStoreListener;
import net.scape.project.communitygoals.listeners.GoalListener;
import net.scape.project.communitygoals.placeholders.CGExpansion;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class CommunityGoals extends JavaPlugin {

    private static CommunityGoals instance;
    private GoalManager goalManager;
    private Economy economy; // optional

    private FileConfiguration messages;
    private File messagesFile;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("messages.yml", false);

        // Optional Vault hook
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }

        goalManager = new GoalManager();

        // Register command
        getCommand("communitygoals").setExecutor(new CGCommand());
        Bukkit.getPluginManager().registerEvents(new GoalListener(this), this);

        if (getServer().getPluginManager().isPluginEnabled("CraftingStore")) {
            getLogger().info("Hooked into CraftingStore!");
            getServer().getPluginManager().registerEvents(new CraftingStoreListener(this), this);
        }

        // Register PlaceholderAPI expansion
        if (isPlaceholderAPI()) {
            new CGExpansion(this).register();
            getLogger().info("PlaceholderAPI detected. Placeholders registered.");
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholders will be unavailable.");
        }

        getLogger().info("CommunityGoals enabled.");
    }

    @Override
    public void onDisable() {
        if (goalManager != null) goalManager.saveSync();
        getLogger().info("CommunityGoals disabled.");
    }

    public static CommunityGoals get() {
        return instance;
    }
    public GoalManager goals() { return goalManager; }
    public Economy economy() { return economy; }

    public boolean isPlaceholderAPI() {
        return getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public void reloadMessages() {
        if (messagesFile == null) {
            messagesFile = new File(getDataFolder(), "messages.yml");
        }
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public FileConfiguration getMessages() {
        if (messages == null) {
            reloadMessages();
        }
        return messages;
    }
}