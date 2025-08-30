package net.scape.project.communitygoals.goal;

public enum GoalType {
    BLOCK_BREAK,
    BLOCK_PLACE,
    ITEM_CRAFT,
    FISH_CATCH,
    MOB_KILL,
    PLAYER_KILL,
    PLAYTIME_MINUTES,
    DISTANCE_TRAVELED,   // meters (blocks)
    MONEY_DONATED,       // requires Vault to donate via command
    DONATION_STORE,
}