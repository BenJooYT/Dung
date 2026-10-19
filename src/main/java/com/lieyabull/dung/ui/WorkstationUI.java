package com.lieyabull.dung.ui;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.WorkstationRules;
import com.lieyabull.dung.game.WorkstationType;
import com.lieyabull.dung.items.Affix;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.meta.MetaManager;
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

/**
 * Unified chest GUI for the five workstation types in an UPGRADE room. Each workstation lists the
 * player's eligible run gear and shows the selected item, the exact operation + resulting change,
 * and the costs before committing. Destructive operations (SALVAGE, PRESERVE) require an explicit
 * confirm click. All changes are applied server-side via {@link DungeonInstance}, which re-validates
 * the item is still in the slot and belongs to the player.
 */
public final class WorkstationUI implements Listener {

    private static final int SIZE = 45; // 5 rows
    private static final String GUI_KEY = "dung_ws";
    private static final String ACTION_SELECT = "select";
    private static final String ACTION_CONFIRM = "confirm";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_PREV = "prev";
    private static final String ACTION_NEXT = "next";

    // Gear list occupies the first three rows (0-26); the control row holds confirm/info.
    private static final int PAGE_SIZE = 27;
    private static final int INFO_SLOT = 32;
    private static final int CONFIRM_SLOT = 31;
    private static final int BACK_SLOT = 41;
    private static final int PREV_SLOT = 36;
    private static final int NEXT_SLOT = 44;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // Per-inventory open state (keyed by inventory so a re-open on top of the old GUI stays tracked).
    private final Map<Inventory, State> openStates = new ConcurrentHashMap<>();

    private final Dung plugin;
    private final org.bukkit.NamespacedKey key;
    private final org.bukkit.NamespacedKey slotKey;

    public WorkstationUI(Dung plugin) {
        this.plugin = plugin;
        this.key = new org.bukkit.NamespacedKey(plugin, GUI_KEY); // plugin-owned namespace
        this.slotKey = new org.bukkit.NamespacedKey(plugin, GUI_KEY + "_slot");
    }

    private static final class State {
        WorkstationType type;
        DungeonInstance di;
        // maps GUI gear slot -> player inventory slot (0-40; covers storage, armor and offhand)
        int[] guiToPlayer;
        int selected = -1;      // player inventory slot currently selected (-1 = none)
        String selectedFp = ""; // identity fingerprint of the selected item, re-checked before applying
        boolean confirmed;      // for SALVAGE/PRESERVE: whether the confirm step is active
        boolean busy;           // set once an operation is executing, to guard against double-click re-apply
        int page;               // zero-based page of the gear list (PAGE_SIZE entries per page)
        int[] groups = new int[3]; // eligible counts {hotbar, bag(main), armor+offhand} for the info panel
    }

    public void openWorkstation(Player p, DungeonInstance di, WorkstationType type) {
        openWorkstation(p, di, type, 0);
    }

    public void openWorkstation(Player p, DungeonInstance di, WorkstationType type, int page) {
        PlayerState st = di.playerStateOf(p);
        if (st == null) return;
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());

        String title = com.lieyabull.dung.lang.Lang.forPlayer(p, "ws.title",
                type.color + wsLabel(p, type), prof.persistentCoins, prof.shards, st.coins);
        Inventory inv = Bukkit.createInventory(null, SIZE, LEGACY.deserialize(title));
        State state = new State();
        state.type = type;
        state.di = di;
        state.guiToPlayer = new int[SIZE];
        java.util.Arrays.fill(state.guiToPlayer, -1);

        List<Integer> slots = type == WorkstationType.STORAGE
                ? di.persistentSlots(p)
                : di.workstationSlots(p);
        // PRESERVE/SALVAGE reject persistent gear server-side via WorkstationRules.isWorkstationGear
        // (run gear only) — filter those pieces out of the list instead of letting CONFIRM refuse them.
        // UPGRADE/REFORGE accept persistent gear (at double cost), so they keep the full list.
        if (type == WorkstationType.PRESERVE || type == WorkstationType.SALVAGE) {
            List<Integer> runOnly = new ArrayList<>();
            for (int playerSlot : slots) {
                if (WorkstationRules.isWorkstationGear(itemAt(p, playerSlot))) runOnly.add(playerSlot);
            }
            slots = runOnly;
        }
        int totalPages = Math.max(1, (slots.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        state.page = Math.max(0, Math.min(page, totalPages - 1));
        // Eligible items arrive armor-first, bag, hotbar last (see dungeon.slotScheme). Surface the
        // group sizes in the info panel so the ordering is self-explanatory in a 27-slot page grid.
        int hotbar = 0, bag = 0, worn = 0;
        for (int playerSlot : slots) {
            if (playerSlot >= 36) worn++;
            else if (playerSlot >= 9) bag++;
            else hotbar++;
        }
        state.groups = new int[]{hotbar, bag, worn};
        int from = state.page * PAGE_SIZE;
        int to = Math.min(slots.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            int playerSlot = slots.get(i);
            int guiIndex = i - from;
            ItemStack gear = itemAt(p, playerSlot);
            if (gear == null) continue;
            ItemStack display = gear.clone();
            display.editMeta(meta -> meta.getPersistentDataContainer().set(
                    key, org.bukkit.persistence.PersistentDataType.STRING, ACTION_SELECT));
            display.editMeta(meta -> meta.getPersistentDataContainer().set(
                    slotKey, org.bukkit.persistence.PersistentDataType.INTEGER, guiIndex));
            inv.setItem(guiIndex, display);
            state.guiToPlayer[guiIndex] = playerSlot;
        }

        // Info panel stays anchored at INFO_SLOT in both the list and the confirm view.
        inv.setItem(INFO_SLOT, makeInfo(p, type, state.groups));
        if (totalPages > 1) {
            if (state.page > 0) {
                inv.setItem(PREV_SLOT, makeItem(Material.ARROW, "§f← Previous",
                        List.of("§7Page " + (state.page + 1) + " of " + totalPages), ACTION_PREV));
            }
            if (state.page < totalPages - 1) {
                inv.setItem(NEXT_SLOT, makeItem(Material.ARROW, "§fNext →",
                        List.of("§7Page " + (state.page + 1) + " of " + totalPages), ACTION_NEXT));
            }
        }

        fillEmpty(inv);
        openStates.put(inv, state);
        p.openInventory(inv);
    }

    /**
     * Raw-slot read using the exact scheme {@link DungeonInstance#workstationSlots} emits: 0-35
     * storage, 36-39 armor, 40 offhand. There are no negative markers — DungeonInstance never
     * produces them, so any out-of-range slot (including the -1 "nothing selected" sentinel)
     * simply yields null.
     */
    private ItemStack itemAt(Player p, int playerSlot) {
        if (playerSlot < 0 || playerSlot > 40) return null;
        return p.getInventory().getItem(playerSlot);
    }

    /** Info panel builder. {@code groups} = eligible counts {hotbar, bag, armor+offhand} from the
     *  list that this state is rendering; null renders without the group summary line. */
    private ItemStack makeInfo(Player p, WorkstationType type, int[] groups) {
        List<String> lines = new ArrayList<>();
        lines.add("§7" + wsDesc(p, type));
        if (groups != null) {
            lines.add(Lang(p, "ws.groupCounts", groups[2], groups[1], groups[0]));
            lines.add("");
        }
        switch (type) {
            case UPGRADE -> {
                lines.add(Lang(p, "ws.info.upgrade.cost"));
                lines.add(Lang(p, "ws.info.upgrade.effect", (int) (WorkstationRules.UPGRADE_STAT_PER_LEVEL * 100)));
            }
            case REFORGE -> {
                lines.add(Lang(p, "ws.info.reforge.cost", WorkstationRules.REFORGE_SHARD_COST, WorkstationRules.REFORGE_SHARD_PER_REFORGE));
                lines.add(Lang(p, "ws.info.reforge.effect"));
            }
            case PRESERVE -> {
                lines.add(Lang(p, "ws.info.preserve.chance",
                        (int) (WorkstationRules.PRESERVE_SUCCESS_CHANCE * 100), WorkstationRules.PRESERVE_PITY));
                lines.add(Lang(p, "ws.info.preserve.effect"));
                lines.add(Lang(p, "ws.info.preserve.cost", WorkstationRules.PRESERVE_COIN_COST,
                        WorkstationRules.PRESERVE_PERSISTENT_COIN_COST, WorkstationRules.PRESERVE_SHARD_COST));
            }
            case SALVAGE -> {
                lines.add(Lang(p, "ws.info.salvage.effect"));
            }
            case STORAGE -> {
                lines.add(Lang(p, "ws.info.storage.effect"));
            }
        }
        Material mat = switch (type) {
            case UPGRADE -> Material.SMITHING_TABLE;
            case REFORGE -> Material.GRINDSTONE;
            case PRESERVE -> Material.ANVIL;
            case SALVAGE -> Material.BARREL;
            case STORAGE -> Material.ENDER_CHEST;
        };
        return makeItem(mat, type.color + wsLabel(p, type), lines);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        State state = openStates.get(e.getInventory());
        if (state == null) return;
        e.setCancelled(true);
        if (state.busy) return; // an operation is already running on this GUI; ignore further clicks

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR
                || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        String action = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);

        if (action == null) {
            // Clicked an item in the player's own inventory (bottom slots) — treat as a selection
            // if it is eligible for this workstation type. The bottom view lists the 36 storage
            // slots in vanilla chest order (main 9-35 first, hotbar 0-8 last), so the raw offset
            // must be converted back to the PlayerInventory slot DungeonInstance operates on.
            int rawSlot = e.getRawSlot();
            int invSize = e.getInventory().getSize();
            if (rawSlot >= invSize) {
                int viewIndex = rawSlot - invSize;
                if (viewIndex >= 0 && viewIndex < 36) {
                    int playerSlot = viewIndex < 27 ? viewIndex + 9 : viewIndex - 27;
                    ItemStack playerItem = itemAt(p, playerSlot);
                    if (playerItem != null && eligibleForType(playerItem, state.type)) {
                        state.selected = playerSlot;
                        state.confirmed = false;
                        renderDetail(p, state, e.getInventory());
                    }
                }
            }
            return;
        }

        if (ACTION_BACK.equals(action)) {
            state.confirmed = false;
            int page = state.page;
            reopen(() -> openWorkstation(p, state.di, state.type, page));
            return;
        }
        if (ACTION_PREV.equals(action)) {
            int page = state.page;
            reopen(() -> openWorkstation(p, state.di, state.type, page - 1));
            return;
        }
        if (ACTION_NEXT.equals(action)) {
            int page = state.page;
            reopen(() -> openWorkstation(p, state.di, state.type, page + 1));
            return;
        }
        if (ACTION_SELECT.equals(action)) {
            Integer guiIndex = pdc.get(slotKey,
                    org.bukkit.persistence.PersistentDataType.INTEGER);
            if (guiIndex == null || guiIndex < 0 || guiIndex >= state.guiToPlayer.length) return;
            state.selected = state.guiToPlayer[guiIndex];
            state.confirmed = false;
            renderDetail(p, state, e.getInventory());
            return;
        }
        if (ACTION_CONFIRM.equals(action)) {
            boolean destructive = state.type == WorkstationType.PRESERVE || state.type == WorkstationType.SALVAGE;
            if (destructive && !state.confirmed) {
                // first confirm click for a destructive op: arm it and re-render to require a 2nd click
                state.confirmed = true;
                renderDetail(p, state, e.getInventory());
                return;
            }
            execute(state, p);
            int page = state.page;
            reopen(() -> openWorkstation(p, state.di, state.type, page));
        }
    }

    /**
     * Whether an item may be selected for the given workstation type. Mirrors the server-side
     * validation in {@link DungeonInstance}: PRESERVE/SALVAGE accept run gear only
     * ({@link WorkstationRules#isWorkstationGear}), UPGRADE/REFORGE additionally accept persistent
     * gear (at double cost), and STORAGE is the persistent-only read-only view.
     */
    private static boolean eligibleForType(ItemStack item, WorkstationType type) {
        if (item == null) return false;
        return switch (type) {
            case STORAGE -> com.lieyabull.dung.items.GearFactory.isPersistent(item);
            case PRESERVE, SALVAGE -> WorkstationRules.isWorkstationGear(item);
            case UPGRADE, REFORGE ->
                    WorkstationRules.isWorkstationGear(item)
                            || com.lieyabull.dung.items.GearFactory.isPersistent(item);
        };
    }

    /** Render the detail panel for the currently selected item (costs + result preview + confirm/back). */
    private void renderDetail(Player p, State state, Inventory inv) {
        int playerSlot = state.selected;
        ItemStack item = currentItem(p, state, playerSlot);
        if (item == null) {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "workstation.unavailable"));
            int page = state.page;
            reopen(() -> openWorkstation(p, state.di, state.type, page));
            return;
        }
        state.selectedFp = fingerprint(item);
        List<String> lines = new ArrayList<>();
        lines.add(Lang(p, "ws.selected", item.getItemMeta() == null ? item.getType().name()
                : item.getItemMeta().getDisplayName()));
        // Cost line for the confirmation button lore (set by UPGRADE case)
        String[] confirmCostLine = {null};
        switch (state.type) {
            case UPGRADE -> {
                int lvl = GearFactory.getUpgradeLevel(item);
                boolean isPersistent = GearFactory.isPersistent(item);
                lines.add(Lang(p, "ws.currentLevel", lvl));
                // Show base stat + upgrade bonus, e.g. "DMG: 32 (+4)"
                String coreTag = GearFactory.coreStatTag(item);
                if (coreTag != null) {
                    var pdc = item.getItemMeta().getPersistentDataContainer();
                    int current = pdc.getOrDefault(org.bukkit.NamespacedKey.minecraft(coreTag),
                            org.bukkit.persistence.PersistentDataType.INTEGER, 0);
                    double mult = WorkstationRules.upgradeStatMult(lvl);
                    int base = (int) Math.round(current / mult);
                    int bonus = current - base;
                    String label = switch (coreTag) {
                        case ItemTags.DAMAGE -> Lang(p, "ws.stat.dmg");
                        case ItemTags.MAGIC_DAMAGE -> Lang(p, "ws.stat.magicdmg");
                        case ItemTags.DEFENSE -> Lang(p, "ws.stat.def");
                        case ItemTags.SHIELD_MAX -> Lang(p, "ws.stat.shield");
                        default -> Lang(p, "ws.stat.stat");
                    };
                    lines.add(Lang(p, "ws.stat.value", label, current, bonus));
                }
                lines.add(Lang(p, "ws.nextLevel", lvl + 1,
                        (int) (WorkstationRules.UPGRADE_STAT_PER_LEVEL * 100)));
                int coinCost = WorkstationRules.upgradeCoinCost(lvl);
                int shardCost = WorkstationRules.upgradeShardCost(lvl);
                if (isPersistent) {
                    lines.add(Lang(p, "ws.upgrade.cost.persistent", coinCost * 2, shardCost * 2));
                    confirmCostLine[0] = Lang(p, "ws.upgrade.cost.persistent", coinCost * 2, shardCost * 2);
                } else {
                    lines.add(Lang(p, "ws.upgrade.cost.normal", coinCost, shardCost));
                    confirmCostLine[0] = Lang(p, "ws.upgrade.cost.normal", coinCost, shardCost);
                }
            }
            case REFORGE -> {
                int reforgeCount = GearFactory.getReforgeCount(item);
                boolean isPersistent = GearFactory.isPersistent(item);
                List<Affix.AffixRoll> rolled = state.di.previewReforge(item);
                lines.add(Lang(p, "ws.reforge.newAffixes", affixSummary(rolled)));
                int cost = WorkstationRules.reforgeShardCost(reforgeCount);
                if (isPersistent) {
                    lines.add(Lang(p, "ws.reforge.cost.persistent", cost * 2));
                } else {
                    lines.add(Lang(p, "ws.reforge.cost.normal", cost));
                }
                if (reforgeCount > 0) {
                    lines.add(Lang(p, "ws.reforge.prior",
                            cost - WorkstationRules.REFORGE_SHARD_COST,
                            reforgeCount, reforgeCount == 1 ? "" : "s"));
                }
            }
            case PRESERVE -> {
                int fails = state.di.preserveFails().getOrDefault(p.getUniqueId(), 0);
                boolean guaranteed = WorkstationRules.preserveGuaranteed(fails);
                lines.add(Lang(p, "ws.preserve.attempt"));
                if (guaranteed) {
                    lines.add(Lang(p, "ws.preserve.pity"));
                } else {
                    int remaining = WorkstationRules.PRESERVE_PITY - fails;
                    lines.add(Lang(p, "ws.preserve.chance",
                            (int) (WorkstationRules.PRESERVE_SUCCESS_CHANCE * 100), remaining,
                            remaining == 1 ? "" : "s"));
                }
                lines.add(Lang(p, "ws.preserve.effect"));
                lines.add(Lang(p, "ws.info.preserve.cost", WorkstationRules.PRESERVE_COIN_COST,
                        WorkstationRules.PRESERVE_PERSISTENT_COIN_COST, WorkstationRules.PRESERVE_SHARD_COST));
            }
            case SALVAGE -> {
                int value = WorkstationRules.salvageValue(
                        GearFactory.getRarity(item), WorkstationRules.primaryStat(item));
                lines.add(Lang(p, "ws.salvage.value", value));
                lines.add(Lang(p, "ws.salvage.destroy"));
            }
            default -> {}
        }
        lines.add("");

        boolean destructive = state.type == WorkstationType.PRESERVE || state.type == WorkstationType.SALVAGE;
        String confirmName = (destructive && state.confirmed)
                ? Lang(p, "ws.confirmAgain") : Lang(p, "ws.confirm");
        List<String> confirmLore;
        if (destructive && state.confirmed) {
            confirmLore = List.of(Lang(p, "ws.confirmDestructive"));
        } else {
            confirmLore = new ArrayList<>();
            confirmLore.add(Lang(p, "ws.confirmApply"));
            if (confirmCostLine[0] != null) {
                confirmLore.add(confirmCostLine[0]);
            }
        }
        inv.setItem(CONFIRM_SLOT, makeItem(Material.LIME_DYE, confirmName, confirmLore, ACTION_CONFIRM));
        inv.setItem(BACK_SLOT, makeItem(Material.ARROW, Lang(p, "ws.back.name"),
                List.of(Lang(p, "ws.back.lore")), ACTION_BACK));
        inv.setItem(INFO_SLOT, makeInfo(p, state.type, state.groups)); // keep the info panel anchored
    }

    private ItemStack currentItem(Player p, State state, int playerSlot) {
        // Same raw-slot scheme as DungeonInstance.workstationSlots(): 0-35 storage, 36-39 armor,
        // 40 offhand. No negative markers — out-of-range slots (incl. the -1 sentinel) yield null.
        return itemAt(p, playerSlot);
    }

    private void execute(State state, Player p) {
        if (state.selected < 0) return;
        if (state.busy) return;
        state.busy = true;
        try {
            int playerSlot = state.selected;
            // Re-validate that the same item the player picked is still in the slot before applying. This
            // prevents a rearranged inventory from upgrading/salvaging a *different* item than was shown.
            ItemStack now = currentItem(p, state, playerSlot);
            if (now == null || !fingerprint(now).equals(state.selectedFp)) {
                p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "workstation.changed"));
                return;
            }
            switch (state.type) {
                case UPGRADE -> state.di.tryUpgrade(p, playerSlot);
                case REFORGE -> state.di.tryReforge(p, playerSlot);
                case PRESERVE -> state.di.tryPreserve(p, playerSlot);
                case SALVAGE -> state.di.trySalvage(p, playerSlot);
                case STORAGE -> { /* read-only; nothing to execute */ }
            }
        } finally {
            // Always clear the guard — a rejected operation must never brick the open GUI
            // (busy is only meant to swallow double-clicks within the same tick).
            state.busy = false;
        }
    }

    /** Stable identity fingerprint of an item, so an operation can confirm the same item is still present. */
    private static String fingerprint(ItemStack s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.getType().name()).append('|').append(s.getAmount());
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            sb.append('|').append(m.getDisplayName());
            var pdc = m.getPersistentDataContainer();
            for (org.bukkit.NamespacedKey key : pdc.getKeys()) {
                // Check the tag type before reading — pdc.get() throws IllegalArgumentException
                // when the stored type doesn't match the requested type.
                if (pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                    sb.append('|').append(key.getKey()).append('=').append(
                            pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING));
                } else if (pdc.has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                    sb.append('|').append(key.getKey()).append('=').append(
                            pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER));
                } else if (pdc.has(key, org.bukkit.persistence.PersistentDataType.DOUBLE)) {
                    sb.append('|').append(key.getKey()).append('=').append(
                            pdc.get(key, org.bukkit.persistence.PersistentDataType.DOUBLE));
                }
            }
        }
        return sb.toString();
    }

    private String affixSummary(List<Affix.AffixRoll> rolls) {
        if (rolls.isEmpty()) return "§8(none)";
        StringBuilder sb = new StringBuilder();
        for (Affix.AffixRoll r : rolls) {
            if (sb.length() > 0) sb.append("§7, ");
            sb.append(r.affix().label).append(" ").append(r.affix().stat.color).append("+").append(r.value());
        }
        return sb.toString();
    }

    private static String Lang(Player p, String key, Object... args) {
        return com.lieyabull.dung.lang.Lang.forPlayer(p, key, args);
    }

    private static String wsLabel(Player p, WorkstationType type) {
        return switch (type) {
            case UPGRADE -> Lang(p, "ws.label.upgrade");
            case REFORGE -> Lang(p, "ws.label.reforge");
            case PRESERVE -> Lang(p, "ws.label.preserve");
            case SALVAGE -> Lang(p, "ws.label.salvage");
            case STORAGE -> Lang(p, "ws.label.storage");
        };
    }

    private static String wsDesc(Player p, WorkstationType type) {
        return switch (type) {
            case UPGRADE -> Lang(p, "ws.desc.upgrade");
            case REFORGE -> Lang(p, "ws.desc.reforge");
            case PRESERVE -> Lang(p, "ws.desc.preserve");
            case SALVAGE -> Lang(p, "ws.desc.salvage");
            case STORAGE -> Lang(p, "ws.desc.storage");
        };
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player) {
            openStates.remove(e.getInventory());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player && openStates.get(e.getInventory()) != null) {
            e.setCancelled(true);
        }
    }

    private void reopen(Runnable open) {
        Bukkit.getScheduler().runTask(plugin, open);
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        return makeItem(mat, name, lore, null);
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore, String action) {
        ItemStack s = new ItemStack(mat);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        List<Component> lc = new ArrayList<>();
        for (String line : lore) lc.add(LEGACY.deserialize(line));
        meta.lore(lc);
        if (action != null) {
            meta.getPersistentDataContainer().set(key,
                    org.bukkit.persistence.PersistentDataType.STRING, action);
        }
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
}