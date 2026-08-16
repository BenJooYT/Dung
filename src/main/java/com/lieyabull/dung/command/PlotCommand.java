package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.plot.PlotManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles /plots and /plot commands for the Plots system.
 * /plots — teleports the player to the plots world.
 * /plot claim — claims a plot for the player (250 shards or 150 coins).
 * /plot home — teleports the player to their claimed plot.
 * /plot name <name> — names your plot for warp access.
 * /plot warp <name> — teleports to a named plot.
 * /plots warp <name> — teleports to a named plot.
 */
public final class PlotCommand implements CommandExecutor, TabCompleter {

    private final Dung plugin;

    public PlotCommand(Dung plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use plot commands.");
            return true;
        }

        PlotManager pm = plugin.plotManager();

        if (label.equalsIgnoreCase("plots")) {
            if (args.length == 0) {
                // /plots — teleport to the plots world
                pm.teleportToPlots(p);
                return true;
            }
            if (args[0].equalsIgnoreCase("warp") && args.length >= 2) {
                // /plots warp <name>
                String err = pm.warpToNamedPlot(p, args[1]);
                if (err != null) {
                    p.sendMessage(err);
                } else {
                    p.sendMessage("§aWarped to your plot.");
                }
                return true;
            }
            p.sendMessage("§7Usage: §f/plots§7 or §f/plots warp <name>§7.");
            return true;
        }

        // /plot <subcommand>
        if (args.length == 0) {
            p.sendMessage("§7Plot commands:");
            p.sendMessage("  §f/plots §7— Teleport to the plots world");
            p.sendMessage("  §f/plots warp <name> §7— Warp to a named plot");
            p.sendMessage("  §f/plot claim §7— Claim a plot (§e250 shards§7 or §6" + PlotManager.CLAIM_COIN_COST + " coins§7)");
            p.sendMessage("  §f/plot home §7— Teleport to your claimed plot");
            p.sendMessage("  §f/plot name <name> §7— Name your plot for warp access");
            p.sendMessage("  §f/plot warp <name> §7— Warp to a named plot");
            p.sendMessage("  §f/plot unclaim §7— Abandon your plot and free it up");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "claim": {
                String err = pm.claimPlot(p);
                if (err != null) {
                    p.sendMessage(err);
                }
                return true;
            }
            case "home": {
                if (pm.teleportToPlot(p)) {
                    p.sendMessage("§aTeleported to your plot.");
                } else {
                    p.sendMessage("§cYou don't have a plot yet. Use §f/plot claim§c to get one.");
                }
                return true;
            }
            case "name": {
                if (args.length < 2) {
                    p.sendMessage("§cUsage: §f/plot name <name>§c — name your plot for warp access.");
                    return true;
                }
                String err = pm.setNamePlot(p, args[1]);
                if (err != null) {
                    p.sendMessage(err);
                } else {
                    p.sendMessage("§aPlot named §f" + args[1] + "§a! Use §f/plot warp " + args[1] + "§a to teleport here.");
                }
                return true;
            }
            case "warp": {
                if (args.length < 2) {
                    p.sendMessage("§cUsage: §f/plot warp <name>§c — warp to a named plot.");
                    return true;
                }
                String err = pm.warpToNamedPlot(p, args[1]);
                if (err != null) {
                    p.sendMessage(err);
                } else {
                    p.sendMessage("§aWarped to your plot.");
                }
                return true;
            }
            case "unclaim": {
                String err = pm.unclaimPlot(p);
                if (err != null) {
                    p.sendMessage(err);
                } else {
                    p.sendMessage("§aPlot unclaimed. You can claim a new one with §f/plot claim§a.");
                }
                return true;
            }
            default:
                p.sendMessage("§7Unknown subcommand. Use §f/plot claim§7, §f/plot home§7, §f/plot name§7, §f/plot warp§7, or §f/plot unclaim§7.");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            return List.of();
        }
        if (label.equalsIgnoreCase("plots")) {
            if (args.length == 1) {
                return List.of("warp");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("warp")) {
                return plugin.plotManager().getPlayerPlotNames(p);
            }
            return List.of();
        }
        // /plot tab completion
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("claim", "home", "name", "warp", "unclaim"));
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("warp") || args[0].equalsIgnoreCase("name")) {
                return plugin.plotManager().getPlayerPlotNames(p);
            }
        }
        return List.of();
    }
}