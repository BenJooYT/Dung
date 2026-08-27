package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /troll — opens the OP-only troll item menu.
 */
public final class TrollCommand implements CommandExecutor {

    private final Dung plugin;

    public TrollCommand(Dung plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use the troll menu.");
            return true;
        }
        if (!p.isOp()) {
            p.sendMessage("§cNo permission.");
            return true;
        }
        plugin.trollUI().open(p);
        return true;
    }
}
