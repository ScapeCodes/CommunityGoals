package net.scape.project.communitygoals.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.scape.project.communitygoals.CommunityGoals;
import net.scape.project.communitygoals.goal.Goal;
import net.scape.project.communitygoals.goal.GoalManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class CGExpansion extends PlaceholderExpansion {

    private final CommunityGoals plugin;

    public CGExpansion(CommunityGoals plugin) { this.plugin = plugin; }

    @Override public @NotNull String getIdentifier() { return "communitygoals"; }
    @Override public @NotNull String getAuthor() { return plugin.getDescription().getAuthors().getFirst(); }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    /**
     * Placeholders supported by this expansion:
     *
     * ── Global counts ────────────────────────────────
     * %communitygoals_active_count%   → number of active (enabled & not complete) goals
     * %communitygoals_completed_count% → number of completed goals
     *
     * ── Goal progress ────────────────────────────────
     * %communitygoals_percent_<id>%   → progress percent
     * %communitygoals_current_<id>%   → current progress
     * %communitygoals_target_<id>%    → target value
     * %communitygoals_bar_<id>%       → formatted progress bar
     * %communitygoals_progress_<id>%  → current/target
     * %communitygoals_label_<id>%  → label/displayname
     * %communitygoals_status_<id>%    → active | complete | disabled
     *
     * ── Player-specific ──────────────────────────────
     * %communitygoals_player_contrib_<id>%         → player's contribution amount
     * %communitygoals_player_contrib_percent_<id>% → player's contribution percent
     *
     * Examples:
     *  - %communitygoals_player_contrib_blocks% → "128"
     *  - %communitygoals_player_contrib_percent_blocks% → "12.80"
     */
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        GoalManager gm = plugin.goals();
        String p = params.toLowerCase(Locale.ROOT);

        // --- alias: allow "goal_*" as shorthand for "communitygoals_*"
        if (p.startsWith("goal_")) {
            p = p.substring("goal_".length());
        } else if (p.startsWith("communitygoals_")) {
            p = p.substring("communitygoals_".length());
        }

        if (p.equals("active_count")) {
            return String.valueOf(gm.all().stream().filter(g -> g.isEnabled() && !g.isCompleted()).count());
        }
        if (p.equals("completed_count")) {
            return String.valueOf(gm.all().stream().filter(Goal::isCompleted).count());
        }

        // --- Top contributors ---
//        if (p.startsWith("top_")) {
//            // Format: top_<id>_<rank>_name / top_<id>_<rank>_amount
//            String[] parts = p.split("_");
//            if (parts.length < 4) return "";
//
//            String id = parts[1];
//            int rank;
//            try { rank = Integer.parseInt(parts[2]); } catch (NumberFormatException e) { return ""; }
//            String type = parts[3]; // name or amount
//            Goal g = gm.get(id);
//            if (g == null) return "";
//
//            List<Map.Entry<UUID, Long>> sorted = g.getContributions().entrySet().stream()
//                    .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
//                    .collect(Collectors.toList());
//
//            if (rank < 1 || rank > sorted.size()) return "";
//
//            Map.Entry<UUID, Long> entry = sorted.get(rank - 1);
//            if (type.equals("name")) {
//                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
//                return op.getName() != null ? op.getName() : entry.getKey().toString();
//            } else if (type.equals("amount")) {
//                return String.valueOf(entry.getValue());
//            }
//            return "";
//        }

        // --- Player-specific placeholders ---
        if (p.startsWith("player_contrib_percent_")) {
            String id = p.substring("player_contrib_percent_".length());
            Goal g = gm.get(id);
            if (g == null || player == null || g.getTarget() == 0) return "0.00";
            long contrib = CommunityGoals.get().goals().getPlayerContribution(id, player.getUniqueId());
            double percent = (contrib * 100.0) / g.getTarget();
            return String.format(Locale.US, "%.2f", percent);
        }

        if (p.startsWith("player_contrib_")) {
            String id = p.substring("player_contrib_".length());
            return String.valueOf(CommunityGoals.get().goals().getPlayerContribution(id, player.getUniqueId()));
        }

        // --- Standard placeholders ---
        String[] split = p.split("_", 2);
        if (split.length < 2) return null;
        String key = split[0];
        String id = split[1];

        Goal g = gm.get(id);
        if (g == null) return "";

        switch (key) {
            case "percent": return String.format(Locale.US, "%.1f", g.percent());
            case "current": return String.valueOf(g.getProgress());
            case "target": return String.valueOf(g.getTarget());
            case "bar": return gm.bar(g);
            case "description": return g.getDescription();
            case "progress": return g.getProgress() + "/" + g.getTarget();
            case "status":
                if (!g.isEnabled()) return "disabled";
                if (g.isCompleted()) return "complete";
                return "active";
            default: return "";
        }
    }
}
