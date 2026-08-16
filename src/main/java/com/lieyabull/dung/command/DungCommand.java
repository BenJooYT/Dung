package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.ItemPool;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.items.Rarity;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.party.Party;
import com.lieyabull.dung.party.PartyManager;
import com.lieyabull.dung.ui.ChatUI;
import com.lieyabull.dung.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        switch (label.toLowerCase()) {
            case "shop": return shopCmd(p, args);
            case "upgrades": return upgradesCmd(p, args);
            case "salvage": return salvageCmd(p, args);
            case "party": return partyCmd(p, args);
            default: return dungCmd(p, args);
        }
    }

    // ---------- /dung <sub> ----------

    private boolean dungCmd(Player p, String[] args) {
        GameManager gm = plugin.game();
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "start":
                if (gm.isInInstance(p)) {
                    p.sendMessage("§cYou're already in a run. Use /dung leave first.");
                    return true;
                }
                // Check if player is in a party
                Party party = gm.partyManager().partyOf(p);
                if (party != null) {
                    // Party leader starts the run for the whole party
                    if (!party.isLeader(p.getUniqueId())) {
                        p.sendMessage("§cOnly the party leader can start a run.");
                        return true;
                    }
                    gm.startRun(party, System.nanoTime());
                    party.broadcast("§aRun started! Clear rooms, gear up, defeat the Warden.");
                } else {
                    // Solo: create a single-player party
                    party = gm.partyManager().createParty(p);
                    gm.startRun(party, System.nanoTime());
                    p.sendMessage("§aRun started. Clear rooms, gear up, defeat the Warden.");
                }
                return true;
            case "descend": {
                DungeonInstance di = gm.instanceOf(p);
                if (di == null) { p.sendMessage("§cStart a run first."); return true; }
                di.descend();
                return true;
            }
            case "leave": {
                DungeonInstance di = gm.instanceOf(p);
                if (di != null) {
                    gm.leaveInstance(p);
                    p.sendMessage("§7Left the run.");
                } else {
                    p.sendMessage("§cNo active run.");
                }
                return true;
            }
            case "party": return partyCmd(p, args);
            case "shop": return shopCmd(p, args);
            case "upgrades": return upgradesCmd(p, args);
            case "salvage": return salvageCmd(p, args);
            case "stats": stats(p); return true;
            case "class": classCmd(p, args); return true;
            case "give":
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                give(p, args);
                return true;
            case "help":
            default:
                ChatUI.startPrompt(p);
                return true;
        }
    }

    // ---------- /party ----------

    private boolean partyCmd(Player p, String[] args) {
        PartyManager pm = plugin.game().partyManager();
        if (args.length == 0) {
            Party party = pm.partyOf(p);
            if (party == null) {
                p.sendMessage("§7You are not in a party. Use §f/party create§7 to start one.");
                p.sendMessage("§7Commands: §fcreate, invite <player>, accept, decline, leave, kick <player>, disband, info");
                return true;
            }
            p.sendMessage("§6--- Party ---");
            p.sendMessage("§7Leader: §f" + Bukkit.getOfflinePlayer(party.leader()).getName());
            p.sendMessage("§7Members (" + party.size() + "/" + Party.MAX_SIZE + "):");
            for (java.util.UUID uid : party.members()) {
                String name = Bukkit.getOfflinePlayer(uid).getName();
                String tag = uid.equals(party.leader()) ? " §6(Leader)" : "";
                p.sendMessage("  §7- §f" + name + tag);
            }
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create": {
                if (pm.partyOf(p) != null) {
                    p.sendMessage("§cYou're already in a party. Leave first.");
                    return true;
                }
                pm.createParty(p);
                p.sendMessage("§aParty created! Invite players with §f/party invite <player>");
                return true;
            }
            case "invite": {
                if (args.length < 2) { p.sendMessage("§cUsage: /party invite <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage("§cPlayer not found."); return true; }
                if (target.equals(p)) { p.sendMessage("§cYou can't invite yourself."); return true; }
                if (pm.invite(p, target)) {
                    p.sendMessage("§aInvited " + target.getName() + " to the party.");
                    target.sendMessage("§a" + p.getName() + " invited you to a party! §f/party accept§a or §f/party decline");
                } else {
                    p.sendMessage("§cCould not invite. You may not be the leader, or they're already in a party.");
                }
                return true;
            }
            case "accept": {
                if (pm.acceptInvite(p)) {
                    p.sendMessage("§aYou joined the party!");
                } else {
                    p.sendMessage("§cNo pending invite or party is full.");
                }
                return true;
            }
            case "decline": {
                pm.declineInvite(p);
                p.sendMessage("§7Invite declined.");
                return true;
            }
            case "leave": {
                pm.leaveParty(p);
                p.sendMessage("§7You left the party.");
                return true;
            }
            case "kick": {
                if (args.length < 2) { p.sendMessage("§cUsage: /party kick <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage("§cPlayer not found."); return true; }
                if (pm.kick(p, target)) {
                    p.sendMessage("§aKicked " + target.getName() + " from the party.");
                } else {
                    p.sendMessage("§cCould not kick. You may not be the leader.");
                }
                return true;
            }
            case "disband": {
                if (pm.disband(p)) {
                    p.sendMessage("§cParty disbanded.");
                } else {
                    p.sendMessage("§cYou are not the party leader.");
                }
                return true;
            }
            default:
                p.sendMessage("§7Party commands: §fcreate, invite <player>, accept, decline, leave, kick <player>, disband, info");
                return true;
        }
    }

    // ---------- /shop ----------

    private boolean shopCmd(Player p, String[] args) {
        plugin.shopUI().openPersistentShop(p);
        return true;
    }

    // ---------- /upgrades ----------

    private boolean upgradesCmd(Player p, String[] args) {
        plugin.shopUI().openUpgrades(p);
        return true;
    }

    // ---------- /salvage ----------

    private boolean salvageCmd(Player p, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("all")) return salvageAll(p);
        if (args.length > 0 && (args[0].equalsIgnoreCase("fav") || args[0].equalsIgnoreCase("favorite"))) {
            return toggleFavorite(p);
        }
        return salvageHeld(p);
    }

    /** Break the held Dung armor piece into permanent shards (only during a run). */
    private boolean salvageHeld(Player p) {
        GameManager gm = plugin.game();
        if (!gm.isInInstance(p)) {
            p.sendMessage("§cSalvage only works while inside a run. Start one with /dung start.");
            return true;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        String kind = tag(held, ItemTags.KIND);
        if (!"armor".equals(kind)) {
            p.sendMessage("§cHold a Dung armor piece in your main hand to salvage it.");
            return true;
        }
        if (com.lieyabull.dung.items.GearFactory.isFavorite(held)) {
            p.sendMessage("§8That armor is §bfavorited§8. Run §f/salvage favorite§8 to un-favorite it first.");
            return true;
        }
        if (com.lieyabull.dung.items.GearFactory.isStarter(held)) {
            p.sendMessage("§8That's your free starter kit — it can't be salvaged.");
            return true;
        }
        int shards = salvageValue(held);
        held.setAmount(held.getAmount() - 1);
        addShards(p, shards);
        p.sendMessage("§bSalvaged " + rarityColor(held)
                + (held.getItemMeta() == null ? held.getType().name() : held.getItemMeta().getDisplayName())
                + "§b → §b+" + shards + " shards§7 (total §b" + plugin.meta().profile(p.getUniqueId()).shards + "§7). Spend them with /upgrades.");
        return true;
    }

    /** Toggle the favorite flag on the held armor piece (works anywhere, protects from salvage). */
    private boolean toggleFavorite(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!"armor".equals(tag(held, ItemTags.KIND))) {
            p.sendMessage("§cHold a Dung armor piece to favorite/un-favorite it.");
            return true;
        }
        boolean now = com.lieyabull.dung.items.GearFactory.toggleFavorite(held);
        p.sendMessage(now
                ? "§bFavorited — §f/salvage§b and §f/salvage all§b will skip this piece."
                : "§7Un-favorited — this piece can be salvaged again.");
        return true;
    }

    /** Salvage every salvable armor piece in the main inventory OUTSIDE the hotbar, armor slots,
     *  and offhand. Favorited pieces are always skipped. */
    private boolean salvageAll(Player p) {
        GameManager gm = plugin.game();
        if (!gm.isInInstance(p)) {
            p.sendMessage("§cSalvage only works while inside a run. Start one with /dung start.");
            return true;
        }
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        int pieces = 0, shards = 0;
        // main storage only (0-35); slots 36+ are armor/offhand which getSize() ALSO includes,
        // and those are armed/equipped, not "in the bag".
        for (int slot = 9; slot < 36; slot++) {
            org.bukkit.inventory.ItemStack s = inv.getItem(slot);
            if (!isSalvableArmor(s)) continue;
            pieces++;
            shards += salvageValue(s);
            inv.setItem(slot, null);
        }
        if (pieces == 0) {
            p.sendMessage("§7Nothing to salvage — no Dung armor in your bag that isn't favorited, hotbar, or equipped.");
            return true;
        }
        addShards(p, shards);
        p.sendMessage("§bSalvaged §f" + pieces + "§b armor pieces §b→ §b+" + shards
                + " shards§7 (total §b" + plugin.meta().profile(p.getUniqueId()).shards + "§7). Spend them with /upgrades.");
        return true;
    }

    private static boolean isSalvableArmor(org.bukkit.inventory.ItemStack s) {
        if (s == null || s.getType() == org.bukkit.Material.AIR) return false;
        if (com.lieyabull.dung.items.GearFactory.isFavorite(s)) return false;
        // /salvage all only sweeps the bag; persistent gear is never bulk-salvaged (only held salvage).
        if (com.lieyabull.dung.items.GearFactory.isPersistent(s)) return false;
        // Free starter-kit gear is never salvageable.
        if (com.lieyabull.dung.items.GearFactory.isStarter(s)) return false;
        return "armor".equals(pdcString(s, ItemTags.KIND));
    }

    /** Shard value of one armor piece: rarity-scaled + defense. */
    private static int salvageValue(org.bukkit.inventory.ItemStack s) {
        String rs = pdcString(s, ItemTags.RARITY);
        Rarity r = Rarity.COMMON;
        if (rs != null) {
            try {
                r = Rarity.valueOf(rs);
            } catch (IllegalArgumentException ignored) {
            }
        }
        int def = pdcInt(s, ItemTags.DEFENSE);
        return Math.max(1, (r.ordinal() + 1) * 2 + def / 10);
    }

    private static String pdcString(org.bukkit.inventory.ItemStack s, String key) {
        if (s == null || s.getItemMeta() == null) return null;
        return s.getItemMeta().getPersistentDataContainer().get(
                org.bukkit.NamespacedKey.minecraft(key), org.bukkit.persistence.PersistentDataType.STRING);
    }

    private static int pdcInt(org.bukkit.inventory.ItemStack s, String key) {
        if (s == null || s.getItemMeta() == null) return 0;
        Integer v = s.getItemMeta().getPersistentDataContainer().get(
                org.bukkit.NamespacedKey.minecraft(key), org.bukkit.persistence.PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private void addShards(Player p, int amount) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        prof.shards += amount;
        plugin.meta().save();
    }

    private String rarityColor(org.bukkit.inventory.ItemStack s) {
        String rs = pdcString(s, ItemTags.RARITY);
        if (rs == null) return "";
        try {
            return Rarity.valueOf(rs).legacy;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    // ---------- existing: stats / class / give ----------

    private void stats(Player p) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        p.sendMessage("§6--- " + p.getName() + " ---");
        p.sendMessage("§7Class: §f" + TextUtil.capital(prof.classId));
        p.sendMessage("§7Persistent coins: §6" + prof.persistentCoins + "   §7Shards: §b" + prof.shards);
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
        p.sendMessage("§aClass set to " + TextUtil.capital(c) + ". Next run uses it.");
    }

    private void give(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§7Usage: /dung give <rareweapon|heal|coins>"); return; }
        switch (args[1].toLowerCase()) {
            case "rareweapon":
                // free debug spawn (no coin cost): real purchases go through /shop
                p.getInventory().addItem(GearFactory.markPersistent(ItemPool.randomWeapon(2)));
                p.sendMessage("§aDebug: spawned a weapon (persists through death).");
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

    private String tag(ItemStack s, String key) {
        if (s == null || s.getType() == org.bukkit.Material.AIR || s.getItemMeta() == null) return null;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        String v = pdc.get(org.bukkit.NamespacedKey.minecraft(key), org.bukkit.persistence.PersistentDataType.STRING);
        return v;
    }

}
