package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.ItemPool;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.items.Rarity;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.meta.Upgrades;
import com.lieyabull.dung.ui.ChatUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class DungCommand implements CommandExecutor {
    private static final int WEAPON_COST = 20;
    private static final int ARMOR_COST = 15;

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
            default: return dungCmd(p, args);
        }
    }

    // ---------- /dung <sub> ----------

    private boolean dungCmd(Player p, String[] args) {
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
            case "shop": return shopCmd(p, args);
            case "upgrades": return upgradesCmd(p, args);
            case "salvage": return salvageCmd(p, args);
            case "stats": stats(p); return true;
            case "class": classCmd(p, args); return true;
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

    // ---------- /shop ----------

    private boolean shopCmd(Player p, String[] args) {
        if (args.length == 0) { showShop(p); return true; }
        switch (args[0].toLowerCase()) {
            case "weapon": return buyGear(p, true);
            case "armor": return buyGear(p, false);
            default: showShop(p); return true;
        }
    }

    /** Between-run shop: spend persistent coins on gear that carries into your next run. */
    private void showShop(Player p) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        p.sendMessage("§6--- Dung Shop ---");
        p.sendMessage("§7Persistent coins: §6" + prof.persistentCoins + " §8(earned by beating bosses, survives death)");
        p.sendMessage("§7Shards: §b" + prof.shards + " §8(from salvaging armor in a run — /salvage)");
        p.sendMessage("");
        p.sendMessage("§7Buy gear that persists between runs:");
        p.sendMessage(ChatUI.command("  [ RANDOM WEAPON — §6" + WEAPON_COST + " coins§6 ]", "/shop weapon", "Buy a random weapon (rarity up-weighted)"));
        p.sendMessage(ChatUI.command("  [ RANDOM ARMOR — §6" + ARMOR_COST + " coins§6 ]", "/shop armor", "Buy a random armor piece"));
        p.sendMessage(ChatUI.command("  [ Permanent Upgrades ]", "/upgrades", "Spend shards on permanent stat upgrades"));
    }

    private boolean buyGear(Player p, boolean weapon) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        int cost = weapon ? WEAPON_COST : ARMOR_COST;
        if (prof.persistentCoins < cost) {
            p.sendMessage("§cYou need " + cost + " coins. Earn them by beating bosses.");
            return true;
        }
        prof.persistentCoins -= cost;
        plugin.meta().save(); // persist the spend so a restart can't duplicate the item
        ItemStack item = (weapon ? ItemPool.randomWeapon(2) : ItemPool.randomArmor(2, (int) (Math.random() * 4)));
        GearFactory.markPersistent(item); // bought with persistent coins -> survives death
        p.getInventory().addItem(item);
        p.sendMessage("§aPurchased! " + item.getItemMeta().getDisplayName() + " §7(-§6" + cost + " coins§7)");
        return true;
    }

    // ---------- /upgrades ----------

    private boolean upgradesCmd(Player p, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("buy")) {
            return buyUpgrade(p, args[1].toLowerCase());
        }
        showUpgrades(p);
        return true;
    }

    /** Between-run menu: spend shards on permanent stat upgrades. */
    private void showUpgrades(Player p) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        p.sendMessage("§b--- Permanent Upgrades ---");
        p.sendMessage("§7You have §b" + prof.shards + " shards§7. Earn them with §f/salvage§7 in a run.");
        p.sendMessage("");
        for (Upgrades.Track t : Upgrades.ALL) {
            int owned = prof.upgrades.getOrDefault(t.id(), 0);
            String line = "§7" + t.label() + "  §8Lv §f" + owned;
            if (owned >= t.maxLevel()) {
                p.sendMessage("  " + line + "  §8MAXED");
            } else {
                int cost = Upgrades.cost(t, owned);
                p.sendMessage(ChatUI.command("  " + line + "  §8→ §6" + cost + " shards§8 " + effect(t, owned), "/upgrades buy " + t.id(), "Spend " + cost + " shards"));
            }
        }
    }

    private boolean buyUpgrade(Player p, String id) {
        Upgrades.Track t = Upgrades.byId(id);
        if (t == null) { p.sendMessage("§cUnknown upgrade."); return true; }
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        int owned = prof.upgrades.getOrDefault(t.id(), 0);
        if (owned >= t.maxLevel()) { p.sendMessage("§8That upgrade is already maxed."); return true; }
        int cost = Upgrades.cost(t, owned);
        if (prof.shards < cost) {
            p.sendMessage("§cYou need " + cost + " shards (have " + prof.shards + "). Salvage armor with /salvage in a run.");
            return true;
        }
        prof.shards -= cost;
        prof.upgrades.put(t.id(), owned + 1);
        plugin.meta().save();
        p.sendMessage("§a" + t.label() + " §7is now §fLv " + (owned + 1) + "§7. §8(Effect: " + effect(t, owned + 1) + ")");
        return true;
    }

    private String effect(Upgrades.Track t, int level) {
        return switch (t.id()) {
            case "damage" -> "+" + (level * 2) + " damage";
            case "hearts" -> "+" + (level * 10) + " max HP";
            case "defense" -> "+" + level + " defense";
            case "crit" -> "+" + level + "% crit chance";
            case "speed" -> "+" + (level * 5) + "% move speed";
            case "mana" -> "+" + (level * 10) + " max mana";
            default -> "";
        };
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
        if (!gm.isRunning() || !gm.player().equals(p)) {
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
        int shards = salvageValue(held);
        held.setAmount(held.getAmount() - 1);
        addShards(p, shards);
        p.sendMessage("§bSalvaged " + rarityColor(held) + held.getItemMeta().getDisplayName()
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
        if (!gm.isRunning() || !gm.player().equals(p)) {
            p.sendMessage("§cSalvage only works while inside a run. Start one with /dung start.");
            return true;
        }
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        int pieces = 0, shards = 0;
        for (int slot = 9; slot < inv.getSize(); slot++) { // 9..35 = non-hotbar main storage
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
        return "armor".equals(pdcString(s, ItemTags.KIND));
    }

    /** Shard value of one armor piece: rarity-scaled + defense. */
    private static int salvageValue(org.bukkit.inventory.ItemStack s) {
        String rs = pdcString(s, ItemTags.RARITY);
        Rarity r = rs == null ? Rarity.COMMON : Rarity.valueOf(rs);
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
        p.sendMessage("§7Class: §f" + capital(prof.classId));
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
        p.sendMessage("§aClass set to " + capital(c) + ". Next run uses it.");
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

    private String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
