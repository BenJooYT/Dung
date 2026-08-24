package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.dummy.Dummy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles /dummy — the admin command system for stationary clickable dummy NPCs.
 * name/remove/name/setcommand/removecommand/tp all act on the NEAREST dummy
 * within 5 blocks of the sender. Clicks on a dummy execute its configured
 * left/right command as the clicking player.
 */
public final class DummyCommand implements CommandExecutor, TabCompleter {

    private final Dung plugin;

    public DummyCommand(Dung plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use dummy commands.");
            return true;
        }
        if (!p.isOp() && !p.hasPermission("dung.admin")) {
            p.sendMessage("§cYou don't have permission for that.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create": {
                if (args.length < 2) {
                    p.sendMessage("§cUsage: §f/dummy create <name[/r line2...]>§c.");
                    return true;
                }
                String rawName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                Dummy d = plugin.dummyManager().create(p, rawName);
                p.sendMessage("§aDummy created at your location (§f" + d.worldName + "§a).");
                return true;
            }
            case "remove": {
                if (plugin.dummyManager().removeNearest(p)) {
                    p.sendMessage("§aNearest dummy removed.");
                } else {
                    p.sendMessage("§cNo dummy within 5 blocks — look at one.");
                }
                return true;
            }
            case "name": {
                Dummy d = nearestOrMessage(p);
                if (d == null) return true;
                if (args.length < 2) {
                    p.sendMessage("§cUsage: §f/dummy name <name[/r line2...]>§c.");
                    return true;
                }
                String rawName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                plugin.dummyManager().setName(d, rawName);
                p.sendMessage("§aDummy renamed to §f" + rawName + "§a.");
                return true;
            }
            case "setcommand": {
                Dummy d = nearestOrMessage(p);
                if (d == null) return true;
                if (args.length < 3) {
                    p.sendMessage("§cUsage: §f/dummy setcommand left|right <command>§c.");
                    return true;
                }
                String cmd = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                if (args[1].equalsIgnoreCase("left")) {
                    plugin.dummyManager().setLeft(d, cmd);
                } else if (args[1].equalsIgnoreCase("right")) {
                    plugin.dummyManager().setRight(d, cmd);
                } else {
                    p.sendMessage("§cUsage: §f/dummy setcommand left|right <command>§c.");
                    return true;
                }
                p.sendMessage("§aDummy " + args[1].toLowerCase() + "-click command set to §f" + cmd + "§a.");
                return true;
            }
            case "setavatar": {
                Dummy d = nearestOrMessage(p);
                if (d == null) return true;
                if (args.length != 2) {
                    p.sendMessage("§cUsage: §f/dummy setavatar <playerName>§c.");
                    return true;
                }
                plugin.dummyManager().setAvatar(d, args[1]);
                p.sendMessage("§aDummy avatar set to §f" + args[1]
                        + "§a. §7(Skin resolves from Mojang — it may take a moment to appear.)");
                return true;
            }
            case "removecommand": {
                Dummy d = nearestOrMessage(p);
                if (d == null) return true;
                if (args.length != 2 || (!args[1].equalsIgnoreCase("left") && !args[1].equalsIgnoreCase("right"))) {
                    p.sendMessage("§cUsage: §f/dummy removecommand left|right§c.");
                    return true;
                }
                if (args[1].equalsIgnoreCase("left")) {
                    plugin.dummyManager().clearLeft(d);
                } else {
                    plugin.dummyManager().clearRight(d);
                }
                p.sendMessage("§aDummy " + args[1].toLowerCase() + "-click command cleared.");
                return true;
            }
            case "list": {
                List<Dummy> all = plugin.dummyManager().all();
                if (all.isEmpty()) {
                    p.sendMessage("§7No dummies exist. Use §f/dummy create <name>§7.");
                    return true;
                }
                p.sendMessage("§7Dummies (" + all.size() + "):");
                int i = 1;
                for (Dummy d : all) {
                    p.sendMessage(String.format("§7%d. §f%s §7(§f%s %.1f %.1f %.1f§7) L:%s R:%s",
                            i++, d.rawName(), d.worldName, d.x, d.y, d.z,
                            summary(d.leftCommand), summary(d.rightCommand)));
                }
                return true;
            }
            case "pos": {
                Dummy d = nearestOrMessage(p);
                if (d == null) return true;
                plugin.dummyManager().relocate(d, p.getLocation());
                p.sendMessage("§aDummy moved to your position and facing.");
                return true;
            }
            case "tp": {
                Dummy d = nearestOrMessage(p);
                if (d == null) return true;
                World w = Bukkit.getWorld(d.worldName);
                if (w == null) {
                    p.sendMessage("§cThat dummy's world is not loaded.");
                    return true;
                }
                p.teleport(new Location(w, d.x, d.y, d.z, d.yaw, d.pitch));
                p.sendMessage("§aTeleported to the nearest dummy.");
                return true;
            }
            default:
                sendHelp(p);
                return true;
        }
    }

    private void sendHelp(Player p) {
        p.sendMessage("§7Dummy commands:");
        p.sendMessage("  §f/dummy create <name[/r line2...]> §7— Spawn a dummy at your location");
        p.sendMessage("  §f/dummy remove §7— Remove the nearest dummy (within 5 blocks)");
        p.sendMessage("  §f/dummy name <name[/r line2...]> §7— Rename the nearest dummy");
        p.sendMessage("  §f/dummy setcommand left|right <command> §7— Set a click command (runs as the clicker)");
        p.sendMessage("  §f/dummy removecommand left|right §7— Clear a click command");
        p.sendMessage("  §f/dummy list §7— List all dummies");
        p.sendMessage("  §f/dummy pos §7— Move the nearest dummy to your position + look direction");
        p.sendMessage("  §f/dummy tp §7— Teleport to the nearest dummy");
    }

    private Dummy nearestOrMessage(Player p) {
        Dummy d = plugin.dummyManager().nearest(p);
        if (d == null) p.sendMessage("§cNo dummy within 5 blocks — look at one.");
        return d;
    }

    private static String summary(String cmd) {
        return cmd == null || cmd.isEmpty() ? "§8none" : "§e" + cmd;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("create", "remove", "name", "setcommand",
                    "removecommand", "setavatar", "list", "pos", "tp", "help"));
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("setcommand")
                || args[0].equalsIgnoreCase("removecommand"))) {
            return List.of("left", "right").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setavatar")) {
            // Suggest online players for convenience; any offline name is still accepted.
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
