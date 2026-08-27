package com.lieyabull.dung.ui;

import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.Rarity;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The /troll menu — a small OP-only chest GUI for handing out joke/admin items.
 * Holds the Lightning Wand (calls lightning on targets) and the Fling Wand (flings
 * a looked-at player up and away with a harmless boom).
 */
public final class TrollUI implements Listener {

    private static final int MENU_SIZE = 9;
    private static final int WAND_SLOT = 2;
    private static final int FLING_SLOT = 6;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Map<Inventory, UUID> openMenus = new ConcurrentHashMap<>();

    /** Build the Lightning Wand — a magic staff flagged as a Dung weapon (never a normal drop). */
    public static ItemStack lightningWand() {
        ItemStack s = GearFactory.weapon("lightning_wand", "Lightning Wand",
                Material.BLAZE_ROD, Rarity.MYTHIC, 1, 0, "Lightning", 30);
        s = GearFactory.withMagicDamage(s, 40);
        s = GearFactory.withReach(s, 5.0);
        s = GearFactory.markPersistent(s);
        // Give it a durability pool so it behaves like a real persistent weapon and can't be
        // thrown away accidentally via salvage (it's starred, so normal salvage skips it).
        GearFactory.initDurability(s);
        return s;
    }

    /** Build the Fling Wand — a troll wand that flings the player you're looking at up and away.
     *  It is a standalone item (not Dung gear) handled by {@code TrollWandListener}. */
    public static ItemStack flingWand() {
        ItemStack s = new ItemStack(Material.STICK);
        s.editMeta(meta -> {
            meta.setDisplayName("§e§lFling Wand");
            meta.setLore(java.util.List.of(
                    "§7Sneak + Right-click while looking at a player",
                    "§7to fling them up and away with a harmless boom."));
            var pdc = meta.getPersistentDataContainer();
            pdc.set(org.bukkit.NamespacedKey.minecraft("dung.trolleffect"),
                    org.bukkit.persistence.PersistentDataType.STRING, "fling");
        });
        return s;
    }

    /** Open the troll menu GUI for a player (caller must already have checked OP). */
    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE, LEGACY.deserialize("§4§lTroll Menu"));
        inv.setItem(WAND_SLOT, lightningWand());
        inv.setItem(FLING_SLOT, flingWand());
        // Decorate empty slots with dark filler to clearly mark the usable slots.
        for (int i = 0; i < MENU_SIZE; i++) {
            if (i == WAND_SLOT || i == FLING_SLOT) continue;
            inv.setItem(i, filler());
        }
        openMenus.put(inv, p.getUniqueId());
        p.openInventory(inv);
    }

    private static ItemStack filler() {
        ItemStack f = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        f.editMeta(meta -> meta.setDisplayName(" "));
        return f;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        UUID owner = openMenus.get(e.getInventory());
        if (owner == null) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        if (!p.getUniqueId().equals(owner)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return; // filler
        ItemStack give = clicked.clone();
        if (GearFactory.isGear(give)) {
            GearFactory.localizeFor(give, p);
        }
        p.getInventory().addItem(give);
        p.sendMessage("§aYou received §6" + give.getItemMeta().getDisplayName() + "§a.");
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (openMenus.containsKey(e.getInventory())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        openMenus.remove(e.getInventory());
    }
}
