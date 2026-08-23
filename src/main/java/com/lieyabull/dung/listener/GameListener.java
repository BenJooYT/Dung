package com.lieyabull.dung.listener;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.game.PlayerState;
import com.lieyabull.dung.game.Run;
import com.lieyabull.dung.game.WorkstationType;
import com.lieyabull.dung.pickup.Pickup;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.ui.ChatUI;
import com.lieyabull.dung.ui.StashUI;
import com.lieyabull.dung.dungeon.Floor;
import com.lieyabull.dung.dungeon.RoomGen;
import com.lieyabull.dung.plot.PlotManager;
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
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Wires Paper events to the game. Routes events to the correct dungeon instance per player. */
public final class GameListener implements Listener {
    /** Dung weapons deal only this fraction of their vanilla damage to hostile mobs outside a run. */
    private static final double OUTSIDE_DAMAGE_MULTIPLIER = 0.25;
    private final Dung plugin;
    /** Death locations for players who died in the plots world, so respawn can target their nearest
     *  owned plot. Cleared on respawn/quit. */
    private final Map<UUID, Location> plotsDeathLocations = new HashMap<>();

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
        plugin.meta().setName(p.getUniqueId(), p.getName());
        // First join as an operator/admin: tell them the lobby is theirs to decorate
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        if ((p.isOp() || p.hasPermission("dung.admin")) && !prof.lobbyEditNotified) {
            prof.lobbyEditNotified = true;
            plugin.meta().save();
            p.sendMessage("");
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands(
                    "§6§lYou're an admin! §7The §flobby world §7is editable by operators and players"
                            + " with the §bdung.admin §7permission — build it up however you like."));
        }
        // Send every joining player to the LOBBY spawn (deferred 1 tick so join completes first) —
        // they must never land back inside a run world, which is deleted when its run ends.
        org.bukkit.Location lobbySpawn = plugin.worldManager().lobbySpawn().clone();
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) p.teleport(lobbySpawn);
        });
        if (!p.hasPlayedBefore()) {
            p.teleport(p.getWorld().getSpawnLocation());
            ChatUI.startPrompt(p);
            return;
        }
        // Restore the player's last known location (e.g. plots world) instead of always
        // sending them to the main world spawn. The location is saved on quit.
        if (prof.lastWorld != null) {
            org.bukkit.World w = resolveWorld(prof.lastWorld);
            if (w != null) {
                p.teleport(new org.bukkit.Location(w, prof.lastX, prof.lastY, prof.lastZ, prof.lastYaw, prof.lastPitch));
            }
        }
        ChatUI.startPrompt(p, plugin.game().instanceOf(p) == null);
        // Migrate any persistent items in the player's inventory that lack UUIDs
        plugin.migratePersistentItemUuids();
    }

    /** Resolve a world by name for rejoin. The plots world is created lazily, so it isn't loaded
     *  into {@link org.bukkit.Bukkit#getWorld(String)} until the first visit — load it on demand so
     *  a player who logged out in the plots world actually rejoins there instead of the main world. */
    private org.bukkit.World resolveWorld(String name) {
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(name);
        if (w != null) return w;
        if (name != null && name.equals(com.lieyabull.dung.plot.PlotManager.PLOTS_WORLD_NAME)) {
            return plugin.plotManager().getPlotsWorld();
        }
        return null;
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
                // Guard: the player may have quit (or the run ended) between the death event and
                // this scheduled tick. In that case removePlayer already restored their inventory
                // and delivered pending persists — running onPlayerDeath again would apply the
                // persistent-gear durability penalty a second time.
                if (plugin.game().instanceOf(p) == di && di.isRunning()) {
                    di.onPlayerDeath(p);
                }
            });
        } else if (p.getLocation().getWorld() != null
                && p.getLocation().getWorld().getName().equals(PlotManager.PLOTS_WORLD_NAME)) {
            // Death in the plots world: remember where so onRespawn can send them to their
            // nearest owned plot instead of the world spawn.
            plotsDeathLocations.put(p.getUniqueId(), p.getLocation());
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
            return;
        }
        // Died in the plots world: respawn at their nearest owned plot instead of the world spawn.
        Location death = plotsDeathLocations.remove(p.getUniqueId());
        if (death != null) {
            Location home = plugin.plotManager().nearestOwnedPlotHome(p.getUniqueId(), death);
            if (home != null) {
                e.setRespawnLocation(home);
            }
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
        PlayerInventory inv = p.getInventory();
        if (DungeonInstance.isRunItem(e.getCurrentItem()) || DungeonInstance.isRunItem(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        // Broken armor/shield cannot be equipped. A click that places a broken piece onto an armor
        // slot is cancelled and the piece is routed to a free inventory slot (or the stash if the
        // bag is full). A shift-click auto-equip from the main inventory is cancelled, leaving the
        // piece where it already is (a free slot).
        if (e.getClickedInventory() instanceof PlayerInventory && e.getSlot() >= 36 && e.getSlot() <= 39) {
            ItemStack cursor = e.getCursor();
            if (cursor != null && isBrokenEquippable(cursor)) {
                e.setCancelled(true);
                e.setCursor(null);
                StashUI.placeOrStash(p, cursor);
                p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cThat armor is broken — repair it at §6/shop§7 before equipping."));
                return;
            }
        }
        if (e.isShiftClick() && e.getClickedInventory() instanceof PlayerInventory
                && e.getSlot() < 36 && isBrokenEquippable(e.getCurrentItem())) {
            e.setCancelled(true);
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cThat armor is broken — repair it at §6/shop§7 before equipping."));
            return;
        }
        // Shift-clicking a mana shield equips it into the slot-9 equip slot (only when no shield is
        // currently equipped there, and never while it is broken).
        if (e.isShiftClick() && e.getClickedInventory() instanceof PlayerInventory
                && GearFactory.isShield(e.getCurrentItem())
                && !GearFactory.isShield(inv.getItem(DungeonInstance.SHIELD_SLOT))
                && !GearFactory.isBroken(e.getCurrentItem())) {
            e.setCancelled(true);
            ItemStack shield = e.getCurrentItem().clone();
            inv.setItem(e.getSlot(), null);
            inv.setItem(DungeonInstance.SHIELD_SLOT, shield);
            return;
        }
        // The offhand slot is disabled. Any attempt to place an item there (or retrieve one from it)
        // is routed to the first available inventory slot instead.
        if (e.getClickedInventory() instanceof PlayerInventory && e.getSlot() == 40) {
            e.setCancelled(true);
            ItemStack cursor = e.getCursor();
            ItemStack off = e.getCurrentItem();
            if (cursor != null && !cursor.getType().isAir()) {
                e.setCursor(null);
                placeInFirstAvailableSlot(p, cursor);
            } else if (off != null && !off.getType().isAir()) {
                inv.setItem(40, null);
                placeInFirstAvailableSlot(p, off);
            }
            return;
        }
        // When an armor piece is moved (equipped), a starter armor piece swapped out of its armor
        // slot should be deleted instead of piling up in storage. Deferred to the next tick so the
        // move has taken effect before we scan for displaced starter armor.
        if (!e.isCancelled() && (isArmorItem(e.getCurrentItem()) || isArmorItem(e.getCursor()))) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> di.removeDisplacedStarterArmor(p));
        }
    }

    /** Place an item into the first free main-inventory slot (0-35). Drops it if the inventory is full. */
    private static void placeInFirstAvailableSlot(Player p, ItemStack item) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) {
                inv.setItem(i, item);
                return;
            }
        }
        p.getWorld().dropItemNaturally(p.getLocation(), item);
    }

    /** True if the item is an armor piece (helmet, chestplate, leggings, boots). */
    private static boolean isArmorItem(ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return false;
        String n = s.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    /** True if the item is a broken persistent armor/shield piece that must never be equipped. */
    private static boolean isBrokenEquippable(ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return false;
        if (!GearFactory.isPersistent(s)) return false;
        if (!GearFactory.isBroken(s)) return false;
        return isArmorItem(s) || GearFactory.isShield(s);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        DungeonInstance di = instanceOf(p);
        if (di == null) return;
        if (DungeonInstance.isRunItem(e.getOldCursor()) || DungeonInstance.isRunItem(e.getCursor())
                || e.getNewItems().values().stream().anyMatch(DungeonInstance::isRunItem)) {
            e.setCancelled(true);
            return;
        }
        // A drag that would place a broken armor/shield piece into an armor slot (raw 36-39) must
        // not equip it — cancel so the pieces stay in the main inventory.
        if (e.getNewItems().entrySet().stream().anyMatch(en ->
                en.getKey() >= 36 && en.getKey() <= 39 && isBrokenEquippable(en.getValue()))) {
            e.setCancelled(true);
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cThat armor is broken — repair it at §6/shop§7 before equipping."));
            return;
        }
        // The offhand slot is disabled. If a drag targets it (raw slot 45), cancel the drag and route
        // the whole dragged stack to the first available inventory slot instead.
        if (e.getRawSlots().contains(45)) {
            e.setCancelled(true);
            ItemStack old = e.getOldCursor();
            if (old != null && !old.getType().isAir()) {
                placeInFirstAvailableSlot(p, old);
            }
            e.setCursor(null);
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
            di.setStatus(p, "§cKeys and bombs stay in your hotbar!");
            return;
        }
        // Run gear that uses a placeable material (e.g. Storm Rod = LIGHTNING_ROD) must not be
        // placed as a block — it's a weapon, not a buildable.
        org.bukkit.inventory.meta.ItemMeta im = e.getItemInHand().getItemMeta();
        if (im != null && im.getPersistentDataContainer()
                .has(org.bukkit.NamespacedKey.minecraft(ItemTags.KIND),
                     org.bukkit.persistence.PersistentDataType.STRING)) {
            e.setCancelled(true);
        }
    }

    /** Forbid all block breaking while in a run — players shouldn't be modifying the dungeon world. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        Player p = e.getPlayer();
        DungeonInstance di = instanceOf(p);
        if (di == null) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plotsDeathLocations.remove(p.getUniqueId());
        plugin.shopUI().onQuit(p);
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

    /** Left click / attack to fire tears. Outside a dungeon, Dung weapons still hit hostile mobs
     *  but at a nerfed 25% of their vanilla damage. */
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            DungeonInstance di = instanceOf(p);
            if (di != null) {
                e.setCancelled(true);
                di.registerAttack(p);
                return;
            }
            if (isHostile(e.getEntity()) && isDungWeapon(p.getInventory().getItemInMainHand())) {
                e.setDamage(e.getDamage() * OUTSIDE_DAMAGE_MULTIPLIER);
            }
        }
    }

    /** Hostile mobs (zombies, skeletons, creepers, ...) — Dung weapons only work against these. */
    private static boolean isHostile(Entity e) {
        return e instanceof org.bukkit.entity.Monster;
    }

    /** True if the held item is a Dung weapon (and not broken). */
    private boolean isDungWeapon(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (GearFactory.isBroken(item)) return false;
        String kind = item.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, ItemTags.KIND),
                org.bukkit.persistence.PersistentDataType.STRING);
        return "weapon".equals(kind);
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

    /** Prevent Creeper (Mulliboom) native explosions from destroying blocks in the dungeon.
     *  The Mulliboom's custom death explosion is handled in Enemy.mulliboomExplode() with
     *  a visual-only explosion (0 power, no block damage). This cancels the Creeper's native
     *  explosion that would otherwise destroy blocks. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onExplosionPrime(ExplosionPrimeEvent e) {
        if (e.getEntity().getScoreboardTags().contains("dung.entity")) {
            e.setCancelled(true);
        }
    }

    /** Backup: cancel any explosion that happens in a dungeon world to prevent block damage. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (e.getEntity().getScoreboardTags().contains("dung.entity")) {
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

    /** Handle projectiles fired by Dung mobs (Gaper spit, Maw sonic boom) hitting players,
     *  and Fireball projectiles from the Blaze Staff hitting enemies/boss.
     *  Snowballs deal 0 damage in vanilla Minecraft, and the onEnemyDamage handler cancels
     *  all dung-source damage events — so we need a separate handler to apply the mob's
     *  damage through Dung's PlayerState-based system when a dung projectile hits a player. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent e) {
        org.bukkit.entity.Projectile proj = e.getEntity();
        // Handle Fireball from Blaze Staff hitting enemies/boss
        if (proj.getScoreboardTags().contains("dung.fireball")) {
            if (proj.getShooter() instanceof Player caster && isInRun(caster)) {
                DungeonInstance di = instanceOf(caster);
                if (di == null) return;
                // Get the stored damage value
                double dmg = 0;
                if (proj.hasMetadata("dung.damage")) {
                    dmg = proj.getMetadata("dung.damage").get(0).asDouble();
                }
                if (dmg <= 0) return;
                org.bukkit.Location impact = proj.getLocation();
                org.bukkit.World w = impact.getWorld();
                // AoE damage to all enemies within 3 blocks
                long k = di.run().floor.key(di.curRoom().x, di.curRoom().z);
                java.util.List<com.lieyabull.dung.entity.Enemy> roomList = di.roomEnemies().getOrDefault(k, java.util.List.of());
                for (com.lieyabull.dung.entity.Enemy e2 : roomList) {
                    if (e2.dead) continue;
                    if (e2.entity.getLocation().distance(impact) < 3.0) {
                        e2.damage(dmg, caster, 0, 0);
                    }
                }
                // Also damage boss if within range
                if (di.boss() != null && di.boss().isActive() && di.boss().location().distance(impact) < 3.0) {
                    di.boss().damage(dmg, caster);
                }
                // Visual effects
                w.spawnParticle(org.bukkit.Particle.FLAME, impact, 30, 1.5, 1.5, 1.5, 0.1);
                w.spawnParticle(org.bukkit.Particle.LAVA, impact, 15, 1, 1, 1, 0);
                w.playSound(impact, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
            }
            return;
        }
        // Handle dung mob projectiles hitting players
        if (!(e.getHitEntity() instanceof Player p)) return;
        if (!isInRun(p)) return;
        if (proj.getShooter() instanceof org.bukkit.entity.Entity shooter
                && shooter.getScoreboardTags().contains("dung.entity")) {
            // Find the Enemy instance for this shooter to get its damage value
            DungeonInstance di = instanceOf(p);
            if (di == null) return;
            // Look up the enemy by its entity UUID
            com.lieyabull.dung.entity.Enemy enemy = di.enemyByEntity(shooter.getUniqueId());
            if (enemy != null && !enemy.dead) {
                // Maw (ai=6) ranged attack does 3x its base damage
                double projectileDmg = enemy.type.ai == 6 ? enemy.damage * 3.0 : enemy.damage;
                com.lieyabull.dung.game.GameManager.playerHurt(p, projectileDmg);
            }
        }
    }

    /** Some run gear (e.g. the Storm Rod, which uses the placeable LIGHTNING_ROD material) would
     *  otherwise be placed as a block. Cancel placing any run-gear item held in the main hand. */
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
        // Broken armor/shield cannot be equipped by right-clicking. Cancel and route the piece
        // into a free inventory slot (or the stash if the bag is full).
        ItemStack handItem = p.getInventory().getItemInMainHand();
        if (isBrokenEquippable(handItem)
                && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            e.setCancelled(true);
            p.getInventory().setItemInMainHand(null);
            StashUI.placeOrStash(p, handItem);
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cThat armor is broken — repair it at §6/shop§7 before equipping."));
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        boolean hasAbility = held != null && !held.getType().isAir() && held.getItemMeta() != null
                && held.getItemMeta().getPersistentDataContainer()
                        .has(org.bukkit.NamespacedKey.minecraft(ItemTags.ABILITY),
                             org.bukkit.persistence.PersistentDataType.STRING);
        if (hasAbility && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && p.isSneaking()) {
            e.setCancelled(true);
            // Life Drain: shift + right-click casts the AoE Life Drain ability on enemies.
            // Right-click on a player still heals them (handled in onInteractEntity).
            di.tryCastAbility(p, held);
            return;
        }
        // Life Drain: shift + left-click heals the user with the weapon's stored health
        if (hasAbility && (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK)
                && p.isSneaking()) {
            String ability = held.getItemMeta().getPersistentDataContainer()
                    .get(org.bukkit.NamespacedKey.minecraft(ItemTags.ABILITY),
                         org.bukkit.persistence.PersistentDataType.STRING);
            if ("Life Drain".equals(ability)) {
                e.setCancelled(true);
                int stored = GearFactory.getStoredHealth(held);
                if (stored > 0) {
                    PlayerState st = di.run().playerStateOf(p.getUniqueId());
                    if (st != null && !st.dead) {
                        st.heal(stored);
                        GearFactory.setStoredHealth(held, 0);
                        p.sendMessage("§aYou healed yourself for §c" + stored + "❤");
                        // Spawn damage_indicator particles exploding outward from the player
                        Location pLoc = p.getLocation().add(0, 1, 0);
                        for (int i = 0; i < 12; i++) {
                            double angle = i * Math.PI * 2 / 12;
                            double dx = Math.cos(angle) * 0.5;
                            double dz = Math.sin(angle) * 0.5;
                            Location pt = pLoc.clone().add(dx, 0, dz);
                            p.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, pt, 1, 0, 0, 0, 0);
                        }
                    } else {
                        p.sendMessage("§cYou have no health to heal!");
                    }
                } else {
                    p.sendMessage("§cNo stored health to spend! Attack enemies to charge it.");
                }
                return;
            }
        }
        // pedestal: right-click a pedestal slab to claim the item. Cancel the event for ANY click on a
        // tracked pedestal (not just a successful claim) so that holding an armor piece in the hand
        // never accidentally equips it while claiming the pedestal item.
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && di.isPedestal(e.getClickedBlock().getLocation())) {
            e.setCancelled(true);
            di.claimPedestal(p, e.getClickedBlock().getLocation());
            return;
        }
        if (e.getItem() != null) {
            String en = e.getItem().getType().name();
            if (en.endsWith("_HELMET") || en.endsWith("_CHESTPLATE")
                    || en.endsWith("_LEGGINGS") || en.endsWith("_BOOTS")) {
                di.recomputeStats();
            }
        }
        // workstation: right-click a registered workstation block in an UPGRADE room
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            WorkstationType wt = di.workstationAt(e.getClickedBlock().getLocation());
            if (wt != null) {
                e.setCancelled(true);
                di.openWorkstation(p, wt);
                return;
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

    /** Right-click an item frame on a pedestal to claim the item, or right-click a player
     *  with Life Drain (Soul Siphon) to heal them with stored health. */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        DungeonInstance di = instanceOf(p);
        if (di == null) return;
        // Life Drain: right-click a player (normal click, not sneak) to heal them with stored health.
        // Sneak+right-click instead casts the AoE Life Drain ability, so never heal while sneaking.
        if (e.getRightClicked() instanceof Player target && p != target && !p.isSneaking()) {
            ItemStack held = p.getInventory().getItemInMainHand();
            if (held != null && !held.getType().isAir() && held.getItemMeta() != null) {
                var pdc = held.getItemMeta().getPersistentDataContainer();
                String ability = pdc.get(org.bukkit.NamespacedKey.minecraft(ItemTags.ABILITY),
                        org.bukkit.persistence.PersistentDataType.STRING);
                if ("Life Drain".equals(ability)) {
                    int stored = GearFactory.getStoredHealth(held);
                    if (stored > 0) {
                        // Both must be in the same run/party
                        DungeonInstance targetDi = instanceOf(target);
                        if (targetDi == di) {
                            PlayerState targetSt = di.run().playerStateOf(target.getUniqueId());
                            if (targetSt != null && !targetSt.dead) {
                                targetSt.heal(stored);
                                GearFactory.setStoredHealth(held, 0);
                                p.sendMessage("§aHealed " + target.getName() + " for §c" + stored + "❤");
                                target.sendMessage("§a" + p.getName() + " healed you for §c" + stored + "❤");
                                // Play the heal sound for both the healer and the healed player
                                org.bukkit.Sound healSound = org.bukkit.Sound.ENTITY_WITCH_DRINK;
                                p.getWorld().playSound(p.getLocation(), healSound, 0.8f, 1.0f);
                                p.getWorld().playSound(target.getLocation(), healSound, 0.8f, 1.0f);
                                // Spawn damage_indicator particles from sender to receiver
                                Location src = p.getLocation().add(0, 1, 0);
                                Location dst = target.getLocation().add(0, 1, 0);
                                org.bukkit.util.Vector step = dst.toVector().subtract(src.toVector()).multiply(0.1);
                                for (int t = 0; t < 10; t++) {
                                    Location pt = src.clone().add(step.clone().multiply(t));
                                    p.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, pt, 1, 0, 0, 0, 0);
                                }
                                // Burst of particles around the healed player
                                p.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR,
                                        dst, 20, 0.4, 0.5, 0.4, 0);
                                e.setCancelled(true);
                                return;
                            }
                        }
                    } else {
                        p.sendMessage("§cNo stored health to transfer! Attack enemies to charge it.");
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }
        if (e.getRightClicked() instanceof ItemFrame frame) {
            // Dead players are spectators during a run — they can look, but never claim loot.
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                e.setCancelled(true);
                return;
            }
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
                    if (Pickup.typeOf(m) == com.lieyabull.dung.pickup.Pickup.Type.HEART) {
                        // Heart pickup feedback: same chime as the Soul Siphon heal + red burst.
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITCH_DRINK, 0.8f, 1.0f);
                        p.getWorld().spawnParticle(org.bukkit.Particle.DUST,
                                p.getLocation().add(0, 1.0, 0), 20, 0.4, 0.5, 0.4,
                                new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.3f));
                    }
                } else if (canWarnFull(p)) {
                    // Pickup no-op (e.g. hearts already full): say why instead of silently ignoring.
                    // Throttled — the pickup event re-fires every tick while standing on the item.
                    ChatUI.notify(p, "§7" + pickupName(m) + " is full — left on the ground.");
                }
            }
        }
    }

    /** Throttle for the "X is full" pickup warning (one message per player per 3 seconds). */
    private final java.util.Map<java.util.UUID, Long> lastFullPickupWarn = new java.util.HashMap<>();
    private static final long FULL_PICKUP_WARN_COOLDOWN_MS = 3000;

    private boolean canWarnFull(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastFullPickupWarn.get(p.getUniqueId());
        if (last != null && now - last < FULL_PICKUP_WARN_COOLDOWN_MS) return false;
        lastFullPickupWarn.put(p.getUniqueId(), now);
        return true;
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

    /** Friendly name of a pickup material for the "already full" feedback. */
    private String pickupName(Material m) {
        return switch (Pickup.typeOf(m)) {
            case HEART -> "§cHearts";
            case COIN -> "§eCoins";
            case KEY -> "§9Keys";
            case BOMB -> "§4Bombs";
        };
    }
}