package net.scape.project.communitygoals.listeners;

import net.craftingstore.bukkit.events.DonationReceivedEvent;
import net.scape.project.communitygoals.CommunityGoals;
import net.scape.project.communitygoals.goal.GoalType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class CraftingStoreListener implements Listener {

    private final CommunityGoals plugin;

    public CraftingStoreListener(CommunityGoals plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCraftingStorePurchase(DonationReceivedEvent e) {
        UUID u = e.getUuid();
        double amount = (int) Math.round((double) e.getDonation().getPackage().getPriceInCents() / (double)100.0F);;

        plugin.goals().all().stream()
                .filter(g -> g.isEnabled() && !g.isCompleted() && g.getType() == GoalType.DONATION_STORE)
                .forEach(g -> plugin.goals().addProgress(g.getId(), (long) Math.floor(amount), u));
    }
}