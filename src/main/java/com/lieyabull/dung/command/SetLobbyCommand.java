package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /setlobby — sets the lobby spawn to the player's current location. All lobby-related
 * teleports (join, run end, leave, run-world cleanup) then use this spot.
 */
public final class SetLobbyCommand implements CommandExecutor {

    private final Dung plugin;

    public SetLobbyCommand(Dung plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        if (!p.isOp() && !p.hasPermission("dung.admin")) {
            p.sendMessage("§cYou don't have permission for that.");
            return true;
        }
        if (!plugin.worldManager().setLobbySpawn(p)) {
            p.sendMessage("§cYou must be standing inside the lobby world (§fdung_lobby§c) to set its spawn.");
            return true;
        }
        var at = p.getLocation();
        p.sendMessage("§aLobby spawn set to §f" + at.getBlockX() + ", " + at.getBlockY() + ", "
                + at.getBlockZ() + " §ain §f" + at.getWorld().getName() + "§a. Saved across restarts.");
        return true;
    }
}
