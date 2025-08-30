package net.scape.project.communitygoals.listeners;

import net.scape.project.communitygoals.CommunityGoals;
import net.scape.project.communitygoals.goal.GoalType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GoalListener implements Listener {

    private final CommunityGoals plugin;
    private final Map<UUID, Double> distBuffer = new HashMap<>();

    public GoalListener(CommunityGoals plugin) {
        this.plugin = plugin;
    }

    // ===== BLOCK BREAK / PLACE =====
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        String mat = e.getBlock().getType().name();

        if (p.getGameMode() == GameMode.CREATIVE && plugin.getConfig().getBoolean("settings.creative-prevention")) return;

        plugin.goals().all().stream()
                .filter(g -> g.isEnabled() && !g.isCompleted() && g.getType() == GoalType.BLOCK_BREAK && g.matchesMaterial(mat))
                .forEach(g -> plugin.goals().addProgress(g.getId(), 1, u));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        String mat = e.getBlockPlaced().getType().name();

        if (p.getGameMode() == GameMode.CREATIVE && plugin.getConfig().getBoolean("settings.creative-prevention")) return;

        plugin.goals().all().stream()
                .filter(g -> g.isEnabled() && !g.isCompleted() && g.getType() == GoalType.BLOCK_PLACE && g.matchesMaterial(mat))
                .forEach(g -> plugin.goals().addProgress(g.getId(), 1, u));
    }

    // ===== ITEM CRAFT =====
    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        UUID u = p.getUniqueId();
        String mat = e.getRecipe().getResult().getType().name();
        int amount = e.getRecipe().getResult().getAmount();

        if (p.getGameMode() == GameMode.CREATIVE && plugin.getConfig().getBoolean("settings.creative-prevention")) return;

        plugin.goals().all().stream()
                .filter(g -> g.isEnabled() && !g.isCompleted() && g.getType() == GoalType.ITEM_CRAFT && g.matchesMaterial(mat))
                .forEach(g -> plugin.goals().addProgress(g.getId(), amount, u));
    }

    // ===== FISHING =====
    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();

        if (p.getGameMode() == GameMode.CREATIVE && plugin.getConfig().getBoolean("settings.creative-prevention")) return;

        plugin.goals().all().stream()
                .filter(g -> g.isEnabled() && !g.isCompleted() && g.getType() == GoalType.FISH_CATCH)
                .forEach(g -> plugin.goals().addProgress(g.getId(), 1, u));
    }

    // ===== MOB KILL =====
    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return; // handled separately
        if (!(e.getEntity().getKiller() instanceof Player killer)) return;
        UUID u = killer.getUniqueId();
        String ent = e.getEntityType().name();

        if (killer.getGameMode() == GameMode.CREATIVE && plugin.getConfig().getBoolean("settings.creative-prevention")) return;

        plugin.goals().all().stream()
                .filter(g -> g.isEnabled() && !g.isCompleted() && g.getType() == GoalType.MOB_KILL && g.matchesEntity(ent))
                .forEach(g -> plugin.goals().addProgress(g.getId(), 1, u));
    }

    // ===== PLAYER KILL =====
    @EventHandler(ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        UUID u = killer.getUniqueId();

        if (killer.getGameMode() == GameMode.CREATIVE && plugin.getConfig().getBoolean("settings.creative-prevention")) return;

        plugin.goals().all().stream()
                .filter(g -> g.isEnabled() && !g.isCompleted() && g.getType() == GoalType.PLAYER_KILL)
                .forEach(g -> plugin.goals().addProgress(g.getId(), 1, u));
    }

    // ===== DISTANCE TRAVELED =====
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getWorld() != e.getTo().getWorld()) return;
        Player p = e.getPlayer();

        if (p.getGameMode() == GameMode.CREATIVE && plugin.getConfig().getBoolean("settings.creative-prevention")) return;

        UUID u = p.getUniqueId();
        Location f = e.getFrom();
        Location t = e.getTo();

        double dx = t.getX() - f.getX();
        double dy = t.getY() - f.getY();
        double dz = t.getZ() - f.getZ();
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);

        if (dist <= 0.01) return;

        double total = distBuffer.getOrDefault(u, 0.0) + dist;
        if (total >= 1.0) {
            long blocks = (long)Math.floor(total);
            distBuffer.put(u, total - blocks);
            plugin.goals().all().stream()
                    .filter(g -> g.isEnabled() && !g.isCompleted() && g.getType() == GoalType.DISTANCE_TRAVELED)
                    .forEach(g -> plugin.goals().addProgress(g.getId(), blocks, u));
        } else {
            distBuffer.put(u, total);
        }
    }
}