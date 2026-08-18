package com.lieyabull.dung.ui;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.ItemPool;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.meta.Upgrades;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chest GUI shop system. Handles both in-run shop rooms (run coins) and
 * the between-run persistent shop (persistent coins + shards).
 * Opens as a named chest inventory; clicks are handled via InventoryClickEvent.
 */
public final class ShopUI implements Listener {

    private static final int RUN_SHOP_SIZE = 27; // 3 rows
    private static final int PERSISTENT_SHOP_SIZE = 27;
    private static final int UPGRADES_SIZE = 27;

    // In-run shop prices (run coins)
    private static final int RUN_WEAPON_COST = 8;
    private static final int RUN_ARMOR_COST = 6;
    private static final int RUN_HEART_COST = 4;
    private static final int RUN_MANA_COST = 3;
    private static final int RUN_KEY_COST = 4;
    private static final int RUN_BOMB_COST = 4;
    private static final int RUN_DAMAGE_BUFF_COST = 10;
    private static final int RUN_DEFENSE_BUFF_COST = 8;

    // Persistent shop prices (persistent coins)
    private static final int PERSISTENT_WEAPON_COST = 20;
    private static final int PERSISTENT_ARMOR_COST = 15;
    private static final int REPAIR_COST_PER_10 = 5; // 5 coins per 10 durability restored

    // GUI identifiers stored in inventory ItemMeta
    private static final String GUI_KEY = "dung_gui";
    private static final String GUI_RUN_SHOP = "run_shop";
    private static final String GUI_PERSISTENT_SHOP = "persistent_shop";
    private static final String GUI_UPGRADES = "upgrades";

    // Track which GUIs are open (keyed by inventory, not player, so a purchase that
    // re-opens the shop on top of the old one doesn't wipe the tracking on close).
    private final Map<Inventory, String> openGuis = new ConcurrentHashMap<>();

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Dung plugin;

    public ShopUI(Dung plugin) {
        this.plugin = plugin;
    }

    // ==================== IN-RUN SHOP ROOM ====================

    /**
     * Open the in-run shop GUI for a player in a SHOP room.
     * Shows weapons, armor, consumables, and buffs purchasable with run coins.
     */
    public void openRunShop(Player p, DungeonInstance di) {
        PlayerState st = di.playerStateOf(p);
        if (st == null) return;

        Inventory inv = Bukkit.createInventory(null, RUN_SHOP_SIZE,
                LEGACY.deserialize("§8Shop  §7(Floor " + (di.run().floorIndex + 1) + ")  §x§b§8§8§6§0§b" + st.coins + " coins"));

        // Row 0: Gear
        // Slot 0: Random Weapon
        inv.setItem(0, makeShopItem(Material.IRON_SWORD, "§fRandom Weapon",
                List.of("§7Buy a random weapon", "§7(scales with floor depth)", "", "§e" + RUN_WEAPON_COST + " coins"),
                GUI_RUN_SHOP, "weapon"));
        // Slot 1: Random Armor
        inv.setItem(1, makeShopItem(Material.IRON_CHESTPLATE, "§fRandom Armor",
                List.of("§7Buy a random armor piece", "§7(scales with floor depth)", "", "§e" + RUN_ARMOR_COST + " coins"),
                GUI_RUN_SHOP, "armor"));
        // Slot 2: Random Shield
        inv.setItem(2, makeShopItem(Material.SHIELD, "§bRandom Shield",
                List.of("§7Buy a random mana shield", "§7Auto-slots into hotbar slot 9", "§7Sneak to charge with mana", "§7Absorbs damage while active", "", "§e" + RUN_ARMOR_COST + " coins"),
                GUI_RUN_SHOP, "shield"));

        // Row 0: Consumables
        // Slot 3: Red Heart (heal)
        inv.setItem(3, makeShopItem(Material.RED_DYE, "§cRed Heart",
                List.of("§7Heal §c8 HP", "", "§e" + RUN_HEART_COST + " coins"),
                GUI_RUN_SHOP, "heart"));
        // Slot 4: Mana Potion
        inv.setItem(4, makeShopItem(Material.EXPERIENCE_BOTTLE, "§bMana Potion",
                List.of("§7Restore §b" + (int) (st.maxMana - st.mana) + " mana", "", "§e" + RUN_MANA_COST + " coins"),
                GUI_RUN_SHOP, "mana"));
        // Slot 5: Key
        inv.setItem(5, makeShopItem(Material.TRIPWIRE_HOOK, "§9Key",
                List.of("§7+1 Key", "", "§e" + RUN_KEY_COST + " coins"),
                GUI_RUN_SHOP, "key"));
        // Slot 6: Bomb
        inv.setItem(6, makeShopItem(Material.TNT, "§4Bomb",
                List.of("§7+1 Bomb", "", "§e" + RUN_BOMB_COST + " coins"),
                GUI_RUN_SHOP, "bomb"));

        // Row 1: Buffs (one floor)
        // Slot 9: Damage Buff
        inv.setItem(9, makeShopItem(Material.BLAZE_POWDER, "§cDamage Boost",
                List.of("§7+3 damage for this floor", "", "§e" + RUN_DAMAGE_BUFF_COST + " coins"),
                GUI_RUN_SHOP, "dmg_buff"));
        // Slot 10: Defense Buff
        inv.setItem(10, makeShopItem(Material.SHIELD, "§aDefense Boost",
                List.of("§7+2 defense for this floor", "", "§e" + RUN_DEFENSE_BUFF_COST + " coins"),
                GUI_RUN_SHOP, "def_buff"));

        // Fill empty slots with glass pane
        fillEmpty(inv);

        openGuis.put(inv, GUI_RUN_SHOP);
        p.openInventory(inv);
    }

    // ==================== PERSISTENT SHOP (/shop) ====================

    /**
     * Open the between-run persistent shop GUI.
     * Spend persistent coins on gear that survives death.
     */
    public void openPersistentShop(Player p) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, PERSISTENT_SHOP_SIZE,
                LEGACY.deserialize("§8Dung Shop  §x§b§8§8§6§0§b" + prof.persistentCoins + " coins  §3" + prof.shards + " shards"));

        // Slot 0: Random Weapon (persistent)
        inv.setItem(0, makeShopItem(Material.DIAMOND_SWORD, "§fRandom Weapon",
                List.of("§7Buy a random weapon", "§7(persists through death)", "", "§6" + PERSISTENT_WEAPON_COST + " coins"),
                GUI_PERSISTENT_SHOP, "weapon"));
        // Slot 1: Random Armor (persistent)
        inv.setItem(1, makeShopItem(Material.DIAMOND_CHESTPLATE, "§fRandom Armor",
                List.of("§7Buy a random armor piece", "§7(persists through death)", "", "§6" + PERSISTENT_ARMOR_COST + " coins"),
                GUI_PERSISTENT_SHOP, "armor"));
        // Slot 2: Random Shield (persistent)
        inv.setItem(2, makeShopItem(Material.SHIELD, "§bRandom Shield",
                List.of("§7Buy a random mana shield", "§7(persists through death)", "§7Auto-slots into hotbar slot 9", "§7Sneak to charge with mana", "", "§6" + PERSISTENT_ARMOR_COST + " coins"),
                GUI_PERSISTENT_SHOP, "shield"));

        // Slot 3: Repair Item (repair the held persistent item)
        inv.setItem(3, makeRepairItemButton(p));
        // Slot 4: Repair All
        inv.setItem(4, makeRepairAllButton(p));

        // Slot 5: Upgrades (opens upgrades GUI)
        inv.setItem(5, makeShopItem(Material.NETHER_STAR, "§bPermanent Upgrades",
                List.of("§7Spend shards on permanent stat upgrades", "§7You have §b" + prof.shards + " shards"),
                GUI_PERSISTENT_SHOP, "upgrades"));

        // Slot 6: Repair Broken (repair a broken item for 150 coins + 100 shards)
        inv.setItem(6, makeShopItem(Material.CRACKED_STONE_BRICKS, "§cRepair Broken Item",
                List.of("§7Repair a broken persistent item", "§7Cost: §6150 coins§7 + §3100 shards§7", "§7Adds 10 durability"),
                GUI_PERSISTENT_SHOP, "repair_broken"));

        fillEmpty(inv);

        openGuis.put(inv, GUI_PERSISTENT_SHOP);
        p.openInventory(inv);
    }

    /** Build the "Repair All" button showing the total calculated cost for all damaged items. */
    private ItemStack makeRepairAllButton(Player p) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Repair all damaged persistent gear");
        lore.add("§7Cost scales with repair count");
        int totalCost = 0;
        int count = 0;
        // Scan inventory
        for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
            ItemStack s = p.getInventory().getItem(slot);
            if (s == null || s.getType() == Material.AIR) continue;
            int d = GearFactory.getDurability(s);
            int m = GearFactory.getMaxDurability(s);
            if (d > 0 && m > 0 && d < m) {
                int missing = m - d;
                int repairAmt = Math.min(missing, 10);
                int repairCount = GearFactory.getRepairCount(s);
                int costMult = 1 + repairCount;
                int itemCost = (repairAmt / 10) * REPAIR_COST_PER_10 * costMult;
                if (repairAmt % 10 != 0) itemCost += REPAIR_COST_PER_10 * costMult;
                totalCost += itemCost;
                count++;
            }
        }
        // Scan armor
        org.bukkit.inventory.EquipmentSlot[] armorSlots = {
                org.bukkit.inventory.EquipmentSlot.HEAD,
                org.bukkit.inventory.EquipmentSlot.CHEST,
                org.bukkit.inventory.EquipmentSlot.LEGS,
                org.bukkit.inventory.EquipmentSlot.FEET
        };
        for (org.bukkit.inventory.EquipmentSlot slot : armorSlots) {
            ItemStack s = p.getInventory().getItem(slot);
            if (s == null || s.getType() == Material.AIR) continue;
            int d = GearFactory.getDurability(s);
            int m = GearFactory.getMaxDurability(s);
            if (d > 0 && m > 0 && d < m) {
                int missing = m - d;
                int repairAmt = Math.min(missing, 10);
                int repairCount = GearFactory.getRepairCount(s);
                int costMult = 1 + repairCount;
                int itemCost = (repairAmt / 10) * REPAIR_COST_PER_10 * costMult;
                if (repairAmt % 10 != 0) itemCost += REPAIR_COST_PER_10 * costMult;
                totalCost += itemCost;
                count++;
            }
        }
        lore.add("");
        if (count > 0) {
            lore.add("§7Items to repair: §f" + count);
            lore.add("§7Total cost: §6" + totalCost + " coins");
        } else {
            lore.add("§7No damaged items found");
        }
        return makeShopItem(Material.DIAMOND, "§bRepair All", lore, GUI_PERSISTENT_SHOP, "repair_all");
    }

    /** Build the "Repair Item" button showing the actual calculated cost for the held item. */
    private ItemStack makeRepairItemButton(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        List<String> lore = new ArrayList<>();
        lore.add("§7Repair the item in your main hand");
        lore.add("§7Cost scales with repair count");
        if (held != null && held.getType() != Material.AIR && GearFactory.isPersistent(held)) {
            int dur = GearFactory.getDurability(held);
            int max = GearFactory.getMaxDurability(held);
            if (dur > 0 && max > 0 && dur < max) {
                int missing = max - dur;
                int repairAmt = Math.min(missing, 10);
                int repairCount = GearFactory.getRepairCount(held);
                int costMult = 1 + repairCount;
                int cost = (repairAmt / 10) * REPAIR_COST_PER_10 * costMult;
                if (repairAmt % 10 != 0) cost += REPAIR_COST_PER_10 * costMult;
                lore.add("");
                lore.add("§7Held: §f" + held.getItemMeta().getDisplayName());
                lore.add("§7Durability: §f" + dur + "§7/§f" + max);
                lore.add("§7Repair #" + (repairCount + 1) + ": §6" + cost + " coins");
            } else if (dur <= 0) {
                lore.add("");
                lore.add("§cHeld item is broken — use 'Repair Broken Item'");
            } else {
                lore.add("");
                lore.add("§aHeld item is at full durability");
            }
        } else {
            lore.add("");
            lore.add("§7Hold a damaged persistent item");
            lore.add("§7to see the repair cost");
        }
        return makeShopItem(Material.ANVIL, "§aRepair Item", lore, GUI_PERSISTENT_SHOP, "repair");
    }

    // ==================== UPGRADES GUI ====================

    /**
     * Open the upgrades GUI where players spend shards on permanent stat upgrades.
     */
    public void openUpgrades(Player p) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, UPGRADES_SIZE,
                LEGACY.deserialize("§8Upgrades  §3" + prof.shards + " shards"));

        for (int i = 0; i < Upgrades.ALL.size(); i++) {
            Upgrades.Track t = Upgrades.ALL.get(i);
            int owned = prof.upgrades.getOrDefault(t.id(), 0);
            boolean maxed = owned >= t.maxLevel();
            int cost = maxed ? 0 : Upgrades.cost(t, owned);

            Material mat = switch (t.id()) {
                case "damage" -> Material.IRON_SWORD;
                case "magic_damage" -> Material.BLAZE_ROD;
                case "hearts" -> Material.RED_DYE;
                case "defense" -> Material.SHIELD;
                case "crit" -> Material.ARROW;
                case "speed" -> Material.FEATHER;
                case "mana" -> Material.EXPERIENCE_BOTTLE;
                default -> Material.BARRIER;
            };

            List<String> lore = new ArrayList<>();
            lore.add("§7Level: §f" + owned + "§7/§f" + t.maxLevel());
            lore.add("§7Effect: " + effectDesc(t, owned));
            if (maxed) {
                lore.add("§8MAXED");
            } else {
                lore.add("");
                lore.add("§b" + cost + " shards");
                lore.add("§7Click to upgrade");
            }

            inv.setItem(i, makeShopItem(mat, "§f" + t.label(), lore, GUI_UPGRADES, t.id()));
        }

        // Back button
        inv.setItem(22, makeShopItem(Material.ARROW, "§7← Back to Shop",
                List.of("§7Return to the main shop"), GUI_UPGRADES, "back"));

        fillEmpty(inv);

        openGuis.put(inv, GUI_UPGRADES);
        p.openInventory(inv);
    }

    // ==================== INVENTORY CLICK HANDLER ====================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String guiType = openGuis.get(e.getInventory());
        if (guiType == null) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR
                || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer()
                .get(org.bukkit.NamespacedKey.minecraft(GUI_KEY),
                        org.bukkit.persistence.PersistentDataType.STRING);
        if (action == null) return;

        switch (guiType) {
            case GUI_RUN_SHOP -> handleRunShopClick(p, action);
            case GUI_PERSISTENT_SHOP -> handlePersistentShopClick(p, action);
            case GUI_UPGRADES -> handleUpgradesClick(p, action);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player) {
            openGuis.remove(e.getInventory());
        }
    }

    /** Cancel drags within a tracked GUI so shop items can't be pulled out or swapped in. */
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player && openGuis.get(e.getInventory()) != null) {
            e.setCancelled(true);
        }
    }

    /** Defer a GUI open by one tick. Opening a new inventory synchronously from inside an
     *  InventoryClickEvent makes the resulting InventoryCloseEvent report the *new* inventory,
     *  which onClose then removes from {@link #openGuis} — leaving the shop untracked (and thus
     *  an unprotected generic container). Running it on the next tick keeps tracking intact. */
    private void reopen(Runnable open) {
        Bukkit.getScheduler().runTask(plugin, open);
    }

    // ==================== CLICK HANDLERS ====================

    private void handleRunShopClick(Player p, String action) {
        DungeonInstance di = plugin.game().instanceOf(p);
        if (di == null) {
            p.sendMessage("§cYou are not in a run.");
            p.closeInventory();
            return;
        }
        PlayerState st = di.playerStateOf(p);
        if (st == null) {
            p.closeInventory();
            return;
        }

        switch (action) {
            case "weapon" -> buyRunItem(p, di, st, RUN_WEAPON_COST, () -> {
                ItemStack s = ItemPool.randomWeapon(di.run().floorIndex);
                p.getInventory().addItem(s);
                p.sendMessage("§aPurchased weapon! §7(-§e" + RUN_WEAPON_COST + " coins§7)");
            });
            case "armor" -> buyRunItem(p, di, st, RUN_ARMOR_COST, () -> {
                ItemStack s = ItemPool.randomArmor(di.run().floorIndex, ThreadLocalRandom.current().nextInt(4));
                p.getInventory().addItem(s);
                p.sendMessage("§aPurchased armor! §7(-§e" + RUN_ARMOR_COST + " coins§7)");
            });
            case "shield" -> buyRunItem(p, di, st, RUN_ARMOR_COST, () -> {
                ItemStack s = ItemPool.randomShield(di.run().floorIndex);
                p.getInventory().addItem(s);
                p.sendMessage("§aPurchased shield! §7(-§e" + RUN_ARMOR_COST + " coins§7)");
            });
            case "heart" -> buyRunItem(p, di, st, RUN_HEART_COST, () -> {
                st.heal(8.0);
                p.sendMessage("§aHealed 8 HP! §7(-§e" + RUN_HEART_COST + " coins§7)");
            });
            case "mana" -> buyRunItem(p, di, st, RUN_MANA_COST, () -> {
                st.mana = st.maxMana;
                p.sendMessage("§aMana restored! §7(-§e" + RUN_MANA_COST + " coins§7)");
            });
            case "key" -> buyRunItem(p, di, st, RUN_KEY_COST, () -> {
                st.keys++;
                p.sendMessage("§a+1 Key §7(-§e" + RUN_KEY_COST + " coins§7)");
            });
            case "bomb" -> buyRunItem(p, di, st, RUN_BOMB_COST, () -> {
                st.bombs++;
                p.sendMessage("§a+1 Bomb §7(-§e" + RUN_BOMB_COST + " coins§7)");
            });
            case "dmg_buff" -> buyRunItem(p, di, st, RUN_DAMAGE_BUFF_COST, () -> {
                st.damage += 3;
                p.sendMessage("§c+3 damage for this floor! §7(-§e" + RUN_DAMAGE_BUFF_COST + " coins§7)");
            });
            case "def_buff" -> buyRunItem(p, di, st, RUN_DEFENSE_BUFF_COST, () -> {
                st.defense += 2;
                p.sendMessage("§a+2 defense for this floor! §7(-§e" + RUN_DEFENSE_BUFF_COST + " coins§7)");
            });
        }
        // Refresh the GUI to show updated coin count
        reopen(() -> openRunShop(p, di));
    }

    private void handlePersistentShopClick(Player p, String action) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());

        switch (action) {
            case "weapon" -> {
                if (prof.persistentCoins < PERSISTENT_WEAPON_COST) {
                    p.sendMessage("§cYou need " + PERSISTENT_WEAPON_COST + " coins.");
                    return;
                }
                prof.persistentCoins -= PERSISTENT_WEAPON_COST;
                plugin.meta().save();
                ItemStack s = GearFactory.markPersistent(ItemPool.randomWeapon(0));
                GearFactory.initDurability(s);
                p.getInventory().addItem(s);
                p.sendMessage("§aPurchased weapon! §7(-§6" + PERSISTENT_WEAPON_COST + " coins§7)");
                reopen(() -> openPersistentShop(p)); // refresh
            }
            case "armor" -> {
                if (prof.persistentCoins < PERSISTENT_ARMOR_COST) {
                    p.sendMessage("§cYou need " + PERSISTENT_ARMOR_COST + " coins.");
                    return;
                }
                prof.persistentCoins -= PERSISTENT_ARMOR_COST;
                plugin.meta().save();
                ItemStack s = GearFactory.markPersistent(ItemPool.randomArmor(0, ThreadLocalRandom.current().nextInt(4)));
                GearFactory.initDurability(s);
                p.getInventory().addItem(s);
                p.sendMessage("§aPurchased armor! §7(-§6" + PERSISTENT_ARMOR_COST + " coins§7)");
                reopen(() -> openPersistentShop(p)); // refresh
            }
            case "shield" -> {
                if (prof.persistentCoins < PERSISTENT_ARMOR_COST) {
                    p.sendMessage("§cYou need " + PERSISTENT_ARMOR_COST + " coins.");
                    return;
                }
                prof.persistentCoins -= PERSISTENT_ARMOR_COST;
                plugin.meta().save();
                ItemStack s = GearFactory.markPersistent(ItemPool.randomShield(0));
                GearFactory.initDurability(s);
                p.getInventory().addItem(s);
                p.sendMessage("§aPurchased shield! §7(-§6" + PERSISTENT_ARMOR_COST + " coins§7)");
                reopen(() -> openPersistentShop(p)); // refresh
            }
            case "repair" -> {
                // Repair the item the player is holding in their main hand
                ItemStack target = p.getInventory().getItemInMainHand();
                if (target == null || target.getType() == Material.AIR
                        || !GearFactory.isPersistent(target)) {
                    p.sendMessage("§cHold a damaged persistent item in your main hand to repair it.");
                    return;
                }
                int dur = GearFactory.getDurability(target);
                int max = GearFactory.getMaxDurability(target);
                if (dur <= 0 || max <= 0 || dur >= max) {
                    p.sendMessage("§cThat item is not damaged or is broken. Use 'Repair Broken Item' for broken items.");
                    return;
                }
                int missing = max - dur;
                int repairAmt = Math.min(missing, 10);
                int repairCount = GearFactory.getRepairCount(target);
                int costMult = 1 + repairCount; // cost multiplier: 1x, 2x, 3x, ...
                int cost = (repairAmt / 10) * REPAIR_COST_PER_10 * costMult;
                if (repairAmt % 10 != 0) cost += REPAIR_COST_PER_10 * costMult; // round up
                if (prof.persistentCoins < cost) {
                    p.sendMessage("§cYou need " + cost + " coins to repair this item (repair #" + (repairCount + 1) + ").");
                    return;
                }
                prof.persistentCoins -= cost;
                GearFactory.repairItem(target, repairAmt);
                GearFactory.setRepairCount(target, repairCount + 1);
                plugin.meta().save();
                p.sendMessage("§aRepaired item! §7(-§6" + cost + " coins§7) §7(repair #" + (repairCount + 1) + ")");
                reopen(() -> openPersistentShop(p)); // refresh
            }
            case "repair_all" -> {
                int totalCost = 0;
                java.util.List<ItemStack> toRepair = new java.util.ArrayList<>();
                // Collect all persistent items with missing durability (but not broken — dur > 0)
                for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
                    ItemStack s = p.getInventory().getItem(slot);
                    if (s == null || s.getType() == Material.AIR) continue;
                    int d = GearFactory.getDurability(s);
                    int m = GearFactory.getMaxDurability(s);
                    if (d > 0 && m > 0 && d < m) {
                        toRepair.add(s);
                    }
                }
                org.bukkit.inventory.EquipmentSlot[] armorSlots = {
                        org.bukkit.inventory.EquipmentSlot.HEAD,
                        org.bukkit.inventory.EquipmentSlot.CHEST,
                        org.bukkit.inventory.EquipmentSlot.LEGS,
                        org.bukkit.inventory.EquipmentSlot.FEET
                };
                for (org.bukkit.inventory.EquipmentSlot slot : armorSlots) {
                    ItemStack s = p.getInventory().getItem(slot);
                    if (s == null || s.getType() == Material.AIR) continue;
                    int d = GearFactory.getDurability(s);
                    int m = GearFactory.getMaxDurability(s);
                    if (d > 0 && m > 0 && d < m) {
                        toRepair.add(s);
                    }
                }
                if (toRepair.isEmpty()) {
                    p.sendMessage("§cYou have no damaged persistent gear to repair.");
                    return;
                }
                for (ItemStack s : toRepair) {
                    int d = GearFactory.getDurability(s);
                    int m = GearFactory.getMaxDurability(s);
                    int missing = m - d;
                    int repairAmt = Math.min(missing, 10);
                    int repairCount = GearFactory.getRepairCount(s);
                    int costMult = 1 + repairCount;
                    int itemCost = (repairAmt / 10) * REPAIR_COST_PER_10 * costMult;
                    if (repairAmt % 10 != 0) itemCost += REPAIR_COST_PER_10 * costMult;
                    totalCost += itemCost;
                }
                if (prof.persistentCoins < totalCost) {
                    p.sendMessage("§cYou need " + totalCost + " coins to repair all items.");
                    return;
                }
                prof.persistentCoins -= totalCost;
                for (ItemStack s : toRepair) {
                    int d = GearFactory.getDurability(s);
                    int m = GearFactory.getMaxDurability(s);
                    int missing = m - d;
                    int repairAmt = Math.min(missing, 10);
                    int repairCount = GearFactory.getRepairCount(s);
                    GearFactory.repairItem(s, repairAmt);
                    GearFactory.setRepairCount(s, repairCount + 1);
                }
                plugin.meta().save();
                p.sendMessage("§aRepaired " + toRepair.size() + " item(s)! §7(-§6" + totalCost + " coins§7)");
                reopen(() -> openPersistentShop(p)); // refresh
            }
            case "repair_broken" -> {
                // Repair the player's held broken item (main hand)
                ItemStack target = p.getInventory().getItemInMainHand();
                if (target == null || target.getType() == Material.AIR
                        || !GearFactory.isPersistent(target) || !GearFactory.isBroken(target)) {
                    p.sendMessage("§cHold a broken persistent item in your main hand to repair it.");
                    return;
                }
                if (prof.persistentCoins < 150) {
                    p.sendMessage("§cYou need 150 persistent coins to repair a broken item.");
                    return;
                }
                if (prof.shards < 100) {
                    p.sendMessage("§cYou need 100 shards to repair a broken item.");
                    return;
                }
                prof.persistentCoins -= 150;
                prof.shards -= 100;
                GearFactory.repairItem(target, 10);
                plugin.meta().save();
                p.sendMessage("§aRepaired broken item! §7(-§6150 coins§7, §3-100 shards§7) §7(+10 durability)");
                reopen(() -> openPersistentShop(p)); // refresh
            }
            case "upgrades" -> reopen(() -> openUpgrades(p));
        }
    }

    private void handleUpgradesClick(Player p, String action) {
        if ("back".equals(action)) {
            reopen(() -> openPersistentShop(p));
            return;
        }

        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        Upgrades.Track t = Upgrades.byId(action);
        if (t == null) return;

        int owned = prof.upgrades.getOrDefault(t.id(), 0);
        if (owned >= t.maxLevel()) {
            p.sendMessage("§8That upgrade is already maxed.");
            return;
        }
        int cost = Upgrades.cost(t, owned);
        if (prof.shards < cost) {
            p.sendMessage("§cYou need " + cost + " shards (have " + prof.shards + ").");
            return;
        }
        prof.shards -= cost;
        prof.upgrades.put(t.id(), owned + 1);
        plugin.meta().save();
        p.sendMessage("§a" + t.label() + " §7is now §fLv " + (owned + 1) + "§7.");
        reopen(() -> openUpgrades(p)); // refresh
    }

    // ==================== HELPERS ====================

    private void buyRunItem(Player p, DungeonInstance di, PlayerState st, int cost, Runnable purchase) {
        if (st.coins < cost) {
            p.sendMessage("§cYou need " + cost + " coins.");
            return;
        }
        st.coins -= cost;
        purchase.run();
    }

    private ItemStack makeShopItem(Material mat, String name, List<String> lore, String guiType, String action) {
        ItemStack s = new ItemStack(mat);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(LEGACY.deserialize(line));
        }
        meta.lore(loreComponents);
        meta.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.minecraft(GUI_KEY),
                org.bukkit.persistence.PersistentDataType.STRING, action);
        s.setItemMeta(meta);
        return s;
    }

    private void fillEmpty(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(""));
        filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }
    }

    private String effectDesc(Upgrades.Track t, int level) {
        return switch (t.id()) {
            case "damage" -> "+" + (level * Upgrades.delta(t)) + " melee damage";
            case "magic_damage" -> "+" + (level * Upgrades.delta(t)) + " magic damage";
            case "hearts" -> "+" + (level * Upgrades.delta(t)) + " max HP";
            case "defense" -> "+" + (level * Upgrades.delta(t)) + " defense";
            case "crit" -> "+" + (level * 0.5) + "% crit chance";
            case "speed" -> "+" + (level * Upgrades.delta(t)) + "% move speed";
            case "mana" -> "+" + (level * Upgrades.delta(t)) + " max mana";
            default -> "";
        };
    }
}