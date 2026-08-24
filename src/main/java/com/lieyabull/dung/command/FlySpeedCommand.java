package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /flyspeed [1-10] — set your creative-fly speed (1 = vanilla default, 10 = fastest).
 * Bare /flyspeed shows the current level; /flyspeed reset restores the default.
 * Admin-only (dung.admin) and blocked mid-run so it can't be used as a combat cheat.
 */
public final class FlySpeedCommand implements CommandExecutor {

    private static final float DEFAULT_SPEED = 0.1f;
    private static final float MAX_SPEED = 1.0f;

    private final Dung plugin;

    public FlySpeedCommand(Dung plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        if (!p.hasPermission("dung.admin")) {
            p.sendMessage("§cYou don't have permission for that.");
            return true;
        }
        if (plugin.game().instanceOf(p) != null) {
            p.sendMessage("§cYou're in a run — fly speed can't be changed mid-run.");
            return true;
        }
        if (args.length == 0) {
            int cur = Math.round(p.getFlySpeed() / DEFAULT_SPEED);
            p.sendMessage("§7Current fly speed: §e" + cur + "§7. Use §f/flyspeed <1-10>§7 or §f/flyspeed reset§7.");
            return true;
        }
        String arg = args[0];
        if (arg.equalsIgnoreCase("reset")) {
            p.setFlySpeed(DEFAULT_SPEED);
            p.sendMessage("§aFly speed reset to 1 (vanilla default).");
            return true;
        }
        int level;
        try {
            level = Integer.parseInt(arg);
        } catch (NumberFormatException ex) {
            p.sendMessage("§c'" + arg + "' isn't a number — use §f/flyspeed <1-10>§c.");
            return true;
        }
        if (level < 1 || level > 10) {
            p.sendMessage("§cFly speed must be between 1 and 10.");
            return true;
        }
        // Vanilla creative fly is 0.1; each level adds a tenth, capped at 1.0.
        p.setFlySpeed(Math.min(level * DEFAULT_SPEED, MAX_SPEED));
        p.sendMessage("§aFly speed set to §e" + level + "§a.");
        return true;
    }
}
