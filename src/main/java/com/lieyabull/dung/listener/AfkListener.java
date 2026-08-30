package com.lieyabull.dung.listener;

import com.lieyabull.dung.Dung;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AFK detection. A player who performs no activity (movement, chat, clicks, slot changes, drops,
 * damage in/out) for {@link #AFK_MILLIS} (3 minutes) is announced in chat in grey and gets a
 * floating {@code [AFK]} tag riding above their head. Any activity removes the tag. A player can
 * also opt in immediately with {@code /afk}; manual AFK is <b>not a toggle</b> — it ends the
 * moment the player acts (moves, chats, clicks, drops, changes slots, is involved in damage).
 *
 * <p>The "is now AFK" line is broadcast exactly once per AFK session. The one-second sweep
 * re-validates/re-creates the tag, but the announcement set guarantees it cannot repeat-announce
 * while a player stays AFK.</p>
 *
 * <p>The tag is a non-persistent TextDisplay riding the player, mirroring the run's overhead HP
 * readout ({@code DungeonInstance.updateHeadHp}) — a player's own {@code setCustomName} cannot
 * change their overhead nametag, so the classic approach does not render for players.</p>
 *
 * <p>While flagged AFK a player cannot be damaged by entities (their event is cancelled at
 * HIGHEST, which also suppresses knockback) and is skipped as a target by dungeon mobs, so AFK
 * players are neither attacked nor shoved around during a run or in the plots world.</p>
 */
public final class AfkListener implements Listener, CommandExecutor {

    /** Idle time before a player is flagged AFK. */
    private static final long AFK_MILLIS = 3 * 60 * 1000L;
    /** How often the AFK sweep runs (1 second). */
    private static final long SWEEP_INTERVAL_TICKS = 20L;
    /** Vertical lift of the [AFK] tag above the head (a passenger display renders at head height). */
    private static final float TAG_LIFT = 0.5f;

    private final Dung plugin;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    /** Live [AFK] tag per player, present only while the player is AFK. */
    private final Map<UUID, TextDisplay> tags = new ConcurrentHashMap<>();
    /** Players who ran {@code /afk}: AFK until they act (any move/chat/click ends it). */
    private final Set<UUID> manualAfk = ConcurrentHashMap.newKeySet();
    /** Players whose "is now AFK" has already been broadcast this AFK session, so the one-second
     *  sweep can silently repair a lost tag without re-announcing the same player. */
    private final Set<UUID> announced = ConcurrentHashMap.newKeySet();

    public AfkListener(Dung plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep,
                SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (manualAfk.contains(id)) {
                markAfk(p); // /afk players stay tagged no matter how much they move
                continue;
            }
            if (now - lastActivity.getOrDefault(id, now) >= AFK_MILLIS) {
                markAfk(p);
            } else {
                clearAfk(p);
            }
        }
    }

    /** True while {@code p} is flagged AFK (manually via {@code /afk} or by idle timeout). */
    public boolean isAfk(Player p) {
        return p != null && (manualAfk.contains(p.getUniqueId()) || tags.containsKey(p.getUniqueId()));
    }

    /** Put a player into {@code /afk}. Not a toggle: re-running {@code /afk} keeps them AFK, and any
     *  action (movement, chat, clicks, …) ends it for them. */
    public boolean enterManual(Player p) {
        if (manualAfk.add(p.getUniqueId())) {
            markAfk(p);
            p.sendMessage("§7You are now AFK. Move, chat, or act to end it.");
            return true;
        }
        p.sendMessage("§7You are already AFK. Move, chat, or act to end it.");
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player p) enterManual(p);
        return true;
    }

    /** Show the [AFK] tag above the head and announce it exactly once per AFK session. Re-validating
     *  an existing tag returns early; a lost tag is re-created silently, never re-announced. */
    private void markAfk(Player p) {
        UUID id = p.getUniqueId();
        boolean first = announced.add(id); // false once this player is already in an AFK session
        TextDisplay tag = tags.get(id);
        if (tag != null) {
            if (tag.isValid() && p.getPassengers().contains(tag)) return; // already fully marked
            p.removePassenger(tag);
            tag.remove();
        }
        if (first) {
            Bukkit.broadcastMessage("§7" + p.getName() + " is now AFK");
        }
        TextDisplay fresh = p.getWorld().spawn(p.getLocation(), TextDisplay.class, td -> {
            td.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize("§7[AFK]"));
            td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            td.setSeeThrough(false);
            td.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0f, TAG_LIFT, 0f), // translation
                    new org.joml.Quaternionf(),               // left rotation (none)
                    new org.joml.Vector3f(1f, 1f, 1f),        // scale
                    new org.joml.Quaternionf()));             // right rotation (none)
            td.setViewRange(0.6f);
            td.setPersistent(false);
        });
        tags.put(id, fresh);
        p.addPassenger(fresh);
    }

    /** Remove the [AFK] tag; the sweep calls this whenever the player is active again. */
    private void clearAfk(Player p) {
        announced.remove(p.getUniqueId());
        TextDisplay tag = tags.remove(p.getUniqueId());
        if (tag == null) return;
        p.removePassenger(tag);
        tag.remove();
    }

    /** Record activity; if the player is in {@code /afk}, any action cancels it. */
    private void markActive(Player p) {
        lastActivity.put(p.getUniqueId(), System.currentTimeMillis());
        releaseManual(p);
    }

    /** End manual AFK (no-op unless the player was in {@code /afk}). */
    private void releaseManual(Player p) {
        if (!manualAfk.remove(p.getUniqueId())) return;
        clearAfk(p);
        p.sendMessage("§7You are no longer AFK.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        markActive(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastActivity.remove(id);
        manualAfk.remove(id);
        clearAfk(e.getPlayer());
    }

    /** Leaving the world drops the passenger tag; the sweep re-adds it in the destination world. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        clearAfk(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        markActive(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        markActive(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHoldItem(PlayerItemHeldEvent e) {
        markActive(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDropItem(PlayerDropItemEvent e) {
        markActive(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        lastActivity.put(p.getUniqueId(), System.currentTimeMillis()); // CHM write — safe off-thread
        // releaseManual touches entities and chat, so defer it to the main thread.
        Bukkit.getScheduler().runTask(plugin, () -> releaseManual(p));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p) markActive(p);
        if (e.getDamager() instanceof Player p) markActive(p);
    }

    /** AFK players cannot be damaged by entities; cancelling also suppresses any knockback. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamagedWhileAfk(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p && isAfk(p)) e.setCancelled(true);
    }
}