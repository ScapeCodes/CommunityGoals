package net.scape.project.communitygoals.goal;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.*;
import java.util.stream.Collectors;

public class Goal {

    private final String id;
    private final String label;
    private final GoalType type;
    private final String description;
    private final long target;
    private long progress;
    private final Set<String> materials; // or empty
    private final Set<String> entities;  // or empty
    private final List<String> rewardCommands;
    private final RewardRecipients recipients;
    private final boolean resetOnComplete;
    private final Map<UUID, Long> contributions = new HashMap<>();
    private boolean enabled;
    private boolean completed;

    public enum RewardRecipients { ONLINE, ALL, CONTRIBUTORS }

    public Goal(String id, String label, GoalType type, String description, long target, long progress,
                Set<String> materials, Set<String> entities, List<String> rewardCommands,
                RewardRecipients recipients, boolean resetOnComplete, boolean enabled, boolean completed) {
        this.id = id;
        this.label = label;
        this.type = type;
        this.description = description;
        this.target = Math.max(1, target);
        this.progress = Math.max(0, progress);
        this.materials = materials == null ? Collections.emptySet() : materials;
        this.entities = entities == null ? Collections.emptySet() : entities;
        this.rewardCommands = rewardCommands == null ? Collections.emptyList() : rewardCommands;
        this.recipients = recipients;
        this.resetOnComplete = resetOnComplete;
        this.enabled = enabled;
        this.completed = completed;
    }

    public String getId() { return id; }
    public String getLabel() {
        return label;
    }
    public GoalType getType() { return type; }
    public String getDescription() { return description; }
    public long getTarget() { return target; }
    public long getProgress() { return progress; }
    public boolean isEnabled() { return enabled; }
    public boolean isCompleted() { return completed; }
    public Set<String> getMaterials() { return materials; }
    public Set<String> getEntities() { return entities; }
    public RewardRecipients getRecipients() { return recipients; }
    public boolean resetOnComplete() { return resetOnComplete; }
    public Map<UUID, Long> getContributions() { return contributions; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public long addProgress(long amount, UUID contributor) {
        if (completed || !enabled || amount <= 0) return progress;
        progress = Math.min(target, progress + amount);
        if (contributor != null) contributions.merge(contributor, amount, Long::sum);
        return progress;
    }

    public void setProgress(long value) {
        progress = Math.max(0, Math.min(target, value));
    }

    public double percent() { return Math.min(100.0, (progress * 100.0) / target); }

    public String progressBar(int length, char cFull, char cEmpty, String format) {
        double pct = percent() / 100.0;
        int full = (int) Math.round(length * pct);
        String bar = String.valueOf(cFull).repeat(Math.max(0, full)) +
                String.valueOf(cEmpty).repeat(Math.max(0, length - full));
        String out = format.replace("{bar}", bar)
                .replace("{percent}", String.format(Locale.US, "%.1f", percent()))
                .replace("{current}", String.valueOf(progress))
                .replace("{target}", String.valueOf(target));
        return ChatColor.translateAlternateColorCodes('&', out);
    }

    public boolean matchesMaterial(String mat) {
        return materials.isEmpty() || materials.contains(mat);
    }
    public boolean matchesEntity(String ent) {
        return entities.isEmpty() || entities.contains(ent);
    }

    public List<String> formatRewardCommands(String playerName) {
        return rewardCommands.stream()
                .map(s -> s.replace("{goal}", id).replace("{desc}", description)
                        .replace("%player%", playerName == null ? "CONSOLE" : playerName))
                .collect(Collectors.toList());
    }
}