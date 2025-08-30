package net.scape.project.communitygoals.goal;

import net.scape.project.communitygoals.CommunityGoals;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static net.scape.project.communitygoals.utils.Utils.format;

public class GoalManager {

    private CommunityGoals plugin = CommunityGoals.get();
    private Map<String, Goal> goals = new LinkedHashMap<>();
    private Set<Integer> broadcastPercents = new HashSet<>();
    private int lastSavedTick = 0;
    private int taskId = -1;

    // progress bar config
    private int barLen;
    private char barFull;
    private char barEmpty;
    private String barFormat;

    public GoalManager() {
        load();
    }

    public void load() {
        plugin.reloadConfig();
        goals.clear();

        barLen = plugin.getConfig().getInt("progress-bar.length", 10);
        barFull = plugin.getConfig().getString("progress-bar.complete-char", "■").charAt(0);
        barEmpty = plugin.getConfig().getString("progress-bar.incomplete-char", "□").charAt(0);
        barFormat = plugin.getConfig().getString("progress-bar.format", "&a{bar} &7{percent}% &8({current}/{target})");

        List<Integer> bc = plugin.getConfig().getIntegerList("broadcasts.interval-percent");
        broadcastPercents.clear();
        broadcastPercents.addAll(bc);

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("goals");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection g = sec.getConfigurationSection(id);
                if (g == null) continue;
                GoalType type = GoalType.valueOf(g.getString("type", "BLOCK_BREAK").toUpperCase(Locale.ROOT));
                String desc = g.getString("description", id);
                long target = g.getLong("target", 1000);
                boolean enabled = g.getBoolean("enabled", true);
                boolean completed = false;

                String label = g.getString("label", id);

                Set<String> materials = new HashSet<>(g.getStringList("materials").stream().map(String::toUpperCase).toList());
                Set<String> entities = new HashSet<>(g.getStringList("entities").stream().map(String::toUpperCase).toList());

                List<String> rewardCmds = g.getStringList("reward-commands");
                Goal.RewardRecipients recipients = Goal.RewardRecipients.valueOf(g.getString("reward-recipients", "ALL"));
                boolean reset = g.getBoolean("reset-on-complete", true);

                long progress = 0;
                // load persisted data
                YamlConfiguration data = YamlConfiguration.loadConfiguration(getDataFile());
                String path = "goals." + id + ".progress";
                progress = data.getLong(path, 0);
                completed = data.getBoolean("goals." + id + ".completed", false);

                Goal goal = new Goal(id, label, type, desc, target, progress, materials, entities, rewardCmds, recipients, reset, enabled, completed);
                // load contributions
                ConfigurationSection csec = data.getConfigurationSection("goals." + id + ".contrib");
                if (csec != null) {
                    for (String uuidStr : csec.getKeys(false)) {
                        try {
                            UUID u = UUID.fromString(uuidStr);
                            long amt = csec.getLong(uuidStr, 0);
                            goal.getContributions().put(u, amt);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                goals.put(id, goal);
            }
        }

        // Register listeners/tasks based on used types
        registerListenersAndTasks();

        // Auto-save task
        int saveInterval = plugin.getConfig().getInt("storage.save-interval-ticks", 600);
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::saveAsync, saveInterval, saveInterval);
    }

    private void registerListenersAndTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> goals.values().stream().filter(g -> g.isEnabled() && !g.isCompleted() && g.getType() == GoalType.PLAYTIME_MINUTES)
                .forEach(g -> {
                    int online = Bukkit.getOnlinePlayers().size();
                    if (online > 0) addProgress(g.getId(), online, null); // +1 per online player per minute
                }), 20L * 60L, 20L * 60L);
    }

    public Collection<Goal> all() { return goals.values(); }
    public Goal get(String id) { return goals.get(id); }

    public String bar(Goal g) {
        return g.progressBar(barLen, barFull, barEmpty, barFormat);
    }

    public void addProgress(String id, long amount, UUID contributor) {
        Goal g = goals.get(id);
        if (g == null || !g.isEnabled() || g.isCompleted()) return;
        long beforePercent = (long)Math.floor(g.percent());
        g.addProgress(amount, contributor);
        maybeBroadcastProgress(g, beforePercent);
        if (g.getProgress() >= g.getTarget()) complete(g);
    }

    private void maybeBroadcastProgress(Goal g, long beforePercent) {
        long afterPercent = (long)Math.floor(g.percent());
        if (afterPercent != beforePercent && broadcastPercents.contains((int)afterPercent)) {
            String fmt = plugin.getConfig().getString("broadcasts.progress", "&6[Goals]&7 {goal}: &e{percent}%&7 complete.");
            String msg = fmt.replace("{goal}", g.getId()).replace("{desc}", g.getDescription())
                    .replace("{percent}", String.format(Locale.US, "%.0f", g.percent())
                            .replace("{label}", g.getLabel()));
            Bukkit.broadcastMessage(format(msg));
        }
    }

    public void complete(Goal g) {
        if (g.isCompleted()) return;
        g.setCompleted(true);
        // broadcast
        String fmt = plugin.getConfig().getString("broadcasts.complete", "&6[Goals]&a Goal complete! &e{goal}&7.");
        Bukkit.broadcastMessage(format(fmt.replace("{goal}", g.getId()).replace("{desc}", g.getDescription())
                .replace("{label}", g.getLabel())));

        // deliver rewards
        switch (g.getRecipients()) {
            case ONLINE -> {
                for (Player p : Bukkit.getOnlinePlayers())
                    dispatchAll(g.formatRewardCommands(p.getName()));
            }
            case ALL -> {
                dispatchAll(g.formatRewardCommands(null)); // run once as console, should target all via your commands
            }
            case CONTRIBUTORS -> {
                // reward contributors individually (replace %player%)
                List<UUID> uuids = new ArrayList<>(g.getContributions().keySet());
                for (UUID u : uuids) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                    for (String cmd : g.formatRewardCommands(op.getName()))
                        dispatch(cmd);
                }
            }
        }

        // reset if configured
        if (g.resetOnComplete()) {
            g.setProgress(0);
            g.getContributions().clear();
            g.setCompleted(false);
        }

        saveAsync();
    }

    private void dispatchAll(List<String> cmds) { cmds.forEach(this::dispatch); }
    private void dispatch(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ChatColor.stripColor(cmd));
    }

    public void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveSync);
    }
    public synchronized void saveSync() {
        try {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(getDataFile());
            for (Goal g : goals.values()) {
                String base = "goals." + g.getId();
                data.set(base + ".progress", g.getProgress());
                data.set(base + ".completed", g.isCompleted());
                // contributions
                Map<String, Long> cser = g.getContributions().entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
                data.set(base + ".contrib", cser);
            }
            data.save(getDataFile());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data.yml: " + e.getMessage());
        }
    }

    /**
     * Get a sorted list of top contributors for a goal.
     *
     * @param goalId the goal ID
     * @param limit  max number of contributors to return
     * @return list of formatted contributor strings
     */
    public List<String> getTopContributors(String goalId, int limit) {
        Goal goal = goals.get(goalId);
        if (goal == null) return Collections.emptyList();

        return goal.getContributions().entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                    String name = player.getName() != null ? player.getName() : entry.getKey().toString();
                    return ChatColor.GOLD + name + ChatColor.GRAY + " - " + ChatColor.AQUA + entry.getValue();
                })
                .collect(Collectors.toList());
    }

    public long getPlayerContribution(String goalId, UUID playerId) {
        Goal g = goals.get(goalId);
        return g != null ? g.getContributions().getOrDefault(playerId, 0L) : 0;
    }

    public String getPlayerContributionPercent(String goalId, UUID playerId) {
        Goal g = goals.get(goalId);
        if (g == null || g.getTarget() == 0) return "0.00";
        long contrib = g.getContributions().getOrDefault(playerId, 0L);
        double percent = (contrib * 100.0) / g.getTarget();
        return String.format(Locale.US, "%.2f", percent);
    }

    public String getTopContributorName(String goalId, int rank) {
        List<Map.Entry<UUID, Long>> sorted = getSortedContributors(goalId);
        if (rank < 1 || rank > sorted.size()) return "";
        OfflinePlayer op = Bukkit.getOfflinePlayer(sorted.get(rank - 1).getKey());
        return op.getName() != null ? op.getName() : sorted.get(rank - 1).getKey().toString();
    }

    public long getTopContributorAmount(String goalId, int rank) {
        List<Map.Entry<UUID, Long>> sorted = getSortedContributors(goalId);
        return (rank >= 1 && rank <= sorted.size()) ? sorted.get(rank - 1).getValue() : 0;
    }

    private List<Map.Entry<UUID, Long>> getSortedContributors(String goalId) {
        Goal goal = goals.get(goalId);
        if (goal == null) return Collections.emptyList();
        return goal.getContributions().entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .toList();
    }

    private File getDataFile() {
        return new File(plugin.getDataFolder(), "data.yml");
    }
}