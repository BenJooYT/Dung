package com.lieyabull.dung.ui;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.items.Affix;
import com.lieyabull.dung.items.GiveCatalog;
import com.lieyabull.dung.items.GiveItemFactory;
import com.lieyabull.dung.items.GiveSpec;
import com.lieyabull.dung.items.Rarity;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Admin gear builder for {@code /dung give}. The menu is intentionally self-contained: every
 * gear choice, rarity, upgrade, affix, persistence flag, and target is visible before the
 * item is built. The actual grant rebuilds from the spec so persistent UUIDs are never reused.
 */
public final class GiveUI implements Listener {

    private static final int ROOT_SIZE = 27;
    private static final int LIST_SIZE = 54;
    private static final int BUILDER_SIZE = 54;
    private static final int TARGET_SIZE = 54;
    private static final int UTILITY_SIZE = 27;
    private static final int PAGE_SIZE = 45;

    private static final String GUI_KEY = "dung_give";

    private static final String ACTION_WEAPONS = "weapons";
    private static final String ACTION_ARMOR = "armor";
    private static final String ACTION_SHIELDS = "shields";
    private static final String ACTION_UTILITY = "utility";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_PREV = "prev";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_PICK = "pick";
    private static final String ACTION_RARITY = "rarity";
    private static final String ACTION_UPGRADE = "upgrade";
    private static final String ACTION_AFFIX = "affix";
    private static final String ACTION_TARGET = "target";
    private static final String ACTION_TARGET_PICK = "target_pick";
    private static final String ACTION_PERSISTENT = "persistent";
    private static final String ACTION_GIVE = "give";
    private static final String ACTION_HEAL = "heal";
    private static final String ACTION_RUN_COINS = "run_coins";
    private static final String ACTION_PERSISTENT_COINS = "persistent_coins";

    private static final int[] RARITY_SLOTS = {18, 19, 20, 21, 22, 23, 24};
    private static final int[] UPGRADE_SLOTS = {27, 28, 29, 30, 31, 32};
    private static final int AFFIX_NONE_SLOT = 36;
    private static final int AFFIX_RANDOM_SLOT = 37;
    private static final int AFFIX_START_SLOT = 38;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private enum Screen { ROOT, LIST, BUILDER, TARGETS, UTILITY }

    private static final class State {
        final UUID admin;
        Screen screen = Screen.ROOT;
        Screen targetReturn = Screen.ROOT;
        GiveSpec.Category category;
        GiveSpec spec;
        UUID target;
        String targetName = "";
        int page;

        State(Player admin) {
            this.admin = admin.getUniqueId();
            this.target = admin.getUniqueId();
            this.targetName = admin.getName();
        }
    }

    private final Dung plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey argKey;
    private final Map<Inventory, State> states = new ConcurrentHashMap<>();

    public GiveUI(Dung plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, GUI_KEY);
        this.argKey = new NamespacedKey(plugin, GUI_KEY + "_arg");
    }

    /** Open the builder root for the sender, targeting themselves. */
    public void open(Player admin) {
        if (!admin.hasPermission("dung.admin")) {
            admin.sendMessage("§cNo permission.");
            return;
        }
        State state = new State(admin);
        render(state);
    }

    /** Open the builder root with an explicit initial target. */
    public void open(Player admin, Player target) {
        if (!admin.hasPermission("dung.admin")) {
            admin.sendMessage("§cNo permission.");
            return;
        }
        State state = new State(admin);
        state.target = target.getUniqueId();
        state.targetName = target.getName();
        render(state);
    }

    // ==================== RENDERING ====================

    private void render(State state) {
        Player admin = Bukkit.getPlayer(state.admin);
        if (admin == null) return;
        if (!admin.hasPermission("dung.admin")) {
            admin.closeInventory();
            admin.sendMessage("§cNo permission.");
            return;
        }
        Inventory inv = switch (state.screen) {
            case ROOT -> renderRoot(state);
            case LIST -> renderList(state);
            case BUILDER -> renderBuilder(state);
            case TARGETS -> renderTargets(state);
            case UTILITY -> renderUtility(state);
        };
        states.put(inv, state);
        admin.openInventory(inv);
    }

    private Inventory renderRoot(State state) {
        Inventory inv = Bukkit.createInventory(null, ROOT_SIZE, LEGACY.deserialize("§8Give — Admin"));
        inv.setItem(10, makeButton(Material.IRON_SWORD, "§fWeapons",
                List.of("§7Build a weapon with an exact rarity,", "§7upgrade level, and reforge set.",
                        "", "§8Target: §f" + state.targetName),
                ACTION_WEAPONS, null));
        inv.setItem(12, makeButton(Material.IRON_CHESTPLATE, "§fArmor",
                List.of("§7Every set and slot, including", "§7the Emberguard endgame branch.",
                        "", "§8Target: §f" + state.targetName),
                ACTION_ARMOR, null));
        inv.setItem(14, makeButton(Material.SHIELD, "§fShields",
                List.of("§7Build a mana shield with an", "§7exact rarity and upgrade level.",
                        "", "§8Target: §f" + state.targetName),
                ACTION_SHIELDS, null));
        inv.setItem(16, makeButton(Material.GOLD_INGOT, "§fUtility",
                List.of("§7Heal the target or grant", "§7run / persistent coins.",
                        "", "§8Target: §f" + state.targetName),
                ACTION_UTILITY, null));
        inv.setItem(22, makeButton(Material.BARRIER, "§cClose", List.of(), ACTION_CLOSE, null));
        fillEmpty(inv);
        return inv;
    }

    private Inventory renderList(State state) {
        if (state.category == null) state.category = GiveSpec.Category.WEAPON;
        String title = switch (state.category) {
            case WEAPON -> "§8Give — Weapons";
            case ARMOR -> "§8Give — Armor";
            case SHIELD -> "§8Give — Shields";
        };
        Inventory inv = Bukkit.createInventory(null, LIST_SIZE, LEGACY.deserialize(title));

        List<Entry> entries = entriesFor(state.category);
        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        state.page = Math.max(0, Math.min(state.page, totalPages - 1));
        int from = state.page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Entry entry = entries.get(i);
            inv.setItem(i - from, makeButton(entry.icon, entry.name, entry.lore,
                    ACTION_PICK, entry.key));
        }

        inv.setItem(45, makeButton(Material.ARROW, "§f← Back", List.of("§7Categories"), ACTION_BACK, null));
        inv.setItem(49, makeButton(Material.PAPER, "§7Page " + (state.page + 1) + " / " + totalPages,
                List.of("§8" + entries.size() + " entries"), null, null));
        if (state.page > 0) {
            inv.setItem(46, makeButton(Material.ARROW, "§f← Previous",
                    List.of("§7Page " + state.page), ACTION_PREV, null));
        }
        if (state.page < totalPages - 1) {
            inv.setItem(52, makeButton(Material.ARROW, "§fNext →",
                    List.of("§7Page " + (state.page + 2)), ACTION_NEXT, null));
        }
        inv.setItem(53, makeButton(Material.BARRIER, "§cClose", List.of(), ACTION_CLOSE, null));
        fillEmpty(inv);
        return inv;
    }

    private Inventory renderBuilder(State state) {
        GiveSpec spec = state.spec;
        if (spec == null) {
            state.screen = Screen.LIST;
            return renderList(state);
        }
        Inventory inv = Bukkit.createInventory(null, BUILDER_SIZE, LEGACY.deserialize("§8Give — Builder"));
        Player recipient = Bukkit.getPlayer(state.target);

        // Live preview. Build is deterministic from the spec seed, so changing a setting updates
        // exactly the item that Give will produce (persistent UUIDs are regenerated on grant).
        inv.setItem(13, GiveItemFactory.build(spec, recipient));

        Rarity[] rarities = Rarity.values();
        for (int i = 0; i < rarities.length && i < RARITY_SLOTS.length; i++) {
            Rarity r = rarities[i];
            boolean allowed = spec.supportsRarity(r);
            boolean selected = spec.rarity() == r;
            List<String> lore = new ArrayList<>();
            if (allowed) {
                lore.add(selected ? "§aSelected" : "§7Click to select");
            } else {
                lore.add(spec.emberguard()
                        ? "§cEmberguard is EMBER-only"
                        : "§cOnly Emberguard armor can be EMBER");
                lore.add("§8Disabled for this item.");
            }
            inv.setItem(RARITY_SLOTS[i], makeButton(allowed ? Material.PAPER : Material.GRAY_STAINED_GLASS_PANE,
                    r.legacy + r.name(), lore, allowed ? ACTION_RARITY : null, r.name()));
        }

        for (int level = 0; level <= GiveSpec.MAX_UPGRADE; level++) {
            boolean selected = spec.upgradeLevel() == level;
            inv.setItem(UPGRADE_SLOTS[level], makeButton(
                    selected ? Material.LIME_DYE : Material.GRAY_DYE,
                    "§fUpgrade §d+" + level,
                    List.of(selected ? "§aSelected" : "§7Click to set the upgrade level",
                            "§8Max " + GiveSpec.MAX_UPGRADE),
                    ACTION_UPGRADE, Integer.toString(level)));
        }

        inv.setItem(AFFIX_NONE_SLOT, makeButton(Material.BARRIER, "§fReforge: §7None",
                List.of("§7Clear all affixes.", spec.affixes().isEmpty() ? "§aSelected" : "§7Click"),
                ACTION_AFFIX, "none"));
        inv.setItem(AFFIX_RANDOM_SLOT, makeButton(Material.ENCHANTED_BOOK, "§fReforge: §dRandom",
                List.of("§7Roll a rarity-appropriate affix set.",
                        "§8Current: §7" + spec.affixSummary()),
                ACTION_AFFIX, "random"));
        Affix[] affixValues = Affix.values();
        for (int i = 0; i < affixValues.length; i++) {
            Affix affix = affixValues[i];
            boolean eligible = affix.appliesTo(spec.kind());
            boolean selected = spec.hasAffix(affix);
            List<String> lore = new ArrayList<>();
            if (eligible) {
                lore.add("§7" + affix.stat.color + "+" + affix.valueFor(spec.rarity())
                        + " " + affix.stat.name().toLowerCase().replace('_', ' '));
                lore.add(selected ? "§aSelected — click to remove" : "§7Click to add");
                if (!selected && spec.affixes().size() >= GiveSpec.MAX_AFFIXES) {
                    lore.add("§cAffix cap reached (" + GiveSpec.MAX_AFFIXES + ").");
                }
            } else {
                lore.add("§cNot valid for " + spec.kind() + " gear.");
            }
            inv.setItem(AFFIX_START_SLOT + i, makeButton(
                    eligible ? Material.NAME_TAG : Material.GRAY_STAINED_GLASS_PANE,
                    (selected ? "§a" : "§f") + affix.label, lore,
                    eligible ? ACTION_AFFIX : null, affix.name()));
        }

        inv.setItem(4, makeButton(Material.BOOK, "§f" + spec.displayName(),
                List.of("§7" + spec.describe(), "", "§8Use the buttons below to change it."),
                null, null));
        inv.setItem(45, makeButton(Material.PLAYER_HEAD, "§fTarget: §a" + state.targetName,
                List.of("§7Click to choose an online player."), ACTION_TARGET, null));
        inv.setItem(46, makeButton(spec.persistent() ? Material.LIME_DYE : Material.GRAY_DYE,
                "§fPersistent: " + (spec.persistent() ? "§aON" : "§7OFF"),
                List.of("§7ON: survives death (UUID + ★).", "§7OFF: normal run gear.",
                        "§8Full durability; not the half-durability Persist Master copy."),
                ACTION_PERSISTENT, null));
        inv.setItem(49, makeButton(Material.EMERALD_BLOCK, "§aGive",
                List.of("§7Grant this exact item to §f" + state.targetName + "§7.",
                        "§8Preview rebuilds on every change."),
                ACTION_GIVE, null));
        inv.setItem(52, makeButton(Material.ARROW, "§f← Back", List.of("§7Gear list"), ACTION_BACK, null));
        inv.setItem(53, makeButton(Material.BARRIER, "§cClose", List.of(), ACTION_CLOSE, null));
        fillEmpty(inv);
        return inv;
    }

    private Inventory renderTargets(State state) {
        Inventory inv = Bukkit.createInventory(null, TARGET_SIZE, LEGACY.deserialize("§8Give — Target"));
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        Player self = Bukkit.getPlayer(state.admin);
        if (self != null) {
            players.remove(self);
            players.add(0, self);
        }
        int totalPages = Math.max(1, (players.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        state.page = Math.max(0, Math.min(state.page, totalPages - 1));
        int from = state.page * PAGE_SIZE;
        int to = Math.min(players.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Player target = players.get(i);
            boolean selected = target.getUniqueId().equals(state.target);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta skull) skull.setOwningPlayer(target);
            meta.displayName(LEGACY.deserialize((selected ? "§a" : "§f") + target.getName()));
            meta.lore(List.of(LEGACY.deserialize(selected ? "§aSelected target" : "§7Click to target")));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, ACTION_TARGET_PICK);
            meta.getPersistentDataContainer().set(argKey, PersistentDataType.STRING,
                    target.getUniqueId().toString());
            head.setItemMeta(meta);
            inv.setItem(i - from, head);
        }
        inv.setItem(45, makeButton(Material.ARROW, "§f← Back", List.of(), ACTION_BACK, null));
        inv.setItem(49, makeButton(Material.PAPER, "§7Page " + (state.page + 1) + " / " + totalPages,
                List.of("§8" + players.size() + " online players"), null, null));
        if (state.page > 0) {
            inv.setItem(46, makeButton(Material.ARROW, "§f← Previous",
                    List.of("§7Page " + state.page), ACTION_PREV, null));
        }
        if (state.page < totalPages - 1) {
            inv.setItem(52, makeButton(Material.ARROW, "§fNext →",
                    List.of("§7Page " + (state.page + 2)), ACTION_NEXT, null));
        }
        inv.setItem(53, makeButton(Material.BARRIER, "§cClose", List.of(), ACTION_CLOSE, null));
        fillEmpty(inv);
        return inv;
    }

    private Inventory renderUtility(State state) {
        Inventory inv = Bukkit.createInventory(null, UTILITY_SIZE, LEGACY.deserialize("§8Give — Utility"));
        inv.setItem(4, makeButton(Material.GOLDEN_APPLE, "§fHeal target",
                List.of("§7Restore §f" + state.targetName + "§7 to full health."), ACTION_HEAL, null));
        inv.setItem(10, makeButton(Material.GOLD_NUGGET, "§eRun coins ×1",
                List.of("§7Drop 1 gold-nugget run coin."), ACTION_RUN_COINS, "1"));
        inv.setItem(11, makeButton(Material.GOLD_NUGGET, "§eRun coins ×10",
                List.of("§7Drop 10 gold-nugget run coins."), ACTION_RUN_COINS, "10"));
        inv.setItem(12, makeButton(Material.GOLD_NUGGET, "§eRun coins ×100",
                List.of("§7Drop 100 gold-nugget run coins."), ACTION_RUN_COINS, "100"));
        inv.setItem(14, makeButton(Material.GOLD_INGOT, "§6Persistent coins ×10",
                List.of("§7Add 10 persistent coins."), ACTION_PERSISTENT_COINS, "10"));
        inv.setItem(15, makeButton(Material.GOLD_INGOT, "§6Persistent coins ×100",
                List.of("§7Add 100 persistent coins."), ACTION_PERSISTENT_COINS, "100"));
        inv.setItem(16, makeButton(Material.GOLD_INGOT, "§6Persistent coins ×1000",
                List.of("§7Add 1000 persistent coins."), ACTION_PERSISTENT_COINS, "1000"));
        inv.setItem(18, makeButton(Material.PLAYER_HEAD, "§fTarget: §a" + state.targetName,
                List.of("§7Click to choose an online player."), ACTION_TARGET, null));
        inv.setItem(22, makeButton(Material.ARROW, "§f← Back", List.of("§7Categories"), ACTION_BACK, null));
        inv.setItem(26, makeButton(Material.BARRIER, "§cClose", List.of(), ACTION_CLOSE, null));
        fillEmpty(inv);
        return inv;
    }

    // ==================== EVENTS ====================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        State state = states.get(e.getInventory());
        if (state == null) return;
        e.setCancelled(true);
        if (!p.hasPermission("dung.admin")) {
            p.closeInventory();
            p.sendMessage("§cNo permission.");
            return;
        }
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        String action = stringTag(e.getCurrentItem(), actionKey);
        if (action == null || action.isEmpty()) return;
        String arg = stringTag(e.getCurrentItem(), argKey);
        handle(p, state, action, arg);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (states.containsKey(e.getInventory())) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        states.remove(e.getInventory());
    }

    private void handle(Player p, State state, String action, String arg) {
        switch (action) {
            case ACTION_WEAPONS -> {
                state.category = GiveSpec.Category.WEAPON;
                state.page = 0;
                state.screen = Screen.LIST;
                reopen(state);
            }
            case ACTION_ARMOR -> {
                state.category = GiveSpec.Category.ARMOR;
                state.page = 0;
                state.screen = Screen.LIST;
                reopen(state);
            }
            case ACTION_SHIELDS -> {
                state.category = GiveSpec.Category.SHIELD;
                state.page = 0;
                state.screen = Screen.LIST;
                reopen(state);
            }
            case ACTION_UTILITY -> {
                state.screen = Screen.UTILITY;
                reopen(state);
            }
            case ACTION_BACK -> {
                switch (state.screen) {
                    case LIST -> state.screen = Screen.ROOT;
                    case BUILDER -> state.screen = Screen.LIST;
                    case TARGETS -> state.screen = state.targetReturn;
                    case UTILITY -> state.screen = Screen.ROOT;
                    default -> state.screen = Screen.ROOT;
                }
                reopen(state);
            }
            case ACTION_CLOSE -> p.closeInventory();
            case ACTION_PREV -> {
                if (state.page > 0) state.page--;
                reopen(state);
            }
            case ACTION_NEXT -> {
                state.page++;
                reopen(state);
            }
            case ACTION_PICK -> {
                GiveSpec spec = specFor(state, arg);
                if (spec == null) return;
                state.spec = spec;
                state.screen = Screen.BUILDER;
                reopen(state);
            }
            case ACTION_RARITY -> {
                if (state.spec == null || arg == null) return;
                try {
                    state.spec.setRarity(Rarity.valueOf(arg));
                } catch (IllegalArgumentException ignored) {
                    // stale or tampered button
                }
                reopen(state);
            }
            case ACTION_UPGRADE -> {
                if (state.spec == null || arg == null) return;
                try {
                    state.spec.setUpgradeLevel(Integer.parseInt(arg));
                } catch (NumberFormatException ignored) {
                    // stale or tampered button
                }
                reopen(state);
            }
            case ACTION_AFFIX -> {
                if (state.spec == null || arg == null) return;
                switch (arg) {
                    case "none" -> state.spec.clearAffixes();
                    case "random" -> state.spec.randomizeAffixes(ThreadLocalRandom.current());
                    default -> {
                        try {
                            state.spec.toggleAffix(Affix.valueOf(arg));
                        } catch (IllegalArgumentException ignored) {
                            // stale or tampered button
                        }
                    }
                }
                reopen(state);
            }
            case ACTION_TARGET -> {
                state.targetReturn = state.screen;
                state.page = 0;
                state.screen = Screen.TARGETS;
                reopen(state);
            }
            case ACTION_TARGET_PICK -> {
                if (arg == null) return;
                try {
                    UUID id = UUID.fromString(arg);
                    Player target = Bukkit.getPlayer(id);
                    if (target == null) return;
                    state.target = id;
                    state.targetName = target.getName();
                    if (state.spec != null) state.spec.setTarget(id);
                    state.screen = state.targetReturn == Screen.UTILITY
                            ? Screen.UTILITY : (state.spec == null ? Screen.ROOT : Screen.BUILDER);
                    reopen(state);
                } catch (IllegalArgumentException ignored) {
                    // stale or tampered button
                }
            }
            case ACTION_PERSISTENT -> {
                if (state.spec == null) return;
                state.spec.togglePersistent();
                reopen(state);
            }
            case ACTION_GIVE -> {
                if (state.spec == null) return;
                grant(p, state);
            }
            case ACTION_HEAL -> {
                Player target = target(state);
                if (target == null) return;
                target.setHealth(target.getMaxHealth());
                p.sendMessage("§aHealed §f" + target.getName() + "§a.");
                target.sendMessage("§aYou were healed by §f" + p.getName() + "§a.");
                reopen(state);
            }
            case ACTION_RUN_COINS -> {
                int amount = parseInt(arg, 10);
                Player target = target(state);
                if (target == null) return;
                giveMaterial(target, Material.GOLD_NUGGET, Math.max(1, amount));
                p.sendMessage("§eGave §f" + amount + "§e run coins to §f" + target.getName() + "§e.");
                reopen(state);
            }
            case ACTION_PERSISTENT_COINS -> {
                int amount = parseInt(arg, 10);
                Player target = target(state);
                if (target == null) return;
                plugin.meta().addPersistentCoins(target.getUniqueId(), amount);
                plugin.meta().save();
                p.sendMessage("§6Gave §f" + amount + "§6 persistent coins to §f" + target.getName() + "§6.");
                target.sendMessage("§6You received §f" + amount + "§6 persistent coins.");
                reopen(state);
            }
            default -> {
                // Unknown/tampered action: leave the menu untouched.
            }
        }
    }

    private void grant(Player admin, State state) {
        if (!admin.hasPermission("dung.admin")) {
            admin.closeInventory();
            admin.sendMessage("§cNo permission.");
            return;
        }
        Player target = target(state);
        if (target == null) return;
        GiveSpec spec = state.spec;
        ItemStack item = GiveItemFactory.build(spec, target);
        giveItem(target, item);
        admin.sendMessage("§aGave §f" + spec.displayName() + "§a (" + spec.rarity()
                + ", upgrade " + spec.upgradeLevel() + ") to §f" + target.getName() + "§a.");
        if (target != admin) {
            target.sendMessage("§aYou received §f" + spec.displayName() + "§a from §f" + admin.getName() + "§a.");
        }
        plugin.getLogger().info("[give] " + admin.getName() + " -> " + target.getName() + ": " + spec.describe());
        // Keep the builder open and refresh the preview/status.
        reopen(state);
    }

    private void giveItem(Player target, ItemStack item) {
        var leftovers = target.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
    }

    /** Give a bulk material in valid stack sizes; a 100-coin grant must not create one oversized stack. */
    private void giveMaterial(Player target, Material material, int amount) {
        int max = Math.max(1, material.getMaxStackSize());
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, max);
            giveItem(target, new ItemStack(material, n));
            left -= n;
        }
    }

    private Player target(State state) {
        Player target = Bukkit.getPlayer(state.target);
        if (target == null) {
            Player admin = Bukkit.getPlayer(state.admin);
            if (admin != null) admin.sendMessage("§cTarget is no longer online.");
        }
        return target;
    }

    private void reopen(State state) {
        // Defer one tick so the previous InventoryCloseEvent removes the old inventory first.
        Bukkit.getScheduler().runTask(plugin, () -> render(state));
    }

    // ==================== CATALOG / HELPERS ====================

    private record Entry(String key, Material icon, String name, List<String> lore) {}

    private List<Entry> entriesFor(GiveSpec.Category category) {
        List<Entry> out = new ArrayList<>();
        switch (category) {
            case WEAPON -> {
                for (GiveCatalog.WeaponBase base : GiveCatalog.WEAPONS) {
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Damage: §c" + base.baseDamage() + "  §7Ability: §6" + base.ability());
                    if (base.magicDamage() > 0) lore.add("§7Magic damage: §d" + base.magicDamage());
                    if (base.reach() > 0) lore.add("§7Reach: §b" + base.reach());
                    lore.add("§8Click to configure.");
                    out.add(new Entry("weapon:" + base.id(), base.material(), "§f" + base.name(), lore));
                }
            }
            case ARMOR -> {
                for (GiveCatalog.ArmorPiece piece : GiveCatalog.ALL_ARMOR_PIECES) {
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Set: §f" + piece.set().name() + "  §7Slot: §f" + piece.set().pieceName(piece.slot()));
                    lore.add("§7Base defense: §a" + piece.set().baseDefense());
                    if (piece.set().emberguard()) lore.add("§dEMBER only");
                    lore.add("§8Click to configure.");
                    out.add(new Entry(piece.key(), piece.set().material(piece.slot()),
                            "§f" + piece.displayName(), lore));
                }
            }
            case SHIELD -> out.add(new Entry("shield:" + GiveCatalog.MANA_SHIELD.id(),
                    GiveCatalog.MANA_SHIELD.material(), "§f" + GiveCatalog.MANA_SHIELD.name(),
                    List.of("§7Rarity-scaled shield capacity.", "§8Click to configure.")));
        }
        return out;
    }

    private GiveSpec specFor(State state, String key) {
        if (key == null) return null;
        if (key.startsWith("weapon:")) {
            GiveCatalog.WeaponBase base = GiveCatalog.weapon(key.substring("weapon:".length()));
            return base == null ? null : GiveSpec.weapon(base, state.target);
        }
        if (key.startsWith("armor:")) {
            String[] parts = key.split(":");
            if (parts.length != 3) return null;
            try {
                int slot = Integer.parseInt(parts[2]);
                GiveCatalog.ArmorPiece piece = GiveCatalog.armorPiece(parts[1], slot);
                return piece == null ? null : GiveSpec.armor(piece, state.target);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (key.startsWith("shield:")) {
            return GiveSpec.shield(GiveCatalog.MANA_SHIELD, state.target);
        }
        return null;
    }

    private ItemStack makeButton(Material mat, String name, List<String> lore, String action, String arg) {
        ItemStack s = new ItemStack(mat);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(LEGACY.deserialize(line));
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (action != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            if (arg != null) {
                meta.getPersistentDataContainer().set(argKey, PersistentDataType.STRING, arg);
            }
        }
        s.setItemMeta(meta);
        return s;
    }

    private void fillEmpty(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.empty());
        filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }
    }

    private static String stringTag(ItemStack item, NamespacedKey key) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
