package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /lobby — teleport back to the lobby spawn from anywhere (except mid-run).
 */
public final class LobbyCommand implements CommandExecutor {

    private final Dung plugin;

    public LobbyCommand(Dung plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        if (plugin.game().instanceOf(p) != null) {
            p.sendMessage("§cYou're in a run — use §f/dung leave§c first.");
            return true;
        }
        if (p.getWorld().getName().equals(com.lieyabull.dung.world.WorldManager.LOBBY_WORLD_NAME)) {
            p.sendMessage("§7You're already in the lobby.");
            return true;
        }
        p.teleport(plugin.worldManager().lobbySpawn());
        p.sendMessage("§aWelcome back to the lobby.");
        return true;
    }
}
