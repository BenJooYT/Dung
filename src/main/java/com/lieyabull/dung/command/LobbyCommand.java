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
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "run.cantLobbyInRun"));
            return true;
        }
        if (p.getWorld().getName().equals(com.lieyabull.dung.world.WorldManager.LOBBY_WORLD_NAME)) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "lobby.alreadyIn"));
            return true;
        }
        p.teleport(plugin.worldManager().lobbySpawn());
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "lobby.welcomeBack"));
        return true;
    }
}
