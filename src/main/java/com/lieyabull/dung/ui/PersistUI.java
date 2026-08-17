package com.lieyabull.dung.ui;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.PlayerState;
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
 * The "Persist Master" GUI found in UPGRADE rooms (every 5 floors). Shows the player's run gear
 * and lets them pay 50 run coins + 200 persistent coins + 300 shards to try persisting an item
 * past the current run. 40% success delivers the item after the run as persistent half-durability
 * gear; 60% fail returns the item one rarity worse.
 */
public final class PersistUI implements Listener {

    private static final int PERSIST_SIZE = 36;

    // GUI identifiers stored in inventory ItemMeta
    private static final String GUI_KEY = "dung_persist";
    private static final String GUI_PERSIST = "persist";
    private static final String TAG_SLOT = "dung_persist_slot";

    // Persist attempt cost
    private static final int COIN_COST = 50;
    private static final int PC_COST = 200;
    private static final int SHARD_COST = 300;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // Track which GUIs are open (keyed by inventory) and map each GUI slot -> player inventory slot
    private final Map<Inventory, String> openGuis = new ConcurrentHashMap<>();
    private final Map<Inventory, int[]> persistSlots = new ConcurrentHashMap<>();

    private final Dung plugin;

    public PersistUI(Dung plugin) {
        this.plugin = plugin;
    }

    /** Open the Persist Master GUI for a player inside an UPGRADE room. */
    public void openPersist(Player p, DungeonInstance di) {
        PlayerState st = di.playerStateOf(p);
        if (st == null) return;
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, PERSIST_SIZE,
                LEGACY.deserialize("§8Persist  §e" + st.coins + " coins  §6" + prof.persistentCoins
                        + " pc  §3" + prof.shards + " shards"));

        List<Integer> slots = di.persistableSlots(p);
        int[] guiToPlayer = new int[PERSIST_SIZE];
        java.util.Arrays.fill(guiToPlayer, -1);
        for (int i = 0; i < slots.size() && i < PERSIST_SIZE; i++) {
            int playerSlot = slots.get(i);
            final int guiIndex = i;
            ItemStack gear = p.getInventory().getItem(playerSlot);
            ItemStack display = gear.clone();
            display.editMeta(meta -> meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(TAG_SLOT),
                    org.bukkit.persistence.PersistentDataType.INTEGER, guiIndex));
            inv.setItem(i, display);
            guiToPlayer[i] = playerSlot;
        }

        // Cost / odds info panel
        inv.setItem(27, makeInfo(Material.ANVIL, "§dTry to Persist",
                List.of("§7Pay to attempt persisting an item",
                        "§7so it survives past this run.",
                        "",
                        "§e" + COIN_COST + " run coins",
                        "§6" + PC_COST + " persistent coins",
                        "§3" + SHARD_COST + " shards",
                        "",
                        "§a40% Success §7→ delivered after the run",
                        "§7   (persistent, §ehalf durability§7)",
                        "§c60% Fail §7→ returned one rarity worse")));

        fillEmpty(inv);
        openGuis.put(inv, GUI_PERSIST);
        persistSlots.put(inv, guiToPlayer);
        p.openInventory(inv);
    }

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
        Integer guiSlot = meta.getPersistentDataContainer().get(
                org.bukkit.NamespacedKey.minecraft(TAG_SLOT),
                org.bukkit.persistence.PersistentDataType.INTEGER);
        if (guiSlot == null) return;

        int[] guiToPlayer = persistSlots.get(e.getInventory());
        if (guiToPlayer == null || guiSlot < 0 || guiSlot >= guiToPlayer.length) return;
        int playerSlot = guiToPlayer[guiSlot];
        if (playerSlot < 0) return;

        DungeonInstance di = plugin.game().instanceOf(p);
        if (di == null) return;
        di.tryPersist(p, playerSlot);
        reopen(() -> openPersist(p, di)); // refresh
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player) {
            openGuis.remove(e.getInventory());
            persistSlots.remove(e.getInventory());
        }
    }

    /** Cancel drags within a tracked GUI so items can't be pulled out or swapped in. */
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player && openGuis.get(e.getInventory()) != null) {
            e.setCancelled(true);
        }
    }

    /** Defer a GUI open by one tick (see ShopUI#reopen for why). */
    private void reopen(Runnable open) {
        Bukkit.getScheduler().runTask(plugin, open);
    }

    private ItemStack makeInfo(Material mat, String name, List<String> lore) {
        ItemStack s = new ItemStack(mat);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) loreComponents.add(LEGACY.deserialize(line));
        meta.lore(loreComponents);
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
