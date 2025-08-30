package net.scape.project.communitygoals.commands;

import net.milkbowl.vault.economy.Economy;
import net.scape.project.communitygoals.CommunityGoals;
import net.scape.project.communitygoals.goal.Goal;
import net.scape.project.communitygoals.goal.GoalManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;

import static net.scape.project.communitygoals.utils.Utils.format;

public class CGCommand implements CommandExecutor {

    private CommunityGoals plugin = CommunityGoals.get();

    public CGCommand() {}

    private String color(String s) {
        return format(s);
    }

    private String msg(String key) {
        FileConfiguration m = plugin.getMessages(); // messages.yml
        String prefix = color(m.getString("prefix", "&b&lCGOALS&7 "));
        String raw = m.getString(key, "&cMissing message: " + key);
        return color(prefix + raw);
    }

    private String msg(String key, Object... replacements) {
        FileConfiguration m = plugin.getMessages();
        String prefix = color(m.getString("prefix", "&b&lCGOALS&7 "));
        String raw = m.getString(key, "&cMissing message: " + key);
        if (replacements.length % 2 == 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                String place = String.valueOf(replacements[i]);
                String val = String.valueOf(replacements[i + 1]);
                raw = raw.replace("{" + place + "}", val);
            }
        }
        return color(prefix + raw);
    }

    private String usage(String key, String label) {
        String raw = plugin.getMessages().getString("usage." + key, "&cUsage: /" + label + " " + key);
        return color(raw.replace("{label}", label));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        GoalManager gm = plugin.goals();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            for (String line : plugin.getMessages().getStringList("help")) {
                sender.sendMessage(color(plugin.getMessages().getString("prefix", "&6[Goals]&7 ")
                        + line.replace("{label}", label)));
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("communitygoals.admin")) {
                    sender.sendMessage(msg("no-permission"));
                    return true;
                }
                gm.saveSync();
                gm.load();
                plugin.reloadMessages();
                sender.sendMessage(msg("reloaded"));
                return true;
            }
            case "list" -> {
                sender.sendMessage(msg("list-header"));
                for (Goal g : gm.all()) {
                    if (!g.isEnabled()) continue;
                    sender.sendMessage(msg("list-line",
                            "goal", g.getId(),
                            "percent", String.format(Locale.US, "%.0f", g.percent()),
                            "current", g.getProgress(),
                            "target", g.getTarget(),
                            "label", g.getLabel()));
                }
                return true;
            }
            case "progress" -> {
                if (args.length < 2) {
                    sender.sendMessage(usage("progress", label));
                    return true;
                }
                Goal g = gm.get(args[1]);
                if (g == null) {
                    sender.sendMessage(msg("unknown-goal", "goal", args[1]));
                    return true;
                }
                String line = plugin.getMessages().getString("progress-line", "{goal}: {bar}")
                        .replace("{goal}", g.getId())
                        .replace("{bar}", gm.bar(g)
                                .replace("{label}", g.getLabel()));
                sender.sendMessage(color(plugin.getMessages().getString("prefix", "&6[Goals]&7 ") + line));
                return true;
            }
            case "add" -> {
                if (!sender.hasPermission("communitygoals.admin")) {
                    sender.sendMessage(msg("no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(usage("add", label));
                    return true;
                }
                Goal g = gm.get(args[1]);
                if (g == null) {
                    sender.sendMessage(msg("unknown-goal", "goal", args[1]));
                    return true;
                }
                long amt;
                try {
                    amt = Long.parseLong(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(msg("not-number", "input", args[2]));
                    return true;
                }
                gm.addProgress(g.getId(), amt, (sender instanceof Player p) ? p.getUniqueId() : null);
                sender.sendMessage(msg("added-progress",
                        "amount", amt,
                        "goal", g.getId(),
                        "current", g.getProgress(),
                        "target", g.getTarget()),
                        "label", g.getLabel());
                return true;
            }
            case "set" -> {
                if (!sender.hasPermission("communitygoals.admin")) {
                    sender.sendMessage(msg("no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(usage("set", label));
                    return true;
                }
                Goal g = gm.get(args[1]);
                if (g == null) {
                    sender.sendMessage(msg("unknown-goal", "goal", args[1]));
                    return true;
                }
                long amt;
                try {
                    amt = Long.parseLong(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(msg("not-number", "input", args[2]));
                    return true;
                }
                g.setProgress(amt);
                sender.sendMessage(msg("set-progress",
                        "goal", g.getId(),
                        "current", g.getProgress(),
                        "target", g.getTarget(),
                        "label", g.getLabel()));
                return true;
            }
            case "complete" -> {
                if (!sender.hasPermission("communitygoals.admin")) {
                    sender.sendMessage(msg("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(usage("complete", label));
                    return true;
                }
                Goal g = gm.get(args[1]);
                if (g == null) {
                    sender.sendMessage(msg("unknown-goal", "goal", args[1]));
                    return true;
                }
                if (g.isCompleted()) {
                    sender.sendMessage(msg("already-complete", "goal", g.getId()));
                    return true;
                }
                gm.complete(g);
                sender.sendMessage(msg("forced-complete", "goal", g.getId()));
                return true;
            }
            case "donate" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(msg("players-only"));
                    return true;
                }
                if (!p.hasPermission("communitygoals.donate")) {
                    sender.sendMessage(msg("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(usage("donate", label));
                    return true;
                }
                Economy eco = plugin.economy();
                if (eco == null) {
                    sender.sendMessage(msg("eco-missing"));
                    return true;
                }
                double amt;
                try {
                    amt = Double.parseDouble(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(msg("not-number", "input", args[1]));
                    return true;
                }
                if (amt <= 0) {
                    sender.sendMessage(msg("amount-positive"));
                    return true;
                }
                if (!eco.has(p, amt)) {
                    sender.sendMessage(msg("not-enough-money"));
                    return true;
                }

                Goal target = plugin.goals().all().stream()
                        .filter(g -> g.isEnabled() && !g.isCompleted() && g.getType().name().equals("MONEY_DONATED"))
                        .findFirst().orElse(null);
                if (target == null) {
                    sender.sendMessage(msg("donate-disabled"));
                    return true;
                }

                eco.withdrawPlayer(p, amt);
                long add = (long) Math.floor(amt);
                plugin.goals().addProgress(target.getId(), add, p.getUniqueId());
                sender.sendMessage(msg("donate-success",
                        "amount", (long) amt,
                        "goal", target.getId(),
                        "label", target.getLabel()));
                return true;
            }
            default -> {
                sender.sendMessage(usage("unknown", label));
                return true;
            }
        }
    }
}