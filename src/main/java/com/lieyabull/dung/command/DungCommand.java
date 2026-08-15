package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.items.ItemPool;
import com.lieyabull.dung.items.Rarity;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.ui.ChatUI;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public final class DungCommand implements CommandExecutor {
    private final Dung plugin;

    public DungCommand(Dung plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use Dung.");
            return true;
        }
        GameManager gm = plugin.game();
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "start":
                if (gm.isRunning()) {
                    p.sendMessage("§cYou're already in a run. Use /dung leave first.");
                    return true;
                }
                gm.startRun(p, System.nanoTime());
                p.sendMessage("§aRun started. Clear rooms, gear up, defeat the Warden.");
                return true;
            case "descend":
                if (!gm.isRunning()) { p.sendMessage("§cStart a run first."); return true; }
                gm.descend();
                return true;
            case "leave":
                if (gm.isRunning()) {
                    gm.endRun(true);
                    p.sendMessage("§7Left the run.");
                } else {
                    p.sendMessage("§cNo active run.");
                }
                return true;
            case "shop":
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                shop(p, gm);
                return true;
            case "stats":
                stats(p);
                return true;
            case "class":
                classCmd(p, args);
                return true;
            case "give":
                // cheat/debug command: admin-only so normal players can't spawn items/heal
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                give(p, args);
                return true;
            case "help":
            default:
                ChatUI.startPrompt(p);
                return true;
        }
    }

    private void shop(Player p, GameManager gm) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        p.sendMessage("§6--- Dung Shop ---");
        p.sendMessage("§7You have §6" + prof.persistentCoins + " persistent coins§7.");
        if (prof.persistentCoins < 20) {
            p.sendMessage("§cYou need at least 20 coins. Earn them by clearing floors.");
            return;
        }
        p.sendMessage(ChatUI.command("[ Buy a RARE weapon (§620 coins§6) ]", "/dung give rareweapon", "Spend 20 persistent coins"));
    }

    private void stats(Player p) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        p.sendMessage("§6--- " + p.getName() + " ---");
        p.sendMessage("§7Class: §f" + capital(prof.classId));
        p.sendMessage("§7Persistent coins: §6" + prof.persistentCoins);
        p.sendMessage("§7Deaths: §c" + prof.deaths + "   §7Best floor: §f" + prof.bestFloor + "   §7Kills: §f" + prof.kills);
        p.sendMessage("§7Floors cleared: §f" + prof.clears);
    }

    private void classCmd(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§7Classes: §fwarrior, mage, ranger");
            return;
        }
        String c = args[1].toLowerCase();
        if (!c.equals("warrior") && !c.equals("mage") && !c.equals("ranger")) {
            p.sendMessage("§cUnknown class.");
            return;
        }
        plugin.meta().profile(p.getUniqueId()).classId = c;
        plugin.meta().save(); // persist immediately so a crash/restart can't roll the choice back
        p.sendMessage("§aClass set to " + capital(c) + ". Next run uses it.");
    }

    private void give(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§7Usage: /dung give <rareweapon|heal|coins>"); return; }
        switch (args[1].toLowerCase()) {
            case "rareweapon":
                MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
                if (prof.persistentCoins < 20) { p.sendMessage("§cNot enough coins."); return; }
                prof.persistentCoins -= 20;
                plugin.meta().save(); // persist the spend so a restart can't duplicate the item
                p.getInventory().addItem(ItemPool.randomWeapon(2));
                p.sendMessage("§aPurchased! (Rarity up-weighted)");
                break;
            case "heal":
                p.setHealth(20);
                p.sendMessage("§aHealed.");
                break;
            case "coins":
                p.getInventory().addItem(new ItemStack(org.bukkit.Material.GOLD_NUGGET, 10));
                p.sendMessage("§e+10 coins (run).");
                break;
            default:
                p.sendMessage("§7Unknown give target.");
        }
    }

    private String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}