package com.lieyabull.dung.listener;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.Run;
import com.lieyabull.dung.pickup.Pickup;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.ui.ChatUI;
import com.lieyabull.dung.dungeon.Floor;
import com.lieyabull.dung.dungeon.RoomGen;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Wires Paper events to the game. Only the active run's player is affected. */
public final class GameListener implements Listener {
    private final Dung plugin;

    public GameListener(Dung plugin) {
        this.plugin = plugin;
    }

    private boolean isInRun(Player p) {
        GameManager gm = plugin.game();
        return gm.isRunning() && gm.player().equals(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!e.getPlayer().hasPlayedBefore()) {
            e.getPlayer().teleport(e.getPlayer().getWorld().getSpawnLocation());
        }
        ChatUI.startPrompt(e.getPlayer());
    }

    /** Stop natural/world mob spawning, but ONLY inside the run's world while a run is active.
     *  The dungeon spawns its own mobs via spawnEntity (CUSTOM), so we suppress stray monsters in
     *  the run world without interfering with other plugins' spawners in other worlds. */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        GameManager gm = plugin.game();
        if (!gm.isRunning()) return;
        if (!e.getEntity().getWorld().equals(gm.world())) return;
        SpawnReason r = e.getSpawnReason();
        if (r == SpawnReason.NATURAL || r == SpawnReason.CHUNK_GEN
                || r == SpawnReason.SPAWNER || r == SpawnReason.REINFORCEMENTS
                || r == SpawnReason.DROWNED || r == SpawnReason.INFECTION
                || r == SpawnReason.NETHER_PORTAL) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!isInRun(p)) return;
        plugin.game().onPlayerMoved(p.getLocation());
    }

    /** Handle death cleanly for the run's player so they never strand on the vanilla screen. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (plugin.game().isRunning() && plugin.game().player().equals(p)) {
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.setDeathMessage(null);
            p.sendMessage("§cYou died.");
            // schedule the run teardown on the next tick to avoid modifying the player mid-event,
            // then kick them off the death screen so they are never trapped on it.
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.game().onDeath();
                if (p.isDead()) p.spigot().respawn();
            });
        }
    }

    /** Always bring a run player back to world spawn. Not gated on `isRunning()` because by the
     *  time they click Respawn on a vanilla death screen, the run has already been torn down. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        GameManager gm = plugin.game();
        if (gm.player() != null && gm.player().equals(p)) {
            gm.endRun(false);
            e.setRespawnLocation(e.getPlayer().getWorld().getSpawnLocation());
            p.setHealth(20);
            p.setGameMode(GameMode.SURVIVAL);
            p.setWalkSpeed((float) 0.2);
        }
    }

    /** Recompute MMORPG stats when the player swaps held item or changes armor. */
    @EventHandler
    public void onHeldItem(org.bukkit.event.player.PlayerItemHeldEvent e) {
        if (isInRun(e.getPlayer())) plugin.game().run().playerState().recomputeStats();
    }

    @EventHandler
    public void onArmor(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && isInRun(p)) {
            plugin.game().run().playerState().recomputeStats();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        GameManager gm = plugin.game();
        if (gm.isRunning() && gm.player().equals(e.getPlayer())) {
            gm.endRun(true);
        }
    }

    /** Left click / attack to fire tears. */
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && isInRun(p)) {
            e.setCancelled(true);
            plugin.game().registerAttack();
        }
    }

    /** Dung mobs/boss are real vanilla mobs whose native AI also attacks the player. Block ALL
     *  vanilla damage they deal (melee + their projectiles) so only Dung's PlayerState-based damage
     *  applies — otherwise the mob's native hit chips the real HP bar and bypasses Dung's HP/defense. */
    @EventHandler(priority = EventPriority.LOW)
    public void onEnemyDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        GameManager gm = plugin.game();
        if (!(gm.isRunning() && gm.player().equals(p))) return;
        if (isDungSource(e.getDamager())) {
            e.setCancelled(true);
        }
    }

    /** True if the damage came from a Dung-created entity, or a projectile fired by one. */
    private boolean isDungSource(org.bukkit.entity.Entity source) {
        if (source == null) return false;
        if (source.getScoreboardTags().contains("dung.entity")) return true;
        if (source instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof org.bukkit.entity.Entity shooter) {
            return shooter.getScoreboardTags().contains("dung.entity");
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isInRun(p)) return;
        // block placing/destroying while in run
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getItem() != null
                && (e.getItem().getType().isBlock() || e.getItem().getType() == Material.TNT)) {
            // allow? bombs are consumables used via sneak; block block-placing
        }
        // ability: sneak + right-click casts the held weapon's stored ability. Gated on the held
        // item carrying a dung.ability tag so vanilla sneak+RMB (place block, eat, offhand) keeps
        // working for everything else — and non-weapon sneaky clicks never spam "no ability".
        ItemStack held = p.getInventory().getItemInMainHand();
        boolean hasAbility = held != null && !held.getType().isAir() && held.getItemMeta() != null
                && held.getItemMeta().getPersistentDataContainer()
                        .has(org.bukkit.NamespacedKey.minecraft(ItemTags.ABILITY),
                             org.bukkit.persistence.PersistentDataType.STRING);
        if (hasAbility && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && p.isSneaking()) {
            e.setCancelled(true);
            plugin.game().tryCastAbility(p, held);
        }
        if (e.getItem() != null) {
            String en = e.getItem().getType().name();
            if (en.endsWith("_HELMET") || en.endsWith("_CHESTPLATE")
                    || en.endsWith("_LEGGINGS") || en.endsWith("_BOOTS")) {
                plugin.game().recomputeStats();
            }
        }
        // shop: right-click the shop block in a SHOP room
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType() == Material.EMERALD_BLOCK) {
            plugin.game().openShop();
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && isInRun(p)) {
            Item it = e.getItem();
            Material m = it.getItemStack().getType();
            if (Pickup.isPickup(m)) {
                e.setCancelled(true);
                GameManager gm = plugin.game();
                PlayerState st = gm.run().playerState();
                if (Pickup.apply(m, st)) {
                    it.remove();
                    ChatUI.notify(p, pickupMsg(m, st));
                }
            }
        }
    }

    private String pickupMsg(Material m, PlayerState st) {
        switch (Pickup.typeOf(m)) {
            case HEART: return "§c+1 Red Heart §7(" + (int) st.hearts + "/" + st.maxHearts + ")";
            case COIN: return "§e+1 Coin §7(" + st.coins + ")";
            case KEY: return "§9+1 Key §7(" + st.keys + ")";
            case BOMB: return "§4+1 Bomb §7(" + st.bombs + ")";
        }
        return "";
    }
}