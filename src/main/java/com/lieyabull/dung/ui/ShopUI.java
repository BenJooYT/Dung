package com.lieyabull.dung.ui;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.WorkstationRules;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.ItemPool;
import com.lieyabull.dung.items.Rarity;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.meta.Upgrades;
import com.lieyabull.dung.plot.potion.PotionDefinition;
import com.lieyabull.dung.plot.potion.PotionFactory;
import com.lieyabull.dung.ui.StashUI;
import com.lieyabull.dung.shop.Category;
import com.lieyabull.dung.shop.RollAnimationMath;
import com.lieyabull.dung.shop.ServerSideRollResult;
import com.lieyabull.dung.shop.ShopPendingStore;
import com.lieyabull.dung.shop.ShopRules;
import com.lieyabull.dung.shop.ShopTransaction;
import com.lieyabull.dung.shop.ShopType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gacha-style gear shop used by BOTH the in-run SHOP room (run coins) and the between-run persistent
 * shop (persistent coins). A 27-slot main menu lists the three roll categories — WEAPONS, ARMOR,
 * MANA SHIELDS (+ persistent-only repair/upgrade utilities); each opens a dedicated 54-slot roll
 * GUI backed by the existing randomized item pool.
 * <p>
 * Purchases are a two-stage horizontal slot-machine roll (item, then rarity). The result is generated
 * server-side the moment ROLL is clicked (and the currency charged) — the animations only present that
 * already-determined result. The player then chooses KEEP or SALVAGE; salvaging awards shards.
 * <p>
 * Security: currency is charged exactly once per roll, only one roll runs at a time, the result can be
 * resolved exactly once, GUI items can never be taken or moved, and paid-for persistent results are
 * written to disk so a disconnect/restart can never lose or duplicate a purchase.
 */
public final class ShopUI implements Listener {

    private static final int MENU_SIZE = 27;  // main shop menu: category entries (+ persistent utilities)
    private static final int SIZE = 54;       // per-category roll GUI
    private static final int UPGRADES_SIZE = 27;

    private static final String GUI_KEY = "dung_gui";
    private static final String ACTION_MENU_WEAPON = "menu_weapon";
    private static final String ACTION_MENU_ARMOR = "menu_armor";
    private static final String ACTION_MENU_SHIELD = "menu_shield";
    private static final String ACTION_MENU_SUPPLIES = "menu_supplies";
    private static final String ACTION_ROLL = "roll";
    private static final String ACTION_KEEP = "keep";
    private static final String ACTION_SALVAGE = "salvage";
    private static final String ACTION_BACK = "back";

    // Supplies GUI (in-run shop only): direct purchases for run consumables, no gacha.
    private static final String ACTION_BUY_KEY = "buy_key";
    private static final String ACTION_BUY_BOMB = "buy_bomb";
    private static final String ACTION_BUY_HEART = "buy_heart";
    private static final String ACTION_BUY_MANA = "buy_mana";
    private static final String ACTION_BUY_DMG_TONIC = "buy_dmg_tonic";
    private static final String ACTION_BUY_DEF_TONIC = "buy_def_tonic";

    // Potion purchase actions
    private static final String ACTION_BUY_FOREST_POTION = "buy_forest_potion";
    private static final String ACTION_BUY_STONE_POTION = "buy_stone_potion";
    private static final int POTION_FOREST_COST = 55;  // persistent coins
    private static final int POTION_STONE_COST = 55;

    // Persistent shop utility buttons (kept from the legacy persistent shop — not categories).
    private static final String ACTION_REPAIR = "repair";
    private static final String ACTION_REPAIR_ALL = "repair_all";
    private static final String ACTION_REPAIR_BROKEN = "repair_broken";
    private static final String ACTION_UPGRADES = "upgrades";
    private static final int REPAIR_COST_PER_10 = 5; // 5 persistent coins per 10 durability restored
    private static final int REPAIR_BROKEN_COINS = 150;
    private static final int REPAIR_BROKEN_SHARDS = 100;

    // Main menu (27 slots): the three roll categories in one row; persistent-only utility row below.
    private static final int MENU_WEAPON_SLOT = 10;
    private static final int MENU_ARMOR_SLOT = 12;
    private static final int MENU_SHIELD_SLOT = 14;
    private static final int MENU_SUPPLIES_SLOT = 16; // run shop only
    private static final int MENU_POTION_FOREST_SLOT = 18; // persistent shop only
    private static final int MENU_REPAIR_SLOT = 20;
    private static final int MENU_UPGRADES_SLOT = 22;
    private static final int MENU_REPAIR_ALL_SLOT = 24;
    private static final int MENU_POTION_STONE_SLOT = 26; // persistent shop only

    // Supplies GUI (27 slots) — run consumables bought directly with run coins.
    private static final int SUPPLY_SIZE = 27;
    private static final int SUPPLY_KEY_SLOT = 10;
    private static final int SUPPLY_BOMB_SLOT = 12;
    private static final int SUPPLY_HEART_SLOT = 14;
    private static final int SUPPLY_MANA_SLOT = 16;
    private static final int SUPPLY_DMG_TONIC_SLOT = 20;
    private static final int SUPPLY_DEF_TONIC_SLOT = 24;
    private static final int SUPPLY_KEY_COST = 6;
    private static final int SUPPLY_BOMB_COST = 6;
    private static final int SUPPLY_HEART_COST = 5;
    private static final int SUPPLY_MANA_COST = 4;
    private static final int SUPPLY_TONIC_COST = 12;
    /** Stat boost granted by each tonic for the rest of the run. */
    private static final int TONIC_STAT_AMOUNT = 2;

    // Roll GUI (54 slots). Kept intentionally sparse: the two roll windows, ROLL and KEEP/SALVAGE —
    // no tabs, rails, info panes, or redundant status/preview items. Balance lives in the title.
    private static final int BACK_SLOT = 4;
    private static final int ROLL_SLOT = 22;
    private static final int KEEP_SLOT = 37;
    private static final int SALVAGE_SLOT = 43;

    // Two-stage slot machine: the item row scrolls the item and keeps it once finished, then the
    // rarity row directly beneath it scrolls the rarity.
    private static final int[] WINDOW_ITEM_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] WINDOW_RARITY_SLOTS = {29, 30, 31, 32, 33};
    private static final int WINDOW_CENTER = RollAnimationMath.CENTER;
    private static final int ITEM_STEPS = 14;   // scroll steps in the item slot-machine
    private static final int RARITY_STEPS = 10; // scroll steps in the rarity slot-machine

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Dung plugin;
    private final NamespacedKey key;
    private final ShopPendingStore pendingStore;
    private final Map<UUID, ShopSession> sessions = new ConcurrentHashMap<>();
    private final Map<Inventory, UUID> openGuis = new ConcurrentHashMap<>();      // roll GUIs
    private final Map<Inventory, UUID> openMenuGuis = new ConcurrentHashMap<>();  // main menus
    private final Map<Inventory, UUID> openSupplyGuis = new ConcurrentHashMap<>(); // supplies GUIs
    private final Map<Inventory, UUID> openUpgradeGuis = new ConcurrentHashMap<>();

    public ShopUI(Dung plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, GUI_KEY); // plugin-owned namespace, not the reserved minecraft: one
        this.pendingStore = new ShopPendingStore(plugin);
    }

    // ==================== OPENING ====================

    /** Open the in-run shop (run coins) for a player in a SHOP room. */
    public void openRunShop(Player p, DungeonInstance di) {
        PlayerState st = di.playerStateOf(p);
        if (st == null) return;
        ShopSession s = sessions.get(p.getUniqueId());
        if (s == null || s.type != ShopType.RUN) {
            s = new ShopSession(p.getUniqueId(), ShopType.RUN);
            sessions.put(p.getUniqueId(), s);
        }
        s.type = ShopType.RUN;
        s.di = di;
        open(p, s);
    }

    /** Open the between-run persistent shop (persistent coins). Restores any paid-for result that is
     *  still pending (e.g. from a previous disconnect) so it can be resolved, never lost. */
    public void openPersistentShop(Player p) {
        ShopSession s = sessions.get(p.getUniqueId());
        if (s == null || s.type != ShopType.PERSISTENT) {
            s = new ShopSession(p.getUniqueId(), ShopType.PERSISTENT);
            sessions.put(p.getUniqueId(), s);
        }
        s.type = ShopType.PERSISTENT;
        s.di = null;
        ServerSideRollResult pending = pendingStore.get(p.getUniqueId());
        if (pending != null && s.transaction.state() == ShopTransaction.State.IDLE) {
            s.transaction.startRoll(pending.category);
            s.transaction.attachResult(pending);
            s.transaction.rarityAnimationFinished(); // fast-forward to RESULT
            buildWindows(s); // restore the frozen neighbors around the recovered result
        }
        open(p, s);
    }

    /** Called from {@link com.lieyabull.dung.listener.GameListener#onQuit}: drop the in-memory session
     *  (run gear dies with the run). Persistent pending stays in the disk-backed store. */
    public void onQuit(Player p) {
        ShopSession s = sessions.remove(p.getUniqueId());
        if (s != null && s.animationTask != null) s.animationTask.cancel();
    }

    /** Force-close any open shop/supplies GUI and drop the session like {@link #onQuit} — used when
     *  a player leaves or ends a run so a stale session can never outlive its instance. */
    public void forceClose(Player p) {
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top != null && (openGuis.containsKey(top) || openMenuGuis.containsKey(top)
                || openSupplyGuis.containsKey(top))) {
            p.closeInventory();
        }
        ShopSession s = sessions.remove(p.getUniqueId());
        if (s != null && s.animationTask != null) s.animationTask.cancel();
    }

    private void open(Player p, ShopSession s) {
        // A pending (paid-for) result always reopens its roll GUI so it can be resolved.
        if (s.transaction.state() != ShopTransaction.State.IDLE && s.transaction.category() != null) {
            openRoll(p, s, s.transaction.category());
            return;
        }
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE, LEGACY.deserialize(title(p, s)));
        openMenuGuis.put(inv, s.player);
        renderMenu(p, s, inv);
        p.openInventory(inv);
    }

    /** Open the dedicated roll GUI for one category. */
    private void openRoll(Player p, ShopSession s, Category cat) {
        s.transaction.selectCategory(cat);
        Inventory old = s.inv;
        if (old != null) openGuis.remove(old);
        Inventory inv = Bukkit.createInventory(null, SIZE,
                LEGACY.deserialize(title(p, s) + clang(p, "shop.titleSeparator", catLabel(p, cat))));
        openGuis.put(inv, s.player);
        s.inv = inv;
        render(p, s);
        p.openInventory(inv);
    }

    /** Recreate the open roll inventory with a fresh title/balance, preserving the session state. */
    private void refresh(Player p, ShopSession s) {
        if (s.transaction.state() == ShopTransaction.State.IDLE) s.rollNeutralIcon = null;
        Category cat = s.transaction.category();
        if (cat == null || s.transaction.state() == ShopTransaction.State.IDLE) {
            reopen(() -> open(p, s));
            return;
        }
        Inventory old = s.inv;
        if (old != null) openGuis.remove(old);
        Inventory inv = Bukkit.createInventory(null, SIZE,
                LEGACY.deserialize(title(p, s) + clang(p, "shop.titleSeparator", catLabel(p, cat))));
        openGuis.put(inv, s.player);
        s.inv = inv;
        render(p, s);
        p.openInventory(inv);
    }

    private String title(Player p, ShopSession s) {
        if (s.type == ShopType.RUN) {
            int floor = s.di == null ? 1 : s.di.currentFloorNumber();
            PlayerState st = s.di == null ? null : s.di.playerStateOf(p);
            int coins = st == null ? 0 : st.coins;
            return com.lieyabull.dung.lang.Lang.forPlayer(p, "shop.title.run", floor, coins);
        }
        MetaManager.MetaProfile prof = plugin.meta().profile(s.player);
        return com.lieyabull.dung.lang.Lang.forPlayer(p, "shop.title.persistent",
                prof.persistentCoins, prof.shards);
    }

    // ==================== RENDERING ====================

    private void render(Player p, ShopSession s) {
        Inventory inv = s.inv;
        if (inv == null) return;
        inv.clear();
        fillEmpty(inv);
        inv.setItem(BACK_SLOT, makeButton(Material.ARROW, clang(p, "shop.back.name"),
                List.of(clang(p, "shop.back.lore")), ACTION_BACK));
        switch (s.transaction.state()) {
            case IDLE -> renderIdle(p, s);
            case RESULT, KEEP_PENDING -> renderResult(p, s);
            case ROLLING_ITEM, ROLLING_RARITY -> renderRolling(p, s);
        }
    }

    /** The main shop menu: one entry per roll category; persistent-only utilities below. */
    private void renderMenu(Player p, ShopSession s, Inventory inv) {
        inv.clear();
        fillEmpty(inv);
        inv.setItem(MENU_WEAPON_SLOT, menuEntry(p, s, Category.WEAPON, ACTION_MENU_WEAPON));
        inv.setItem(MENU_ARMOR_SLOT, menuEntry(p, s, Category.ARMOR, ACTION_MENU_ARMOR));
        inv.setItem(MENU_SHIELD_SLOT, menuEntry(p, s, Category.MANA_SHIELD, ACTION_MENU_SHIELD));
        if (s.type == ShopType.PERSISTENT) {
            inv.setItem(MENU_REPAIR_SLOT, makeRepairItemButton(p));
            inv.setItem(MENU_UPGRADES_SLOT, makeButton(Material.NETHER_STAR,
                    clang(p, "shop.upgrades.name"),
                    List.of(clang(p, "shop.upgrades.lore")), ACTION_UPGRADES));
            inv.setItem(MENU_REPAIR_ALL_SLOT, makeRepairAllButton(p));
            // Potion buttons
            inv.setItem(MENU_POTION_FOREST_SLOT, makePotionBuyButton(Material.SPLASH_POTION,
                    clang(p, "shop.potion.forest.name"), POTION_FOREST_COST, p,
                    List.of(clang(p, "shop.potion.forest.lore1"),
                            clang(p, "shop.potion.forest.lore2")),
                    ACTION_BUY_FOREST_POTION));
            inv.setItem(MENU_POTION_STONE_SLOT, makePotionBuyButton(Material.SPLASH_POTION,
                    clang(p, "shop.potion.stone.name"), POTION_STONE_COST, p,
                    List.of(clang(p, "shop.potion.stone.lore1"),
                            clang(p, "shop.potion.stone.lore2")),
                    ACTION_BUY_STONE_POTION));
        } else {
            inv.setItem(MENU_SUPPLIES_SLOT, makeButton(Material.CHEST,
                    clang(p, "shop.supplies.name"),
                    List.of(clang(p, "shop.supplies.lore1"),
                            clang(p, "shop.supplies.lore2")),
                    ACTION_MENU_SUPPLIES));
        }
    }

    /** Open the in-run supplies GUI (direct purchases with run coins). */
    private void openSupplies(Player p, ShopSession s) {
        Inventory inv = Bukkit.createInventory(null, SUPPLY_SIZE,
                LEGACY.deserialize(title(p, s) + clang(p, "shop.titleSeparator",
                        clang(p, "shop.supplies.titleSuffix"))));
        openSupplyGuis.put(inv, s.player);
        renderSupplies(p, s, inv);
        p.openInventory(inv);
    }

    private void renderSupplies(Player p, ShopSession s, Inventory inv) {
        DungeonInstance di = s.di != null ? s.di : plugin.game().instanceOf(p);
        PlayerState st = di == null ? null : di.playerStateOf(p);
        int coins = st == null ? 0 : st.coins;
        inv.clear();
        fillEmpty(inv);
        inv.setItem(BACK_SLOT, makeButton(Material.ARROW, clang(p, "shop.back.name"),
                List.of(clang(p, "shop.back.lore")), ACTION_BACK));
        inv.setItem(SUPPLY_KEY_SLOT, supplyButton(p, Material.TRIPWIRE_HOOK,
                clang(p, "shop.supply.key.name"), SUPPLY_KEY_COST, coins,
                List.of(clang(p, "shop.supply.key.lore"),
                        st == null ? "" : clang(p, "shop.supply.have", "§9", st.keys)), ACTION_BUY_KEY));
        inv.setItem(SUPPLY_BOMB_SLOT, supplyButton(p, Material.TNT,
                clang(p, "shop.supply.bomb.name"), SUPPLY_BOMB_COST, coins,
                List.of(clang(p, "shop.supply.bomb.lore"),
                        st == null ? "" : clang(p, "shop.supply.have", "§4", st.bombs)), ACTION_BUY_BOMB));
        inv.setItem(SUPPLY_HEART_SLOT, supplyButton(p, Material.RED_DYE,
                clang(p, "shop.supply.heart.name"), SUPPLY_HEART_COST, coins,
                List.of(clang(p, "shop.supply.heart.lore")), ACTION_BUY_HEART));
        inv.setItem(SUPPLY_MANA_SLOT, supplyButton(p, Material.LAPIS_LAZULI,
                clang(p, "shop.supply.mana.name"), SUPPLY_MANA_COST, coins,
                List.of(clang(p, "shop.supply.mana.lore")), ACTION_BUY_MANA));
        inv.setItem(SUPPLY_DMG_TONIC_SLOT, supplyButton(p, Material.BLAZE_POWDER,
                clang(p, "shop.supply.dmgTonic.name"), SUPPLY_TONIC_COST, coins,
                List.of(clang(p, "shop.supply.dmgTonic.lore", TONIC_STAT_AMOUNT)),
                ACTION_BUY_DMG_TONIC));
        inv.setItem(SUPPLY_DEF_TONIC_SLOT, supplyButton(p, Material.IRON_INGOT,
                clang(p, "shop.supply.defTonic.name"), SUPPLY_TONIC_COST, coins,
                List.of(clang(p, "shop.supply.defTonic.lore", TONIC_STAT_AMOUNT)),
                ACTION_BUY_DEF_TONIC));
    }

    /** A persistent-coins direct-purchase button for potions and other items. */
    private ItemStack makePotionBuyButton(Material mat, String name, int cost, Player p,
                                           List<String> extraLore, String action) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        int balance = prof.persistentCoins;
        String currency = clang(p, "shop.currency.persistent");
        List<String> lore = new ArrayList<>(extraLore);
        if (balance >= cost) {
            lore.add(clang(p, "shop.buy.afford", cost, currency));
            return makeButton(mat, name, lore, action);
        }
        lore.add("§8" + cost + " " + currency);
        lore.add("§c" + clang(p, "shop.buy.cant", cost - balance));
        return makeButton(Material.GRAY_DYE, "§8" + name.replaceFirst("§.", ""), lore, action);
    }

    /** A direct-purchase button; grayed out when the balance can't cover it. */
    private ItemStack supplyButton(Player p, Material mat, String name, int cost, int balance,
                                   List<String> extraLore, String action) {
        String currency = clang(p, "shop.currency.run");
        List<String> lore = new ArrayList<>(extraLore);
        if (balance >= cost) {
            lore.add(clang(p, "shop.buy.afford", cost, currency));
            return makeButton(mat, name, lore, action);
        }
        lore.add("§8" + cost + " " + currency);
        lore.add("§c" + clang(p, "shop.buy.cant", cost - balance));
        return makeButton(Material.GRAY_DYE, "§8" + name.replaceFirst("§.", ""), lore, action);
    }

    /** Handle a supplies purchase (currency checked + charged here). */
    private void handleSupplyPurchase(Player p, ShopSession s, String action) {
        DungeonInstance di = s.di != null ? s.di : plugin.game().instanceOf(p);
        PlayerState st = di == null ? null : di.playerStateOf(p);
        if (st == null) { p.closeInventory(); return; }
        // Stale-session guard: a run session is only valid while its own instance is still live.
        if (s.type == ShopType.RUN) {
            DungeonInstance live = plugin.game().instanceOf(p);
            if (live == null || live != s.di) { p.closeInventory(); return; }
        }

        int cost; Material icon; String label; Runnable effect;
        switch (action) {
            case ACTION_BUY_KEY -> { cost = SUPPLY_KEY_COST; icon = Material.TRIPWIRE_HOOK; label = clang(p, "shop.supply.key.name"); effect = () -> st.keys++; }
            case ACTION_BUY_BOMB -> { cost = SUPPLY_BOMB_COST; icon = Material.TNT; label = clang(p, "shop.supply.bomb.name"); effect = () -> st.bombs++; }
            case ACTION_BUY_HEART -> { cost = SUPPLY_HEART_COST; icon = Material.RED_DYE; label = clang(p, "shop.supply.heart.name"); effect = () -> st.heal(8); }
            case ACTION_BUY_MANA -> { cost = SUPPLY_MANA_COST; icon = Material.LAPIS_LAZULI; label = clang(p, "shop.supply.mana.name"); effect = () -> st.mana = st.maxMana; }
            case ACTION_BUY_DMG_TONIC -> { cost = SUPPLY_TONIC_COST; icon = Material.BLAZE_POWDER; label = clang(p, "shop.supply.dmgTonic.name"); effect = () -> st.tonicDamage += TONIC_STAT_AMOUNT; }
            case ACTION_BUY_DEF_TONIC -> { cost = SUPPLY_TONIC_COST; icon = Material.IRON_INGOT; label = clang(p, "shop.supply.defTonic.name"); effect = () -> st.tonicDefense += TONIC_STAT_AMOUNT; }
            default -> { return; }
        }
        if (st.coins < cost) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "shop.supply.needCoins", cost, label));
            return;
        }
        st.coins -= cost;
        effect.run();
        // Refresh the whole supplies view so balances/counts/affordances update.
        reopen(() -> openSupplies(p, s));
    }

    private void renderIdle(Player p, ShopSession s) {
        Inventory inv = s.inv;
        Category cat = s.transaction.category();
        inv.setItem(ROLL_SLOT, rollButton(p, s, cat));
        for (int i = 0; i < WINDOW_ITEM_SLOTS.length; i++) {
            inv.setItem(WINDOW_ITEM_SLOTS[i], i == WINDOW_CENTER ? categoryPreview(p, cat) : darkPane());
        }
        for (int slot : WINDOW_RARITY_SLOTS) {
            inv.setItem(slot, darkPane());
        }
    }

    private void renderRolling(Player p, ShopSession s) {
        Inventory inv = s.inv;
        inv.setItem(ROLL_SLOT, makeButton(Material.BARRIER, "§8" + clang(p, "shop.rolling"),
                List.of(clang(p, "shop.rolling")), null));
    }

    private void renderResult(Player p, ShopSession s) {
        ServerSideRollResult r = s.transaction.pending();
        if (r == null) return;
        Inventory inv = s.inv;
        // Freeze the roll rows on the final result until the player KEEPs or SALVAGES it. The
        // neighboring weapons/rarities that scrolled past stay visible, frozen beside the result.
        List<ItemStack> itemFrame = lastFrame(s.itemFrames);
        for (int i = 0; i < WINDOW_ITEM_SLOTS.length; i++) {
            if (i == WINDOW_CENTER) {
                inv.setItem(WINDOW_ITEM_SLOTS[i], r.item.clone());
            } else if (itemFrame != null && i < itemFrame.size()) {
                inv.setItem(WINDOW_ITEM_SLOTS[i], itemFrame.get(i));
            } else {
                inv.setItem(WINDOW_ITEM_SLOTS[i], darkPane());
            }
        }
        List<ItemStack> rarityFrame = lastFrame(s.rarityFrames);
        for (int i = 0; i < WINDOW_RARITY_SLOTS.length; i++) {
            if (i == WINDOW_CENTER) {
                inv.setItem(WINDOW_RARITY_SLOTS[i], rarityIcon(r.rarity));
            } else if (rarityFrame != null && i < rarityFrame.size()) {
                inv.setItem(WINDOW_RARITY_SLOTS[i], rarityFrame.get(i));
            } else {
                inv.setItem(WINDOW_RARITY_SLOTS[i], darkPane());
            }
        }
        inv.setItem(KEEP_SLOT, keepButton(p, s, r));
        inv.setItem(SALVAGE_SLOT, salvageButton(p, s, r));
    }

    /** Last frame of a slot-machine window (the frozen landing position), or null if absent. */
    private static List<ItemStack> lastFrame(List<List<ItemStack>> frames) {
        return frames == null || frames.isEmpty() ? null : frames.get(frames.size() - 1);
    }

    // ==================== TRANSACTIONS ====================

    private void handleRoll(Player p, ShopSession s) {
        if (!s.transaction.canRoll()) return; // never roll while an animation is active
        Category cat = s.transaction.category();
        if (cat == null) return;
        // Stale-session guard: a run session is only valid while its own instance is still live.
        if (s.type == ShopType.RUN) {
            DungeonInstance live = plugin.game().instanceOf(p);
            if (live == null || live != s.di) { p.closeInventory(); return; }
        }
        if (!s.transaction.startRoll(cat)) return; // guarded state machine: start roll before charging
        int cost = ShopRules.costFor(s.type, cat);

        if (s.type == ShopType.RUN) {
            DungeonInstance di = plugin.game().instanceOf(p);
            if (di == null) {
                s.transaction.reset();
                p.closeInventory();
                return;
            }
            s.di = di;
            PlayerState st = di.playerStateOf(p);
            if (st == null) {
                s.transaction.reset();
                p.closeInventory();
                return;
            }
            if (st.coins < cost) {
                s.transaction.reset();
                p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "shop.roll.needCoins", cost, cat.articleLabel()));
                return;
            }
            st.coins -= cost; // charged exactly once
        } else {
            MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
            if (prof.persistentCoins < cost) {
                s.transaction.reset();
                p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "shop.roll.needPersistent", cost, cat.articleLabel()));
                return;
            }
            prof.persistentCoins -= cost;
            plugin.meta().save();
        }

        // Server-authoritative result: generated here, before any animation starts. The armor
        // trim (which encodes rarity) is applied ONLY to this finalized result — the rolling
        // decoys stay trimless so the rarity isn't leaked before it lands.
        int floor = s.type == ShopType.RUN ? (s.di == null ? 0 : s.di.run().floorIndex) : 1;
        ItemStack item = generateItem(p, cat, floor, s.type);
        // Keep a TRIMLESS neutral copy for the animations: the item window lands on it while the
        // rarity is still rolling, so the trimmed final item must not be shown until RESULT.
        s.rollNeutralIcon = neutralIcon(item);
        GearFactory.finalizeRarityLook(item);
        Rarity rarity = GearFactory.getRarity(item);
        int salvage = ShopRules.salvageValue(rarity, WorkstationRules.primaryStat(item));
        ServerSideRollResult result = new ServerSideRollResult(item, rarity, cat, salvage);
        s.transaction.attachResult(result);
        if (s.type == ShopType.PERSISTENT) {
            pendingStore.put(p.getUniqueId(), result); // persist so it can never be lost
        }
        buildWindows(s); // precompute the frames the animations + frozen result screen will show

        renderRolling(p, s);
        startItemAnimation(p, s);
    }

    private void handleKeep(Player p, ShopSession s) {
        if (!s.transaction.canChoose()) return; // cannot KEEP twice, or in an invalid state
        ServerSideRollResult r = s.transaction.pending();
        if (r == null) return;
        // Stale-session guard: a run session is only valid while its own instance is still live.
        if (s.type == ShopType.RUN) {
            DungeonInstance live = plugin.game().instanceOf(p);
            if (live == null || live != s.di) { p.closeInventory(); return; }
        }
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(r.item.clone());
        if (!leftover.isEmpty()) {
            // Full inventory: the item is never lost. Keep it pending and let the player retry
            // (or choose SALVAGE instead) after freeing a slot.
            s.transaction.markKeepPending();
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "shop.fullInventory"));
            renderResult(p, s);
            return;
        }
        if (s.type == ShopType.PERSISTENT) pendingStore.remove(p.getUniqueId());
        s.transaction.reset();
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "shop.kept", r.rarity.legacy + r.category.articleLabel()));
        refresh(p, s);
    }

    private void handleSalvage(Player p, ShopSession s) {
        if (!s.transaction.canChoose()) return; // cannot SALVAGE twice, or in an invalid state
        ServerSideRollResult r = s.transaction.pending();
        if (r == null) return;
        // Stale-session guard: a run session is only valid while its own instance is still live.
        if (s.type == ShopType.RUN) {
            DungeonInstance live = plugin.game().instanceOf(p);
            if (live == null || live != s.di) { p.closeInventory(); return; }
        }
        DungeonInstance di = s.type == ShopType.RUN ? s.di : null;
        if (di == null) di = plugin.game().instanceOf(p);
        if (s.type == ShopType.RUN && di != null) {
            // In-run shards are banked to the persistent balance on boss defeat (existing behavior).
            di.run().salvageShards.merge(p.getUniqueId(), r.salvageValue, Integer::sum);
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "shop.salvageBanked", r.salvageValue));
        } else {
            MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
            prof.shards += r.salvageValue;
            plugin.meta().save();
            if (s.type == ShopType.PERSISTENT) pendingStore.remove(p.getUniqueId());
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "shop.salvage", r.salvageValue));
        }
        s.transaction.reset();
        refresh(p, s);
    }

    // ==================== ITEM / RARITY ANIMATIONS ====================

    /** Precompute the slot-machine strips for the current roll so the animations and the frozen
     *  result screen share the exact same frames. The final frame of each window keeps the other
     *  weapons/rarities that surrounded the result visible after the roll lands. */
    private void buildWindows(ShopSession s) {
        Category cat = s.transaction.category();
        int floor = s.type == ShopType.RUN ? (s.di == null ? 0 : s.di.run().floorIndex) : 1;
        List<ItemStack> itemDecoys = new ArrayList<>();
        for (int i = 0; i < 8; i++) itemDecoys.add(generateIcon(cat, floor));
        ItemStack resultIcon = s.rollNeutralIcon != null ? s.rollNeutralIcon
                : neutralIcon(s.transaction.pending().item);
        s.itemFrames = RollAnimationMath.frames(
                RollAnimationMath.buildStrip(itemDecoys, resultIcon, ITEM_STEPS));
        Rarity result = s.transaction.pending().rarity;
        List<Rarity> rarityDecoys = Arrays.asList(Rarity.values());
        s.rarityFrames = new ArrayList<>();
        for (List<Rarity> frame : RollAnimationMath.frames(
                RollAnimationMath.buildStrip(rarityDecoys, result, RARITY_STEPS))) {
            List<ItemStack> icons = new ArrayList<>();
            for (Rarity r : frame) icons.add(rarityIcon(r));
            s.rarityFrames.add(icons);
        }
    }

    private void startItemAnimation(Player p, ShopSession s) {
        int[] delays = RollAnimationMath.tickDelays(ITEM_STEPS);
        playFrames(p, s, s.itemFrames, delays, WINDOW_ITEM_SLOTS, () -> {
            s.transaction.itemAnimationFinished();
            startRarityAnimation(p, s);
        });
    }

    private void startRarityAnimation(Player p, ShopSession s) {
        // Keep the finished item visible in the item row while the rarity rolls beneath it —
        // as its TRIMLESS neutral icon, since the trim would leak the still-rolling rarity.
        ItemStack item = s.rollNeutralIcon != null ? s.rollNeutralIcon.clone()
                : neutralIcon(s.transaction.pending().item);
        for (int i = 0; i < WINDOW_ITEM_SLOTS.length; i++) {
            s.inv.setItem(WINDOW_ITEM_SLOTS[i], i == WINDOW_CENTER ? item : darkPane());
        }
        int[] delays = RollAnimationMath.tickDelays(RARITY_STEPS);
        playFrames(p, s, s.rarityFrames, delays, WINDOW_RARITY_SLOTS, () -> {
            s.transaction.rarityAnimationFinished();
            if (s.transaction.canChoose()) renderResult(p, s);
        });
    }

    private void playFrames(Player p, ShopSession s, List<List<ItemStack>> frames, int[] delays,
                            int[] slots, Runnable onDone) {
        if (frames.isEmpty()) {
            onDone.run();
            return;
        }
        applyFrame(s.inv, slots, frames.get(0)); // start moving immediately
        scheduleFrame(p, s, frames, delays, 1, slots, onDone);
    }

    private void scheduleFrame(Player p, ShopSession s, List<List<ItemStack>> frames, int[] delays,
                               int idx, int[] slots, Runnable onDone) {
        if (idx >= frames.size()) {
            onDone.run();
            return;
        }
        int delay = delays[Math.min(idx, delays.length - 1)];
        s.animationTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // If the player closed the GUI or disconnected, skip painting but keep advancing the
            // state machine so the already-determined result is never stranded mid-animation.
            if (p.isOnline() && p.getOpenInventory().getTopInventory() == s.inv) {
                applyFrame(s.inv, slots, frames.get(idx));
            }
            scheduleFrame(p, s, frames, delays, idx + 1, slots, onDone);
        }, delay);
    }

    private void applyFrame(Inventory inv, int[] slots, List<ItemStack> frame) {
        for (int i = 0; i < slots.length; i++) {
            inv.setItem(slots[i], i < frame.size() ? frame.get(i) : darkPane());
        }
    }

    private ItemStack generateItem(Player p, Category cat, int floor, ShopType type) {
        ItemStack item = switch (cat) {
            case WEAPON -> ItemPool.randomWeapon(floor);
            case ARMOR -> ItemPool.randomArmor(floor, ThreadLocalRandom.current().nextInt(4));
            case MANA_SHIELD -> ItemPool.randomShield(floor);
        };
        if (type == ShopType.PERSISTENT) {
            item = GearFactory.markPersistent(item);
            GearFactory.initDurability(item);
        }
        GearFactory.localizeFor(item, p);
        return item;
    }

    /** A decorative neutral icon for the item animation (material + white base name). */
    private ItemStack generateIcon(Category cat, int floor) {
        ItemStack item = switch (cat) {
            case WEAPON -> ItemPool.randomWeapon(floor);
            case ARMOR -> ItemPool.randomArmor(floor, ThreadLocalRandom.current().nextInt(4));
            case MANA_SHIELD -> ItemPool.randomShield(floor);
        };
        return neutralIcon(item);
    }

    private ItemStack neutralIcon(ItemStack item) {
        ItemStack icon = item.clone();
        String base = baseName(item);
        icon.editMeta(meta -> {
            meta.displayName(LEGACY.deserialize("§f" + base));
            meta.lore(List.of(LEGACY.deserialize("§7Rolling...")));
        });
        return icon;
    }

    private static String baseName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = LegacyComponentSerializer.legacySection().serialize(meta.displayName());
            name = name.replaceAll("§[0-9a-fk-or]", "");
            name = name.replace("★ ", "").replace("✦ ", "");
            return name.trim();
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    private ItemStack rarityIcon(Rarity r) {
        Material pane = switch (r) {
            case COMMON -> Material.GRAY_STAINED_GLASS_PANE;
            case UNCOMMON -> Material.GREEN_STAINED_GLASS_PANE;
            case RARE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case EPIC -> Material.PURPLE_STAINED_GLASS_PANE;
            case LEGENDARY -> Material.ORANGE_STAINED_GLASS_PANE;
            case MYTHIC -> Material.RED_STAINED_GLASS_PANE;
        };
        ItemStack icon = new ItemStack(pane);
        icon.editMeta(meta -> meta.displayName(LEGACY.deserialize(r.legacy + r.name())));
        return icon;
    }

    // ==================== ITEM BUILDERS ====================

    /** A main-menu entry for one roll category. */
    private ItemStack menuEntry(Player p, ShopSession s, Category cat, String action) {
        int cost = ShopRules.costFor(s.type, cat);
        String currency = s.type == ShopType.RUN
                ? clang(p, "shop.currency.run.short") : clang(p, "shop.currency.persistent.short");
        return makeButton(cat.icon(), "§f" + catLabel(p, cat),
                List.of(clang(p, "menuEntry.rollFor", catArticleLabel(p, cat)),
                        "§e" + cost + " " + currency + "§7 " + clang(p, "shop.roll.pricePer"),
                        clang(p, "menuEntry.enter", catLabel(p, cat))),
                action);
    }

    /** Current spendable balance of the shop's currency for this player, or -1 if unavailable. */
    private int balanceOf(Player p, ShopSession s) {
        if (s.type == ShopType.RUN) {
            DungeonInstance di = s.di != null ? s.di : plugin.game().instanceOf(p);
            PlayerState st = di == null ? null : di.playerStateOf(p);
            return st == null ? -1 : st.coins;
        }
        return plugin.meta().profile(s.player).persistentCoins;
    }

    /** ROLL button: the single source of cost + rules info (no separate cost/status panes). */
    private ItemStack rollButton(Player p, ShopSession s, Category cat) {
        int cost = ShopRules.costFor(s.type, cat == null ? Category.WEAPON : cat);
        String currency = s.type == ShopType.RUN
                ? clang(p, "shop.currency.run.short") : clang(p, "shop.currency.persistent.short");
        String price = cost + " " + currency;
        String what = cat == null ? clang(p, "shop.roll.anItem") : catArticleLabel(p, cat);
        int balance = p == null ? -1 : balanceOf(p, s);
        if (balance >= 0 && balance < cost) {
            // Affordance: visually locked BEFORE the click instead of a red chat line after it.
            return makeButton(Material.GRAY_DYE,
                    clang(p, "shop.roll.buttonDisabled", price),
                    List.of(clang(p, "shop.roll.cantAfford", cost - balance),
                            clang(p, "shop.roll.why", what)),
                    ACTION_ROLL);
        }
        List<String> lore = new ArrayList<>(List.of(clang(p, "shop.roll.why", what)));
        if (s.type == ShopType.RUN) {
            lore.add(clang(p, "shop.roll.runCoinsLost"));
        } else {
            lore.add(clang(p, "shop.roll.persistentBase"));
        }
        return makeButton(Material.ANVIL, clang(p, "shop.roll.button", price), lore, ACTION_ROLL);
    }

    private ItemStack categoryPreview(Player p, Category cat) {
        if (cat == null) return darkPane();
        return makeButton(cat.icon(), "§f" + catLabel(p, cat),
                List.of(clang(p, "shop.categoryPreview.ready")), null);
    }

    private ItemStack keepButton(Player p, ShopSession s, ServerSideRollResult r) {
        return makeButton(Material.EMERALD_BLOCK, clang(p, "shop.keep.name"),
                List.of(clang(p, "shop.keep.lore", r.rarity.legacy + catArticleLabel(p, r.category))),
                ACTION_KEEP);
    }

    private ItemStack salvageButton(Player p, ShopSession s, ServerSideRollResult r) {
        return makeButton(Material.REDSTONE_BLOCK, clang(p, "shop.salvage.name"),
                List.of(clang(p, "shop.salvage.lore", r.salvageValue)),
                ACTION_SALVAGE);
    }

    private ItemStack darkPane() {
        return makeButton(Material.BLACK_STAINED_GLASS_PANE, "§8", List.of("§7"), null);
    }

    private ItemStack makeButton(Material mat, String name, List<String> lore, String action) {
        ItemStack s = new ItemStack(mat);
        s.editMeta(meta -> {
            meta.displayName(LEGACY.deserialize(name));
            List<Component> comps = new ArrayList<>();
            for (String line : lore) comps.add(LEGACY.deserialize(line));
            meta.lore(comps);
            if (action != null) {
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
            }
        });
        return s;
    }

    private String actionOf(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.STRING);
    }

    private void fillEmpty(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.text("")));
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }
    }

    // ==================== LISTENER ====================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // Upgrades GUI (separate 27-slot inventory, reached from the persistent shop or /upgrades).
        if (openUpgradeGuis.containsKey(e.getInventory())) {
            e.setCancelled(true);
            String action = actionOf(e.getCurrentItem());
            if (action == null) return;
            handleUpgradesClick(p, action);
            return;
        }
        // Main shop menu.
        UUID menuOwner = openMenuGuis.get(e.getInventory());
        if (menuOwner != null) {
            e.setCancelled(true);
            ShopSession ms = sessions.get(menuOwner);
            if (ms == null) return;
            String menuAction = actionOf(e.getCurrentItem());
            if (menuAction == null || menuAction.isEmpty()) return;
            switch (menuAction) {
                case ACTION_MENU_WEAPON -> reopen(() -> openRoll(p, ms, Category.WEAPON));
                case ACTION_MENU_ARMOR -> reopen(() -> openRoll(p, ms, Category.ARMOR));
                case ACTION_MENU_SHIELD -> reopen(() -> openRoll(p, ms, Category.MANA_SHIELD));
                case ACTION_MENU_SUPPLIES -> reopen(() -> openSupplies(p, ms));
                case ACTION_REPAIR -> repairHeld(p, ms);
                case ACTION_REPAIR_ALL -> repairAll(p, ms);
                case ACTION_REPAIR_BROKEN -> repairBroken(p, ms);
                case ACTION_UPGRADES -> reopen(() -> openUpgrades(p));
                case ACTION_BUY_FOREST_POTION -> handlePotionPurchase(p, ms, PotionDefinition.FOREST, POTION_FOREST_COST);
                case ACTION_BUY_STONE_POTION -> handlePotionPurchase(p, ms, PotionDefinition.STONE, POTION_STONE_COST);
            }
            return;
        }
        // Supplies GUI (in-run shop): direct purchases.
        UUID supplyOwner = openSupplyGuis.get(e.getInventory());
        if (supplyOwner != null) {
            e.setCancelled(true);
            ShopSession ss = sessions.get(supplyOwner);
            if (ss == null) return;
            String supplyAction = actionOf(e.getCurrentItem());
            if (supplyAction == null || supplyAction.isEmpty()) return;
            switch (supplyAction) {
                case ACTION_BACK -> reopen(() -> open(p, ss));
                default -> handleSupplyPurchase(p, ss, supplyAction);
            }
            return;
        }
        // Per-category roll GUI.
        UUID owner = openGuis.get(e.getInventory());
        if (owner == null) return;
        // Shop GUI slots: always cancel so items can never be taken, placed, or swapped.
        if (e.getClickedInventory() == e.getInventory()) {
            e.setCancelled(true);
        } else if (e.isShiftClick()) {
            e.setCancelled(true); // block shift-clicks that would move items into the shop
        } else {
            // Normal clicks inside the player's own inventory are allowed so they can free space
            // before choosing KEEP; they never touch the shop's contents.
            return;
        }
        String action = actionOf(e.getCurrentItem());
        if (action == null || action.isEmpty()) return;
        ShopSession s = sessions.get(owner);
        if (s == null) return;
        switch (action) {
            case ACTION_BACK -> {
                // Only leave a roll GUI while idle — a paid-for result must be resolved first
                // (persistent results also survive in the disk-backed pending store).
                if (s.transaction.canRoll()) reopen(() -> open(p, s));
            }
            case ACTION_ROLL -> handleRoll(p, s);
            case ACTION_KEEP -> handleKeep(p, s);
            case ACTION_SALVAGE -> handleSalvage(p, s);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (openGuis.containsKey(e.getInventory()) || openMenuGuis.containsKey(e.getInventory())
                || openSupplyGuis.containsKey(e.getInventory())
                || openUpgradeGuis.containsKey(e.getInventory())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        openGuis.remove(e.getInventory());
        openMenuGuis.remove(e.getInventory());
        openSupplyGuis.remove(e.getInventory());
        openUpgradeGuis.remove(e.getInventory());
        // The session is kept in memory so a pending result survives the GUI closing.
    }

    // ==================== UPGRADES GUI ====================

    /** Open the upgrades GUI where players spend shards on permanent stat upgrades. */
    public void openUpgrades(Player p) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, UPGRADES_SIZE,
                LEGACY.deserialize(clang(p, "shop.upgrades.title", prof.shards)));
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
            // Condensed lore: level + effect on one line, cost/affordance on the next.
            List<String> lore = new ArrayList<>();
            lore.add(clang(p, "upg.lvLine", owned, t.maxLevel(), effectDesc(p, t, owned)));
            if (maxed) {
                // Locked affordance: a MAXED icon with no action, instead of a clickable button
                // that only fails in chat.
                inv.setItem(i, makeButton(Material.BARRIER,
                        clang(p, "upg.maxedBtn", upgLabel(p, t)), lore, null));
                continue;
            }
            boolean affordable = prof.shards >= cost;
            if (affordable) {
                lore.add(clang(p, "upg.clickUpgrade", cost));
            } else {
                lore.add(clang(p, "upg.needMore", cost - prof.shards));
            }
            String name = (affordable ? "§f" : "§8") + upgLabel(p, t);
            inv.setItem(i, makeButton(affordable ? mat : Material.GRAY_DYE, name, lore, t.id()));
        }
        inv.setItem(22, makeButton(Material.ARROW, clang(p, "upg.back.name"),
                List.of(clang(p, "upg.back.lore")), "back"));
        fillEmpty(inv);
        openUpgradeGuis.put(inv, p.getUniqueId());
        p.openInventory(inv);
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
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "upg.maxed"));
            return;
        }
        int cost = Upgrades.cost(t, owned);
        if (prof.shards < cost) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "upg.needShards", cost, prof.shards));
            return;
        }
        prof.shards -= cost;
        prof.upgrades.put(t.id(), owned + 1);
        plugin.meta().save();
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "upg.levelled", t.label(), owned + 1));
        reopen(() -> openUpgrades(p));
    }

    // ==================== REPAIR (persistent shop utility) ====================

    /** Cost to restore the next 10 durability of a persistent item (scales with its repair count). */
    private static int repairCost(ItemStack it) {
        int d = GearFactory.getDurability(it);
        int m = GearFactory.getMaxDurability(it);
        int repairAmt = Math.min(m - d, 10);
        int costMult = 1 + GearFactory.getRepairCount(it);
        int cost = (repairAmt / 10) * REPAIR_COST_PER_10 * costMult;
        if (repairAmt % 10 != 0) cost += REPAIR_COST_PER_10 * costMult; // round up
        return cost;
    }

    /** All damaged (but not broken) persistent gear across main inventory + armor slots. */
    private static List<ItemStack> damagedPersistentGear(Player p) {
        List<ItemStack> damaged = new ArrayList<>();
        List<ItemStack> candidates = new ArrayList<>();
        for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
            ItemStack it = p.getInventory().getItem(slot);
            if (it != null && it.getType() != Material.AIR) candidates.add(it);
        }
        for (org.bukkit.inventory.EquipmentSlot slot : List.of(
                org.bukkit.inventory.EquipmentSlot.HEAD,
                org.bukkit.inventory.EquipmentSlot.CHEST,
                org.bukkit.inventory.EquipmentSlot.LEGS,
                org.bukkit.inventory.EquipmentSlot.FEET)) {
            ItemStack it = p.getInventory().getItem(slot);
            if (it != null && it.getType() != Material.AIR) candidates.add(it);
        }
        for (ItemStack it : candidates) {
            int d = GearFactory.getDurability(it);
            int m = GearFactory.getMaxDurability(it);
            if (d > 0 && m > 0 && d < m) damaged.add(it);
        }
        return damaged;
    }

    private void repairHeld(Player p, ShopSession s) {
        ItemStack target = p.getInventory().getItemInMainHand();
        if (target == null || target.getType() == Material.AIR || !GearFactory.isPersistent(target)) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "repair.holdDamaged"));
            return;
        }
        int dur = GearFactory.getDurability(target);
        int max = GearFactory.getMaxDurability(target);
        if (dur <= 0 || max <= 0 || dur >= max) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "repair.notDamaged"));
            return;
        }
        int repairCount = GearFactory.getRepairCount(target);
        int cost = repairCost(target);
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        if (prof.persistentCoins < cost) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "repair.needCoins", cost, repairCount + 1));
            return;
        }
        prof.persistentCoins -= cost;
        GearFactory.repairItem(target, Math.min(max - dur, 10));
        GearFactory.setRepairCount(target, repairCount + 1);
        plugin.meta().save();
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "repair.done", cost, repairCount + 1));
        refresh(p, s);
    }

    private void repairAll(Player p, ShopSession s) {
        List<ItemStack> toRepair = damagedPersistentGear(p);
        if (toRepair.isEmpty()) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "repair.noneDamaged"));
            return;
        }
        int totalCost = 0;
        for (ItemStack it : toRepair) totalCost += repairCost(it);
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        if (prof.persistentCoins < totalCost) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "repair.needAll", totalCost));
            return;
        }
        prof.persistentCoins -= totalCost;
        for (ItemStack it : toRepair) {
            int d = GearFactory.getDurability(it);
            int m = GearFactory.getMaxDurability(it);
            GearFactory.repairItem(it, Math.min(m - d, 10));
            GearFactory.setRepairCount(it, GearFactory.getRepairCount(it) + 1);
        }
        plugin.meta().save();
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "repair.allDone", toRepair.size(), totalCost));
        refresh(p, s);
    }

    private void repairBroken(Player p, ShopSession s) {
        ItemStack target = p.getInventory().getItemInMainHand();
        if (target == null || target.getType() == Material.AIR
                || !GearFactory.isPersistent(target) || !GearFactory.isBroken(target)) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "repair.holdBroken"));
            return;
        }
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        if (prof.persistentCoins < REPAIR_BROKEN_COINS) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "repair.needBrokenCoins", REPAIR_BROKEN_COINS));
            return;
        }
        if (prof.shards < REPAIR_BROKEN_SHARDS) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "repair.needBrokenShards", REPAIR_BROKEN_SHARDS));
            return;
        }
        prof.persistentCoins -= REPAIR_BROKEN_COINS;
        prof.shards -= REPAIR_BROKEN_SHARDS;
        GearFactory.repairItem(target, 10);
        plugin.meta().save();
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "repair.brokenDone", REPAIR_BROKEN_COINS, REPAIR_BROKEN_SHARDS));
        refresh(p, s);
    }

    /** Handle a potion purchase from the persistent shop. */
    private void handlePotionPurchase(Player p, ShopSession s, PotionDefinition def, int cost) {
        if (s.type != ShopType.PERSISTENT) {
            p.closeInventory();
            return;
        }
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        if (prof.persistentCoins < cost) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "shop.needPersistentFor", cost, def.displayName()));
            return;
        }
        prof.persistentCoins -= cost;
        plugin.meta().save();
        ItemStack potion = PotionFactory.createPotion(def);
        StashUI.placeOrStash(p, potion);
        p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "shop.purchased", def.displayName(), cost));
        refresh(p, s);
    }

    private ItemStack makeRepairItemButton(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        List<String> lore = new ArrayList<>();
        lore.add(clang(p, "shop.repairItem.lore1"));
        if (held != null && held.getType() != Material.AIR && GearFactory.isPersistent(held)) {
            int dur = GearFactory.getDurability(held);
            int max = GearFactory.getMaxDurability(held);
            if (dur > 0 && max > 0 && dur < max) {
                String name = held.getItemMeta().getDisplayName();
                lore.add("");
                lore.add(clang(p, "shop.repairItem.held",
                        name == null ? held.getType().name().toLowerCase() : name));
                lore.add(clang(p, "shop.repairItem.durability", dur, max));
                lore.add(clang(p, "shop.repairItem.repairNum",
                        GearFactory.getRepairCount(held) + 1, repairCost(held)));
            } else if (dur <= 0) {
                lore.add("");
                lore.add(clang(p, "shop.repairItem.broken"));
            } else {
                lore.add("");
                lore.add(clang(p, "shop.repairItem.fullDura"));
            }
        } else {
            lore.add("");
            lore.add(clang(p, "shop.repairItem.holdDamaged1"));
            lore.add(clang(p, "shop.repairItem.holdDamaged2"));
        }
        return makeButton(Material.ANVIL, clang(p, "shop.repairItem.name"), lore, ACTION_REPAIR);
    }

    private ItemStack makeRepairAllButton(Player p) {
        List<String> lore = new ArrayList<>();
        lore.add(clang(p, "shop.repairAll.lore1"));
        List<ItemStack> damaged = damagedPersistentGear(p);
        int totalCost = 0;
        for (ItemStack it : damaged) totalCost += repairCost(it);
        lore.add("");
        if (!damaged.isEmpty()) {
            lore.add(clang(p, "shop.repairAll.items", damaged.size()));
            lore.add(clang(p, "shop.repairAll.total", totalCost));
        } else {
            lore.add(clang(p, "shop.repairAll.none"));
        }
        return makeButton(Material.DIAMOND, clang(p, "shop.repairAll.name"), lore, ACTION_REPAIR_ALL);
    }

    // ==================== HELPERS ====================

    private String effectDesc(Player p, Upgrades.Track t, int level) {
        return switch (t.id()) {
            case "damage" -> clang(p, "upg.effect.damage", level * Upgrades.delta(t));
            case "magic_damage" -> clang(p, "upg.effect.magic_damage", level * Upgrades.delta(t));
            case "hearts" -> clang(p, "upg.effect.hearts", level * Upgrades.delta(t));
            case "defense" -> clang(p, "upg.effect.defense", level * Upgrades.delta(t));
            case "crit" -> clang(p, "upg.effect.crit", level * Upgrades.CRIT_DELTA_PCT);
            case "speed" -> clang(p, "upg.effect.speed", level * Upgrades.delta(t));
            case "mana" -> clang(p, "upg.effect.mana", level * Upgrades.delta(t));
            default -> "";
        };
    }

    private static String clang(Player p, String key, Object... args) {
        return com.lieyabull.dung.lang.Lang.forPlayer(p, key, args);
    }

    /** Localized label of a roll category (used in menus/titles/buttons). */
    private static String catLabel(Player p, Category cat) {
        return switch (cat) {
            case WEAPON -> clang(p, "shop.category.weapon");
            case ARMOR -> clang(p, "shop.category.armor");
            case MANA_SHIELD -> clang(p, "shop.category.manashield");
        };
    }

    /** Localized "a/an <category>" (accusative for HU) used in roll/keep phrasing. */
    private static String catArticleLabel(Player p, Category cat) {
        return switch (cat) {
            case WEAPON -> clang(p, "shop.category.weapon.article");
            case ARMOR -> clang(p, "shop.category.armor.article");
            case MANA_SHIELD -> clang(p, "shop.category.manashield.article");
        };
    }

    /** Localized label of a permanent upgrade track. */
    private static String upgLabel(Player p, Upgrades.Track t) {
        return clang(p, "upg.label." + t.id());
    }

    /** Defer a GUI open by one tick so the InventoryCloseEvent of the previous GUI doesn't untrack it. */
    private void reopen(Runnable open) {
        Bukkit.getScheduler().runTask(plugin, open);
    }

    private static final class ShopSession {
        final UUID player;
        final ShopTransaction transaction;
        ShopType type;
        DungeonInstance di;
        Inventory inv;
        BukkitTask animationTask;
        // Frozen final frames of the two slot-machine windows (item row, rarity row). Once the roll
        // lands, the result screen keeps the neighboring items/rarities that surrounded the result
        // visible until the player chooses KEEP or SALVAGE.
        List<List<ItemStack>> itemFrames;
        List<List<ItemStack>> rarityFrames;
        /** Trimless neutral copy of the rolled item, safe to show while the rarity is still rolling. */
        ItemStack rollNeutralIcon;

        ShopSession(UUID player, ShopType type) {
            this.player = player;
            this.transaction = new ShopTransaction(type);
            this.type = type;
        }
    }
}