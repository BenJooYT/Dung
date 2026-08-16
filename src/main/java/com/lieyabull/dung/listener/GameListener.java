package com.lieyabull.dung.listener;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.Run;
import com.lieyabull.dung.pickup.Pickup;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.ui.ChatUI;
import com.lieyabull.dung.dungeon.Floor;
import com.lieyabull.dung.dungeon.RoomGen;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Wires Paper events to the game. Routes events to the correct dungeon instance per player. */
public final class GameListener implements Listener {
    private final Dung plugin;

    public GameListener(Dung plugin) {
        this.plugin = plugin;
    }

    private boolean isInRun(Player p) {
        return plugin.game().isInInstance(p);
    }

    private DungeonInstance instanceOf(Player p) {
        return plugin.game().instanceOf(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPlayedBefore()) {
            p.teleport(p.getWorld().getSpawnLocation());
            ChatUI.startPrompt(p);
            return;
        }
        // Restore the player's last known location (e.g. plots world) instead of always
        // sending them to the main world spawn. The location is saved on quit.
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        if (prof.lastWorld != null) {
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(prof.lastWorld);
            if (w != null) {
                p.teleport(new org.bukkit.Location(w, prof.lastX, prof.lastY, prof.lastZ, prof.lastYaw, prof.lastPitch));
            }
        }
        ChatUI.startPrompt(p);
    }

    /** Stop natural/world mob spawning, but ONLY inside the run's world while a run is active.
     *  The dungeon spawns its own mobs via spawnEntity (CUSTOM), so we suppress stray monsters in
     *  the run world without interfering with other plugins' spawners in other worlds. */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        // Check all active instances for world match
        for (DungeonInstance inst : plugin.game().instances()) {
            if (inst.world() != null && e.getEntity().getWorld().equals(inst.world())) {
                SpawnReason r = e.getSpawnReason();
                if (r == SpawnReason.NATURAL || r == SpawnReason.SPAWNER || r == SpawnReason.REINFORCEMENTS
                        || r == SpawnReason.DROWNED || r == SpawnReason.INFECTION
                        || r == SpawnReason.NETHER_PORTAL) {
                    e.setCancelled(true);
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        DungeonInstance di = instanceOf(p);
        if (di == null) return;
        di.onPlayerMoved(p, p.getLocation());
    }

    /** Handle death cleanly for the run's player so they never strand on the vanilla screen.
     *  Dead players are set to SPECTATOR mode by onPlayerDeath and can be revived when the
     *  boss is defeated, so we no longer call spigot().respawn() — that would undo the
     *  spectator state. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getEntity();
        DungeonInstance di = instanceOf(p);
        if (di != null) {
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.setDeathMessage(null);
            p.sendMessage("§cYou died.");
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                di.onPlayerDeath(p);
            });
        }
    }

    /** Handle respawn for a run player. Since dead players are now set to SPECTATOR mode
     *  (not removed from the instance), a vanilla respawn event should only trigger if the
     *  player somehow bypassed the spectator path. Set them to spectator and keep them in
     *  the instance so they can be revived on boss defeat.
     *  If the player has already been revived (not in deadPlayers), restore SURVIVAL mode
     *  instead of overriding it back to SPECTATOR. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        DungeonInstance di = instanceOf(p);
        if (di != null) {
            e.setRespawnLocation(e.getPlayer().getWorld().getSpawnLocation());
            if (di.isDead(p.getUniqueId())) {
                p.setGameMode(GameMode.SPECTATOR);
            } else {
                p.setGameMode(GameMode.SURVIVAL);
            }
            p.setHealth(20);
            p.setWalkSpeed((float) 0.2);
        }
    }

    /** Recompute MMORPG stats when the player swaps held item or changes armor. */
    @EventHandler
    public void onHeldItem(org.bukkit.event.player.PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        DungeonInstance di = instanceOf(p);
        if (di != null) di.recomputeStats();
    }

    @EventHandler
    public void onArmor(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            DungeonInstance di = instanceOf(p);
            if (di != null) di.recomputeStats();
        }
    }

    /** Prevent key/bomb run items from being moved, shift-clicked, or dragged out of their hotbar
     *  slots. They are visual-only synced from PlayerState; letting them move lets syncHotbarItems
     *  spawn a fresh copy in the slot, desyncing the counter from the physical item and duplicating it. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        DungeonInstance di = instanceOf(p);
        if (di == null) return;
        if (DungeonInstance.isRunItem(e.getCurrentItem()) || DungeonInstance.isRunItem(e.getCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        DungeonInstance di = instanceOf(p);
        if (di == null) return;
        if (DungeonInstance.isRunItem(e.getOldCursor()) || DungeonInstance.isRunItem(e.getCursor())
                || e.getNewItems().values().stream().anyMatch(DungeonInstance::isRunItem)) {
            e.setCancelled(true);
        }
    }

    /** Keys and bombs are materials that can be placed as blocks (TRIPWIRE_HOOK / TNT). Placing one
     *  decrements the held item, but the next-tick hotbar sync re-creates the copy — leaving the
     *  placed block + a restored stack, which can be broken to farm free items (duplication). Cancel
     *  placement entirely so a run item can never leave the hotbar as a block. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        Player p = e.getPlayer();
        DungeonInstance di = instanceOf(p);
        if (di == null) return;
        if (DungeonInstance.isRunItem(e.getItemInHand())) {
            e.setCancelled(true);
            p.sendActionBar("§cKeys and bombs stay in your hotbar!");
        }
    }

    /** Belt-and-suspenders: forbid breaking key/bomb-material blocks while in a run, so a copy that
     *  somehow reached the world (e.g. placed before this guard) can't be re-collected into a stack. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        Player p = e.getPlayer();
        DungeonInstance di = instanceOf(p);
        if (di == null) return;
        org.bukkit.Material t = e.getBlock().getType();
        if (t == Material.TNT || t == Material.TRIPWIRE_HOOK) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        // Save the player's current location so onJoin can restore it (e.g. plots world).
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        org.bukkit.Location loc = p.getLocation();
        prof.lastWorld = loc.getWorld().getName();
        prof.lastX = loc.getX();
        prof.lastY = loc.getY();
        prof.lastZ = loc.getZ();
        prof.lastYaw = loc.getYaw();
        prof.lastPitch = loc.getPitch();
        plugin.meta().save();
        // Party cleanup first (removes p from the party), so removePlayer can detect an empty party
        // and end the run. The shared run continues for the rest of the party otherwise.
        plugin.game().partyManager().onPlayerQuit(p);
        DungeonInstance di = instanceOf(p);
        if (di != null) {
            di.removePlayer(p);
        }
    }

    /** Sneak + drop (Q) casts the player's class-specific active ability.
     *  Non-sneak drop of key/bomb run items is also cancelled to keep them locked. */
    @EventHandler
    public void onDropItem(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        DungeonInstance di = instanceOf(p);
        if (di == null) return;
        // Prevent dropping key/bomb run items
        if (DungeonInstance.isRunItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            return;
        }
        if (!p.isSneaking()) return;
        e.setCancelled(true);
        di.tryCastClassAbility(p);
    }

    /** Left click / attack to fire tears. */
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            DungeonInstance di = instanceOf(p);
            if (di != null) {
                e.setCancelled(true);
                di.registerAttack(p);
            }
        }
    }

    /** Dung mobs/boss are real vanilla mobs whose native AI also attacks the player. Block ALL
     *  vanilla damage they deal (melee + their projectiles) so only Dung's PlayerState-based damage
     *  applies — otherwise the mob's native hit chips the real HP bar and bypasses Dung's HP/defense. */
    @EventHandler(priority = EventPriority.LOW)
    public void onEnemyDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!isInRun(p)) return;
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
        DungeonInstance di = instanceOf(p);
        if (di == null) return;
        // ability: sneak + right-click casts the held weapon's stored ability.
        // Only process the main hand — the event fires separately for each hand, and processing
        // the offhand would attempt the ability a second time (after mana was spent / cooldown
        // started), producing a spurious "Not enough mana or on cooldown" message.
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        ItemStack held = p.getInventory().getItemInMainHand();
        boolean hasAbility = held != null && !held.getType().isAir() && held.getItemMeta() != null
                && held.getItemMeta().getPersistentDataContainer()
                        .has(org.bukkit.NamespacedKey.minecraft(ItemTags.ABILITY),
                             org.bukkit.persistence.PersistentDataType.STRING);
        if (hasAbility && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && p.isSneaking()) {
            e.setCancelled(true);
            di.tryCastAbility(p, held);
            return;
        }
        // pedestal: right-click a pedestal slab to claim the item
        // Check this BEFORE the armor-equip check so that holding an armor piece while
        // right-clicking a pedestal doesn't equip the armor — we cancel the event early.
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType() == Material.POLISHED_BLACKSTONE_SLAB) {
            if (di.claimPedestal(p, e.getClickedBlock().getLocation())) {
                e.setCancelled(true);
                return;
            }
        }
        if (e.getItem() != null) {
            String en = e.getItem().getType().name();
            if (en.endsWith("_HELMET") || en.endsWith("_CHESTPLATE")
                    || en.endsWith("_LEGGINGS") || en.endsWith("_BOOTS")) {
                di.recomputeStats();
            }
        }
        // locked room: right-click an IRON_BLOCK barrier with a key item
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType() == Material.IRON_BLOCK) {
            ItemStack heldItem = p.getInventory().getItemInMainHand();
            if (DungeonInstance.isKeyItem(heldItem)) {
                e.setCancelled(true);
                di.tryUnlockRoom(p, e.getClickedBlock().getLocation());
                return;
            }
        }
        // bomb wall: right-click a destructible wall (CRACKED_STONE_BRICKS) with a bomb item
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType() == Material.CRACKED_STONE_BRICKS) {
            ItemStack heldItem = p.getInventory().getItemInMainHand();
            if (DungeonInstance.isBombItem(heldItem)) {
                e.setCancelled(true);
                di.tryBombWall(p, e.getClickedBlock().getLocation());
            }
        }
    }

    /** Right-click an item frame on a pedestal to claim the item. */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        DungeonInstance di = instanceOf(p);
        if (di == null) return;
        if (e.getRightClicked() instanceof ItemFrame frame) {
            Location frameLoc = frame.getLocation();
            Location slabLoc = new Location(frameLoc.getWorld(), frameLoc.getBlockX(), frameLoc.getBlockY() - 1, frameLoc.getBlockZ());
            if (di.claimPedestal(p, slabLoc)) {
                e.setCancelled(true);
            }
        } else if (e.getRightClicked() instanceof org.bukkit.entity.Villager v
                && v.getScoreboardTags().contains("dung.shopkeeper")) {
            e.setCancelled(true);
            di.openShop(p);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            DungeonInstance di = instanceOf(p);
            if (di == null) return;
            Item it = e.getItem();
            Material m = it.getItemStack().getType();
            if (Pickup.isPickup(m)) {
                e.setCancelled(true);
                PlayerState st = di.playerStateOf(p);
                if (st != null && Pickup.apply(m, st)) {
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