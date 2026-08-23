package com.lieyabull.dung.listener;

import com.lieyabull.dung.Dung;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Protects the lobby world from modification. Everyone can walk around and interact with GUIs,
 * but blocks are untouchable unless the player has {@code dung.admin} (granted to operators by
 * default). The plots world has its own per-plot protection; runs happen in disposable worlds.
 */
public final class LobbyListener implements Listener {

    /** Permission that bypasses lobby protection (ops have it by default). */
    private static final String ADMIN_PERM = "dung.admin";

    private final Dung plugin;

    public LobbyListener(Dung plugin) {
        this.plugin = plugin;
    }

    private boolean isLobby(org.bukkit.World w) {
        return plugin.worldManager() != null && w.getName().equals(com.lieyabull.dung.world.WorldManager.LOBBY_WORLD_NAME);
    }

    private boolean isAdmin(org.bukkit.entity.Player p) {
        return p.hasPermission(ADMIN_PERM) || p.isOp();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isLobby(e.getBlock().getWorld()) && !isAdmin(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§7The lobby is protected.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isLobby(e.getBlock().getWorld()) && !isAdmin(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§7The lobby is protected.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getBlock() != null && isLobby(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (isLobby(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (isLobby(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (isLobby(e.getBlock().getWorld()) && !isAdmin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (isLobby(e.getBlock().getWorld()) && !isAdmin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && isLobby(e.getEntity().getWorld()) && !isAdmin(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!(e.getRemover() instanceof org.bukkit.entity.Player p)) return;
        if (isLobby(e.getEntity().getWorld()) && !isAdmin(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysical(PlayerInteractEvent e) {
        // Block farmland trampling and other physical grief in the lobby; right-clicks are left
        // alone so players can still use items/GUIs.
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null
                && isLobby(e.getClickedBlock().getWorld()) && !isAdmin(e.getPlayer())) {
            e.setCancelled(true);
        }
    }
}
