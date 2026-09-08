package com.lieyabull.dung.ui;

import com.lieyabull.dung.game.CombatPower;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.Run;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.lang.Lang;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only Combat Power breakdown chest GUI (design section 11): total CP plus one row per
 * source (best weapon, armor, shields, other gear, permanent upgrades, shop tonics) and the
 * run's locked difficulty modifier. Opened with {@code /dung cp}.
 */
public final class CpBreakdownUI implements Listener {
    private static final int SIZE = 27;
    /** Open breakdown GUI -> viewing player, so clicks/drags can be cancelled safely. */
    private final Map<Inventory, UUID> open = new ConcurrentHashMap<>();

    /** Open the breakdown for a player currently in a run. */
    public void open(Player p, PlayerState st, Run run) {
        if (p == null || st == null) return;
        String title = Lang.forPlayer(p, "cp.title", p.getName());
        Inventory inv = org.bukkit.Bukkit.createInventory(p, SIZE,
                LegacyComponentSerializer.legacySection().deserialize(title));
        ItemStack blank = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, blank);

        String bestWeaponName = bestWeaponName(p);
        inv.setItem(10, pane(Material.NETHER_STAR,
                Lang.forPlayer(p, "cp.total", (int) st.combatPower),
                Lang.forPlayer(p, "cp.weapon", bestWeaponName, (int) st.cpWeapon),
                Lang.forPlayer(p, "cp.armor", (int) st.cpArmor)));
        inv.setItem(11, pane(Material.IRON_SWORD,
                Lang.forPlayer(p, "cp.weapon", bestWeaponName, (int) st.cpWeapon)));
        inv.setItem(12, pane(Material.IRON_CHESTPLATE,
                Lang.forPlayer(p, "cp.armor", (int) st.cpArmor)));
        inv.setItem(13, pane(Material.SHIELD,
                Lang.forPlayer(p, "cp.shields", (int) st.cpShields)));
        inv.setItem(14, pane(Material.PAPER,
                Lang.forPlayer(p, "cp.other", (int) st.cpOther)));
        inv.setItem(15, pane(Material.EMERALD,
                Lang.forPlayer(p, "cp.upgrades", (int) st.cpUpgrades)));
        inv.setItem(16, pane(Material.POTION,
                Lang.forPlayer(p, "cp.tonics", (int) st.cpTonics)));
        double modPct = run == null ? 0.0 : run.cpModifier * 100.0;
        String sign = modPct > 0 ? "+" : "";
        inv.setItem(22, pane(Material.BOOK,
                Lang.forPlayer(p, "cp.mod", sign + (int) Math.round(modPct)),
                Lang.forPlayer(p, "cp.hint")));
        open.put(inv, p.getUniqueId());
        p.openInventory(inv);
    }

    /** Display name of the player's strongest usable weapon (falls back to "—" when unarmed). */
    private static String bestWeaponName(Player p) {
        double best = -1.0;
        String name = "—";
        List<ItemStack> all = new ArrayList<>();
        Collections.addAll(all, p.getInventory().getStorageContents());
        Collections.addAll(all, p.getInventory().getArmorContents());
        all.add(p.getInventory().getItemInOffHand());
        for (ItemStack s : all) {
            if (s == null || s.getType().isAir()) continue;
            if (!"weapon".equals(GearFactory.kindOfPublic(s)) || GearFactory.isBroken(s)) continue;
            double cp = CombatPower.itemCp(s);
            if (cp > best) {
                best = cp;
                ItemMeta meta = s.getItemMeta();
                name = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : s.getType().name();
            }
        }
        return name;
    }

    private static ItemStack pane(Material mat, String name, String... lore) {
        ItemStack s = new ItemStack(mat);
        s.editMeta(meta -> {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(List.of(lore));
        });
        return s;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (open.containsKey(e.getInventory())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (open.containsKey(e.getInventory())) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        open.remove(e.getInventory());
    }
}
