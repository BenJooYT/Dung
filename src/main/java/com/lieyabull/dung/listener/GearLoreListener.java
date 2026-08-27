package com.lieyabull.dung.listener;

import com.lieyabull.dung.items.GearFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Localizes gear item lore the moment it enters a player's inventory. Ground drops are created
 * (and may sit unpicked) in English; when a player picks one up, this rewrites its lore to match
 * the picker's selected UI language. Items added directly to an inventory (shop roll, starter kit)
 * are localized at their creation site instead.
 */
public final class GearLoreListener implements Listener {

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack s = e.getItem().getItemStack();
        if (s == null || !GearFactory.isGear(s)) return;
        GearFactory.localizeFor(s, p);
    }
}
