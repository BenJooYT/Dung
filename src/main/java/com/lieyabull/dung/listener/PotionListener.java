package com.lieyabull.dung.listener;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.plot.PlotManager;
import com.lieyabull.dung.plot.ProvenanceManager;
import com.lieyabull.dung.plot.potion.PotionAnimation;
import com.lieyabull.dung.plot.potion.PotionDefinition;
import com.lieyabull.dung.plot.potion.PropagationEngine;
import com.lieyabull.dung.plot.potion.PropagationResult;
import com.lieyabull.dung.plot.potion.PotionFactory;
import com.lieyabull.dung.ui.StashUI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Handles transformation potion throwing, splash detection, and propagation.
 * Potions are thrown by the player and detected via persistent data tags.
 */
public final class PotionListener implements Listener {

    private static final String POTION_TAG = "dung.potion";
    /** Per-player convert toggle: true = player-placed blocks may be transformed. */
    private final Map<UUID, Boolean> convertToggles = new HashMap<>();
    private final Dung plugin;

    public PotionListener(Dung plugin) {
        this.plugin = plugin;
    }

    /** Check if the item stack is a Dung transformation potion. */
    private static String getPotionId(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(org.bukkit.NamespacedKey.minecraft(POTION_TAG), PersistentDataType.STRING);
    }

    /** Get the potion definition by ID. */
    private static PotionDefinition getDefinition(String id) {
        return switch (id) {
            case "forest" -> PotionDefinition.FOREST;
            case "stone" -> PotionDefinition.STONE;
            default -> null;
        };
    }

    /** Tag a potion item with a Dung potion type. */
    public static void tagPotion(ItemStack stack, String potionId) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.minecraft(POTION_TAG),
                PersistentDataType.STRING, potionId);
        meta.getPersistentDataContainer().set(
                org.bukkit.NamespacedKey.minecraft("dung.item"),
                PersistentDataType.BOOLEAN, true);
        stack.setItemMeta(meta);
    }

    /** Check if a stack is a Dung potion. */
    public static boolean isPotion(ItemStack stack) {
        return getPotionId(stack) != null;
    }

    /** Track the projectile so we can identify it when it splashes. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof ThrownPotion potion)) return;
        if (!(e.getEntity().getShooter() instanceof Player player)) return;

        // Check if the potion item has a Dung potion tag
        ItemStack item = potion.getItem();
        String potionId = getPotionId(item);
        if (potionId == null) return;

        // Tag the projectile entity with the potion type so we can find it on splash
        potion.getScoreboardTags().add("dung.potion." + potionId);

        // Store the thrower UUID in metadata
        potion.setMetadata("dung.thrower",
                new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
    }

    /** Handle potion splash: compute propagation and animate. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent e) {
        ThrownPotion potion = e.getEntity();

        // Find the potion type from scoreboard tags
        String potionId = null;
        for (String tag : potion.getScoreboardTags()) {
            if (tag.startsWith("dung.potion.")) {
                potionId = tag.substring("dung.potion.".length());
                break;
            }
        }
        if (potionId == null) return;

        PotionDefinition definition = getDefinition(potionId);
        if (definition == null) return;

        // Find the thrower
        UUID throwerId = null;
        if (potion.hasMetadata("dung.thrower")) {
            throwerId = UUID.fromString(potion.getMetadata("dung.thrower").get(0).asString());
        }
        if (throwerId == null) return;

        Player player = plugin.getServer().getPlayer(throwerId);
        if (player == null) return;

        // Validate the player is on their own plot
        Location impact = potion.getLocation();
        PlotManager pm = plugin.plotManager();
        PlotManager.PlotCoord coord = pm.plotAt(impact);
        if (coord == null) {
            player.sendMessage("§cYou can only use potions on your own plot.");
            returnPotion(player, definition);
            return;
        }

        // Check plot ownership
        if (!pm.ownsPlotByOwner(coord, throwerId)) {
            player.sendMessage("§cYou can only use potions on your own plot.");
            returnPotion(player, definition);
            return;
        }

        // Check convert toggle
        boolean convertEnabled = convertToggles.getOrDefault(throwerId, false);

        // Diagnose why a splash might find nothing, so failures are actionable.
        Block impactBlock = impact.getBlock();
        ProvenanceManager provDiag = plugin.provenanceManager();
        if (PropagationEngine.drillToTarget(impactBlock, definition) == null) {
            player.sendMessage("§7No " + (definition.id().equals("stone") ? "§fstone/ores"
                    : "§flogs/leaves") + " §7found here — dig to expose some first.");
            returnPotion(player, definition);
            return;
        }
        Block target = PropagationEngine.drillToTarget(impactBlock, definition);
        if (provDiag.isPlayerPlaced(target) && !convertEnabled) {
            player.sendMessage("§7That block was placed by a player — enable §f/convert§7 to transform it.");
            returnPotion(player, definition);
            return;
        }
        if (!pm.isBuildableArea(target.getLocation())) {
            player.sendMessage("§7That spot is plot infrastructure (border/path) — aim inside the plot.");
            returnPotion(player, definition);
            return;
        }

        // Run propagation
        World world = impact.getWorld();
        if (world == null) return;

        ProvenanceManager prov = plugin.provenanceManager();
        Random rng = new Random();

        PropagationResult result = PropagationEngine.propagate(
                world, impact, definition, throwerId, convertEnabled,
                block -> prov.isPlayerPlaced(block),
                pm, rng
        );

        if (result.noValidTargets()) {
            player.sendMessage("§7The potion found no valid targets on your plot.");
            returnPotion(player, definition);
            return;
        }

        // Start animation
        PotionAnimation.animate(plugin, result, definition, player);

        // Notify
        int count = result.totalTransformed();
        player.sendMessage("§a" + definition.displayName() + " §atransformed §f" + count
                + " §ablock" + (count == 1 ? "" : "s") + "!");
    }

    /** Return a potion to the player's stash when no valid targets are found. */
    private void returnPotion(Player player, PotionDefinition definition) {
        // Create a new potion item and put it in the stash
        ItemStack refund = PotionFactory.createPotion(definition);
        StashUI.placeOrStash(player, refund);
        player.sendMessage("§7The potion was returned to your stash.");
    }

    // ==================== CONVERT TOGGLE ====================

    /** Get the convert toggle for a player (true = player-placed blocks may be transformed). */
    public boolean isConvertEnabled(UUID playerId) {
        return convertToggles.getOrDefault(playerId, false);
    }

    /** Set the convert toggle for a player. Returns the new state. */
    public boolean toggleConvert(UUID playerId) {
        boolean current = convertToggles.getOrDefault(playerId, false);
        boolean newState = !current;
        convertToggles.put(playerId, newState);
        return newState;
    }

    /** Explicitly set the convert toggle. */
    public void setConvertEnabled(UUID playerId, boolean enabled) {
        convertToggles.put(playerId, enabled);
    }
}