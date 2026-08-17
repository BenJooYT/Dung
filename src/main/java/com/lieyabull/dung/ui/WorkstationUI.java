package com.lieyabull.dung.ui;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.WorkstationRules;
import com.lieyabull.dung.game.WorkstationType;
import com.lieyabull.dung.items.Affix;
import com.lieyabull.dung.items.GearFactory;
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

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // Per-inventory open state (keyed by inventory so a re-open on top of the old GUI stays tracked).
    private final Map<Inventory, State> openStates = new ConcurrentHashMap<>();

    private final Dung plugin;

    public WorkstationUI(Dung plugin) {
        this.plugin = plugin;
    }

    private static final class State {
        WorkstationType type;
        DungeonInstance di;
        // maps GUI gear slot -> player inventory slot (0-40; covers storage, armor and offhand)
        int[] guiToPlayer;
        int selected = -1;      // player inventory slot currently selected
        String selectedFp = ""; // identity fingerprint of the selected item, re-checked before applying
        boolean confirmed;      // for SALVAGE/PRESERVE: whether the confirm step is active
        boolean busy;           // set once an operation is executing, to guard against double-click re-apply
    }

    public void openWorkstation(Player p, DungeonInstance di, WorkstationType type) {
        PlayerState st = di.playerStateOf(p);
        if (st == null) return;
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());

        String title = type.color + type.label + "  §8§7" + (prof.persistentCoins) + " pc  §3" + prof.shards
                + " shards  §e" + st.coins + " coins";
        Inventory inv = Bukkit.createInventory(null, SIZE, LEGACY.deserialize(title));
        State state = new State();
        state.type = type;
        state.di = di;
        state.guiToPlayer = new int[SIZE];
        java.util.Arrays.fill(state.guiToPlayer, -1);

        List<Integer> slots = type == WorkstationType.STORAGE
                ? di.persistentSlots(p)
                : di.workstationSlots(p);
        for (int i = 0; i < slots.size() && i < 27; i++) {
            int playerSlot = slots.get(i);
            int guiIndex = i;
            ItemStack gear = itemAt(p, playerSlot);
            if (gear == null) continue;
            ItemStack display = gear.clone();
            display.editMeta(meta -> meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(GUI_KEY),
                    org.bukkit.persistence.PersistentDataType.STRING, ACTION_SELECT));
            display.editMeta(meta -> meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(GUI_KEY + "_slot"),
                    org.bukkit.persistence.PersistentDataType.INTEGER, guiIndex));
            inv.setItem(i, display);
            state.guiToPlayer[guiIndex] = playerSlot;
        }

        // Info panel
        int infoSlot = type == WorkstationType.STORAGE ? 31 : 30;
        inv.setItem(infoSlot, makeInfo(type));

        fillEmpty(inv);
        openStates.put(inv, state);
        p.openInventory(inv);
    }

    private ItemStack itemAt(Player p, int playerSlot) {
        if (playerSlot < 0) {
            org.bukkit.inventory.EquipmentSlot es = switch (playerSlot) {
                case -1 -> org.bukkit.inventory.EquipmentSlot.HEAD;
                case -2 -> org.bukkit.inventory.EquipmentSlot.CHEST;
                case -3 -> org.bukkit.inventory.EquipmentSlot.LEGS;
                case -4 -> org.bukkit.inventory.EquipmentSlot.FEET;
                default -> null;
            };
            return es == null ? null : p.getInventory().getItem(es);
        }
        return p.getInventory().getItem(playerSlot);
    }

    private ItemStack makeInfo(WorkstationType type) {
        List<String> lines = new ArrayList<>();
        lines.add(type.color + type.label);
        lines.add("§7" + type.description);
        lines.add("");
        switch (type) {
            case UPGRADE -> {
                lines.add("§eCosts: run coins + shards");
                lines.add("§7(scales with current level)");
                lines.add("§7Effect: raises the item's core stat");
                lines.add("§7by §5+" + (int) (WorkstationRules.UPGRADE_STAT_PER_LEVEL * 100) + "%§7/level");
            }
            case REFORGE -> {
                lines.add("§3Cost: " + WorkstationRules.REFORGE_SHARD_COST + " shards");
                lines.add("§7Effect: rerolls the item's affixes");
                lines.add("§7Keeps base stats, rarity, ability.");
            }
            case PRESERVE -> {
                lines.add("§eCost: " + WorkstationRules.PRESERVE_COIN_COST + " run coins");
                lines.add("§3Cost: " + WorkstationRules.PRESERVE_SHARD_COST + " shards");
                lines.add("§7Effect: item persists past this run");
                lines.add("§7(§ehalf durability§7). Persistent coins are");
                lines.add("§7NOT spent here.");
            }
            case SALVAGE -> {
                lines.add("§7Effect: destroy the item for shards");
                lines.add("§7Value scales with rarity + stats.");
                lines.add("§cRequires confirmation.");
            }
            case STORAGE -> {
                lines.add("§7This view is §cread-only§7 inside a run.");
                lines.add("§7You may view but not withdraw persistent");
                lines.add("§7items while in a run.");
            }
        }
        Material mat = switch (type) {
            case UPGRADE -> Material.SMITHING_TABLE;
            case REFORGE -> Material.GRINDSTONE;
            case PRESERVE -> Material.ANVIL;
            case SALVAGE -> Material.BARREL;
            case STORAGE -> Material.ENDER_CHEST;
        };
        return makeItem(mat, type.color + type.label, lines);
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
        String action = pdc.get(org.bukkit.NamespacedKey.minecraft(GUI_KEY),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (action == null) return;

        if (ACTION_BACK.equals(action)) {
            state.confirmed = false;
            reopen(() -> openWorkstation(p, state.di, state.type));
            return;
        }
        if (ACTION_SELECT.equals(action)) {
            Integer guiIndex = pdc.get(org.bukkit.NamespacedKey.minecraft(GUI_KEY + "_slot"),
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
            reopen(() -> openWorkstation(p, state.di, state.type));
        }
    }

    /** Render the detail panel for the currently selected item (costs + result preview + confirm/back). */
    private void renderDetail(Player p, State state, Inventory inv) {
        int playerSlot = state.selected;
        ItemStack item = currentItem(p, state, playerSlot);
        if (item == null) {
            p.sendMessage("§cThat item is no longer available.");
            reopen(() -> openWorkstation(p, state.di, state.type));
            return;
        }
        state.selectedFp = fingerprint(item);
        List<String> lines = new ArrayList<>();
        lines.add("§7Selected: §f" + (item.getItemMeta() == null ? item.getType().name()
                : item.getItemMeta().getDisplayName()));
        switch (state.type) {
            case UPGRADE -> {
                int lvl = GearFactory.getUpgradeLevel(item);
                lines.add("§7Current level: §5" + lvl);
                lines.add("§5Next: Lv " + (lvl + 1) + " §7(+"
                        + (int) (WorkstationRules.UPGRADE_STAT_PER_LEVEL * 100) + "% core stat)");
                lines.add("§eCost: " + WorkstationRules.upgradeCoinCost(lvl) + " run coins");
                lines.add("§3Cost: " + WorkstationRules.upgradeShardCost(lvl) + " shards");
            }
            case REFORGE -> {
                List<Affix.AffixRoll> rolled = state.di.previewReforge(item);
                lines.add("§bNew affixes: " + affixSummary(rolled));
                lines.add("§3Cost: " + WorkstationRules.REFORGE_SHARD_COST + " shards");
            }
            case PRESERVE -> {
                lines.add("§dPreserves this item past the run");
                lines.add("§7(§ehalf durability§7).");
                lines.add("§eCost: " + WorkstationRules.PRESERVE_COIN_COST + " run coins");
                lines.add("§3Cost: " + WorkstationRules.PRESERVE_SHARD_COST + " shards");
            }
            case SALVAGE -> {
                int value = WorkstationRules.salvageValue(
                        GearFactory.getRarity(item), WorkstationRules.primaryStat(item));
                lines.add("§b+ " + value + " shards");
                lines.add("§cThis destroys the item!");
            }
            default -> {}
        }
        lines.add("");

        boolean destructive = state.type == WorkstationType.PRESERVE || state.type == WorkstationType.SALVAGE;
        String confirmName = (destructive && state.confirmed)
                ? "§cCONFIRM AGAIN" : "§aCONFIRM";
        List<String> confirmLore = (destructive && state.confirmed)
                ? List.of("§cClick once more to destroy/remove the item.")
                : List.of("§7Apply the operation to the selected item");
        inv.setItem(30, makeItem(Material.LIME_DYE, confirmName, confirmLore, ACTION_CONFIRM));
        inv.setItem(40, makeItem(Material.ARROW, "§7← Back", List.of("§7Back to the item list"), ACTION_BACK));
        inv.setItem(31, makeInfo(state.type)); // keep the info panel anchored
    }

    private ItemStack currentItem(Player p, State state, int playerSlot) {
        if (playerSlot < 0) {
            // equipped armor slot requested via persistentSlots negative marker
            org.bukkit.inventory.EquipmentSlot es = switch (playerSlot) {
                case -1 -> org.bukkit.inventory.EquipmentSlot.HEAD;
                case -2 -> org.bukkit.inventory.EquipmentSlot.CHEST;
                case -3 -> org.bukkit.inventory.EquipmentSlot.LEGS;
                case -4 -> org.bukkit.inventory.EquipmentSlot.FEET;
                default -> null;
            };
            return es == null ? null : p.getInventory().getItem(es);
        }
        return p.getInventory().getItem(playerSlot);
    }

    private void execute(State state, Player p) {
        if (state.selected < 0) return;
        if (state.busy) return;
        state.busy = true;
        int playerSlot = state.selected;
        // Re-validate that the same item the player picked is still in the slot before applying. This
        // prevents a rearranged inventory from upgrading/salvaging a *different* item than was shown.
        ItemStack now = currentItem(p, state, playerSlot);
        if (now == null || !fingerprint(now).equals(state.selectedFp)) {
            p.sendMessage("§cThe selected item changed; please reselect it.");
            return;
        }
        switch (state.type) {
            case UPGRADE -> state.di.tryUpgrade(p, playerSlot);
            case REFORGE -> state.di.tryReforge(p, playerSlot);
            case PRESERVE -> state.di.tryPreserve(p, playerSlot);
            case SALVAGE -> state.di.trySalvage(p, playerSlot);
            case STORAGE -> { /* read-only; nothing to execute */ }
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
                Object v = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
                if (v == null) v = pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER);
                if (v == null) v = pdc.get(key, org.bukkit.persistence.PersistentDataType.DOUBLE);
                if (v != null) sb.append('|').append(key.getKey()).append('=').append(v);
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
            meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(GUI_KEY),
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