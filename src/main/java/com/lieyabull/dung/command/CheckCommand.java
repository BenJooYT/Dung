package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.EnderChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Admin inspection tool: an op (default {@code dung.admin}) opens the container they are looking
 * at, regardless of any plot container protection. Unlike opening normally, this opens the block's
 * raw inventory directly and never fires an interact event, so plot permissions are bypassed.
 */
public final class CheckCommand implements CommandExecutor {

    /** How far (in blocks) the admin's line of sight reaches. */
    private static final int MAX_DISTANCE = 5;

    private final Dung plugin;

    public CheckCommand(Dung plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this.");
            return true;
        }
        if (!p.hasPermission("dung.admin")) {
            p.sendMessage("§cYou don't have permission to use this.");
            return true;
        }
        Block block = p.getTargetBlockExact(MAX_DISTANCE);
        if (block == null) {
            p.sendMessage("§cLook at a container to open it.");
            return true;
        }
        BlockState state = block.getState();
        if (state instanceof Container c) {
            p.openInventory(c.getInventory());
            return true;
        }
        if (state instanceof EnderChest) {
            p.openInventory(p.getEnderChest());
            return true;
        }
        p.sendMessage("§cThat block is not a container you can open.");
        return true;
    }
}