package com.lieyabull.dung.listener;

import com.lieyabull.dung.Dung;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Handles troll-admin wands handed out from the /troll menu. These work outside a dungeon run,
 * so they are processed before the run-gated interaction logic.
 *
 * Fling Wand: sneak + right-click while looking at a player to fling that player up and away with
 * a harmless (no-damage) explosion. Only the chosen player is knocked back.
 */
public final class TrollWandListener implements Listener {

    private static final double FLING_DISTANCE = 40.0;

    private final Dung plugin;

    public TrollWandListener(Dung plugin) {
        this.plugin = plugin;
    }

    private static boolean isFlingWand(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        String v = pdc.get(org.bukkit.NamespacedKey.minecraft("dung.trolleffect"),
                org.bukkit.persistence.PersistentDataType.STRING);
        return "fling".equals(v);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player caster = e.getPlayer();
        ItemStack held = caster.getInventory().getItemInMainHand();
        if (!isFlingWand(held)) return;
        e.setCancelled(true);
        if (!caster.isSneaking()) return;
        flingTarget(caster);
    }

    private void flingTarget(Player caster) {
        Player target = targetedPlayer(caster);
        if (target == null) {
            caster.sendMessage("§cNo player in your sights.");
            return;
        }
        Location casterLoc = caster.getLocation();
        Location targetLoc = target.getLocation();
        // A explosive-looking but harmless boom (power 0, no fire, no block breaking: no damage).
        target.getWorld().createExplosion(targetLoc, 0F, false, false);
        // Fling the chosen player up and away from the caster (only they get knocked back).
        Vector away = targetLoc.toVector().subtract(casterLoc.toVector()).setY(0);
        if (away.lengthSquared() < 0.01) away = caster.getEyeLocation().getDirection().setY(0);
        away.normalize();
        target.setVelocity(away.multiply(2.0).setY(1.6));
        caster.sendMessage("§eFlinged §f" + target.getName() + "§e §7✨");
    }

    private Player targetedPlayer(Player caster) {
        RayTraceResult result = caster.getWorld().rayTraceEntities(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                FLING_DISTANCE,
                0.4,
                entity -> entity instanceof Player && !entity.equals(caster));
        Entity hit = result == null ? null : result.getHitEntity();
        return hit instanceof Player p ? p : null;
    }
}
