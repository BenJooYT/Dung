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

    /** Check if the player is currently in a dungeon run and send them a message if so. */
    private boolean blockIfInRun(Player p) {
        if (plugin.game().isInInstance(p)) {
            p.sendMessage("§cYou can't use plot warps while in a dungeon run.");
            return true;
        }
        return false;
    }

    /** /convert (and /plot convert): toggle whether potions may transform player-placed blocks. */
    private boolean convertCmd(Player p) {
        if (p.getLocation().getWorld() == null
                || !p.getLocation().getWorld().getName().equals("dung_plots")) {
            p.sendMessage("§cYou can only use /convert in the plots world.");
            return true;
        }
        boolean enabled = plugin.potionListener().toggleConvert(p.getUniqueId());
        p.sendMessage(enabled
                ? "§aConvert mode: §eON§a — potions can now transform player-placed blocks on your plot."
                : "§7Convert mode: §cOFF§7 — potions will only transform natural blocks.");
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use plot commands.");
            return true;
        }

        PlotManager pm = plugin.plotManager();

        if (label.equalsIgnoreCase("plots")) {
        // /convert is a bare top-level command (no args) — handled by label, not args[0].
        if (label.equalsIgnoreCase("convert")) {
            return convertCmd(p);
        }

        if (args.length == 0) {
                // /plots — teleport to the plots world
                if (blockIfInRun(p)) return true;
                pm.teleportToPlots(p);
                return true;
            }
            if (args[0].equalsIgnoreCase("warp") && args.length >= 2) {
                // /plots warp <name>
                if (blockIfInRun(p)) return true;
                String err = pm.warpToNamedPlot(p, args[1]);
                if (err != null) {
                    p.sendMessage(err);
                } else {
                    p.sendMessage("§aWarped to your plot.");
                }
                return true;
            }
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§7Usage: §f/plots§7 or §f/plots warp <name>§7."));
            return true;
        }

        // /plot <subcommand>
        if (args.length == 0) {
            p.sendMessage("§7Plot commands:");
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plots §7— Teleport to the plots world"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plots warp <name> §7— Warp to a named plot"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plot claim §7— Claim the plot you're standing on (price §ex1.25§7 per plot you own)"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plot home §7— Teleport to your claimed plot"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plot name <name> §7— Name your plot for warp access"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plot warp <name> §7— Warp to a named plot"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plot settings §7— Show settings of the plot you're standing on"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plot pvp|fire|public on|off §7— Toggle a plot setting"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plot trust|untrust <name> §7— Grant/revoke build access"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plot container|uncontainer <name> §7— Grant/revoke container access"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plot pickup|unpickup <name> §7— Grant/revoke item pickup access"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plot unclaim §7— Unclaim the plot you're standing on"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/convert §7— Toggle whether potions can transform player-placed blocks"));
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("  §f/plot filllayers §7— Fill bedrock & stone layers (op)"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "claim": {
                if (blockIfInRun(p)) return true;
                if (args.length >= 2) {
                    // /plot claim shards or /plot claim coins
                    String err = pm.claimPlot(p, args[1]);
                    if (err != null) {
                        p.sendMessage(err);
                    }
                } else {
                    // /plot claim — show balances and clickable options
                    pm.showClaimOptions(p);
                }
                return true;
            }
            case "home": {
                if (blockIfInRun(p)) return true;
                if (pm.teleportToPlot(p)) {
                    p.sendMessage("§aTeleported to your plot.");
                } else {
                    p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cYou don't have a plot yet. Use §f/plot claim§c to get one."));
                }
                return true;
            }
            case "name": {
                if (args.length < 2) {
                    p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cUsage: §f/plot name <name>§c — name your plot for warp access."));
                    return true;
                }
                String err = pm.setNamePlot(p, args[1]);
                if (err != null) {
                    p.sendMessage(err);
                } else {
                    p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§aPlot named §f" + args[1] + "§a! Use §f/plot warp " + args[1] + "§a to teleport here."));
                }
                return true;
            }
            case "warp": {
                if (blockIfInRun(p)) return true;
                if (args.length < 2) {
                    p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cUsage: §f/plot warp <name>§c — warp to a named plot."));
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
            case "convert": {
                return convertCmd(p);
            }
            case "filllayers": {
                if (!p.isOp()) {
                    p.sendMessage("§cOnly operators can use /plot filllayers.");
                    return true;
                }
                p.sendMessage("§7Filling bedrock and stone layers in the plots world...");
                int count = plugin.plotManager().fillPlotLayers();
                p.sendMessage("§aFilled bottom layers in §f" + count + " §achunks. (bedrock @ y=0, stone @ y=1-30)");
                return true;
            }
            case "unclaim": {
                if (blockIfInRun(p)) return true;
                String err = pm.unclaimPlot(p);
                if (err != null) {
                    p.sendMessage(err);
                } else {
                    p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§aPlot unclaimed. You can claim a new one with §f/plot claim§a."));
                }
                return true;
            }
            case "settings": {
                String err = pm.showPlotSettings(p);
                if (err != null) p.sendMessage(err);
                return true;
            }
            case "pvp":
            case "fire":
            case "public":
            case "mobkill": {
                if (args.length < 2) {
                    p.sendMessage("§cUsage: §f/plot " + args[0].toLowerCase() + " on|off§c.");
                    return true;
                }
                boolean on = args[1].equalsIgnoreCase("on");
                if (!on && !args[1].equalsIgnoreCase("off")) {
                    p.sendMessage("§cUsage: §f/plot " + args[0].toLowerCase() + " on|off§c.");
                    return true;
                }
                String err = pm.setPlotToggle(p, args[0].toLowerCase(), on);
                if (err != null) p.sendMessage(err);
                return true;
            }
            case "trust":
            case "untrust":
            case "container":
            case "uncontainer":
            case "pickup":
            case "unpickup": {
                if (args.length < 2) {
                    p.sendMessage("§cUsage: §f/plot " + args[0].toLowerCase() + " <player>§c.");
                    return true;
                }
                boolean add = args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("container")
                        || args[0].equalsIgnoreCase("pickup") || args[0].equalsIgnoreCase("mobkill");
                String err = pm.setPlotTrust(p, args[0].toLowerCase(), add, args[1]);
                if (err != null) p.sendMessage(err);
                return true;
            }
            default:
                p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§7Unknown subcommand. Use §f/plot claim§7, §f/plot home§7, §f/plot name§7, §f/plot warp§7, §f/plot settings§7, §f/plot pvp|fire|public|mobkill on|off§7, §f/plot trust|untrust <name>§7, §f/plot container|uncontainer <name>§7, §f/plot pickup|unpickup <name>§7, §f/plot mobkill|unmobkill <name>§7, or §f/plot unclaim§7."));
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
            List<String> subs = new ArrayList<>(List.of("claim", "home", "name", "warp", "unclaim",
                    "settings", "pvp", "fire", "public", "mobkill", "trust", "untrust", "container", "uncontainer",
                    "pickup", "unpickup", "unmobkill", "convert", "filllayers"));
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("warp") || args[0].equalsIgnoreCase("name")) {
                return plugin.plotManager().getPlayerPlotNames(p);
            }
            if (args[0].equalsIgnoreCase("pvp") || args[0].equalsIgnoreCase("fire")
                    || args[0].equalsIgnoreCase("public") || args[0].equalsIgnoreCase("mobkill")) {
                return List.of("on", "off").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
            }
            if (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust")
                    || args[0].equalsIgnoreCase("container") || args[0].equalsIgnoreCase("uncontainer")
                    || args[0].equalsIgnoreCase("pickup") || args[0].equalsIgnoreCase("unpickup")
                    || args[0].equalsIgnoreCase("mobkill") || args[0].equalsIgnoreCase("unmobkill")) {
                return playerNames(args[1]);
            }
        }
        return List.of();
    }

    /** Online player names matching the prefix, for access-management tab completion. */
    private static List<String> playerNames(String prefix) {
        String q = prefix.toLowerCase();
        return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(org.bukkit.entity.Player::getName)
                .filter(n -> n.toLowerCase().startsWith(q))
                .toList();
    }
}