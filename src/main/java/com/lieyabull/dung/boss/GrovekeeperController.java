package com.lieyabull.dung.boss;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.plot.potion.PotionFactory;
import com.lieyabull.dung.ui.StashUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * The Grovekeeper — a forest-themed boss that replaces The Warden with a 20% chance per floor.
 * Uses a Ravager entity with 3 attacks: Root Burst (a moving line of logs that explodes on hit),
 * Timber Walls (slam), Poisonous Roots (radial). Drops a Forest Transmutation Elixir on defeat.
 */
public final class GrovekeeperController {
    private final World world;
    private final Entity boss;
    private final double maxHp;
    private double hp;
    private final int floor;
    private final Dung plugin;
    private final KeyedBossBar bar;
    private final org.bukkit.NamespacedKey barKey;
    private final java.util.List<Player> viewers = new java.util.ArrayList<>();
    private final Runnable onDefeated;
    private boolean defeated = false;
    private int patternTimer = 0;
    // attack state machine
    private static final int ATTACK_ROOT_BURST = 1, ATTACK_TIMBER_WALLS = 2, ATTACK_POISON_ROOTS = 3;
    private int attackIndex = 0;
    private int attackCd = 0;
    private int meleeRpCd = 0;      // ticks until the next melee retribution may fire
    private int warning = 0;
    private int pending = 0;
    private double warnAngle = 0;
    // Root Burst: a moving line of logs (roots) that travels toward the player, explodes on hit,
    // or stops at a wall and lingers for 10s before fading.
    private final Player primary;
    private boolean rootActive = false;
    private double rootDamage;             // damage dealt on a direct hit
    private org.bukkit.scheduler.BukkitTask rootTask;
    /** Every currently-traveling root burst — one lane per party member, all fired at once. */
    private final java.util.List<RootBurst> rootBursts = new java.util.ArrayList<>();
    /** Advanced root-burst projectiles so their controllers can be ticked together/damaged. */
    private static final int ROOT_PERSIST_TICKS = 200; // 10 seconds after stopping at a wall
    private static final int ROOT_ADVANCE_TICKS = 2;   // move the root every 2 ticks
    private static final double ROOT_TRAVEL = 2.4;     // blocks the root tip advances per move along its true heading (50% faster than 1.6)
    private static final int ROOT_LOG_HEIGHT = 1;      // how tall each root log column stands
    private static final double ROOT_DMG_RADIUS = 1.0; // horizontal proximity for a direct hit
    private final java.util.List<RootBlock> rootLogs = new java.util.ArrayList<>();
    // Timber Walls: a 5-wide x 4-tall oak-fence wall that blocks passage and hurts on contact.
    private static final int WALL_WIDTH = 5;
    private static final int WALL_HEIGHT = 4;
    private static final int WALL_DISTANCE = 4;      // blocks ahead of the boss
    private static final int WALL_PERSIST_TICKS = 120; // 6 seconds
    private final java.util.List<WallBlock> wallBlocks = new java.util.ArrayList<>();
    private org.bukkit.scheduler.BukkitTask wallTask;
    private boolean wallActive = false;
    /** Column cells (x, z) of the currently placed wall, so contact detection works at any angle. */
    private final java.util.List<int[]> wallColumns = new java.util.ArrayList<>();
    private int wallYBase = 0;
    private double wallCenterX = 0, wallCenterZ = 0; // center of the wall line (for away-from-wall push)
    /** Active timber-wall fence blocks across all Grovekeepers, keyed by block coords. Lets other
     *  systems (ability dispatch) ask "is this fence in the way?" without reaching into a live boss. */
    private static final java.util.Set<Long> ACTIVE_TIMBER = new java.util.HashSet<>();

    private static long blockKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }

    /** True when any active Grovekeeper timber wall sits on the straight line from {@code from} to
     *  {@code to} (only the fence blocks count; dungeon walls never block here). */
    public static boolean timberWallBlocks(org.bukkit.World w, Location from, Location to) {
        if (ACTIVE_TIMBER.isEmpty()) return false;
        if (w == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return false;
        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        double dist = from.distance(to);
        if (dist < 0.01) return false;
        int steps = Math.max(8, (int) Math.ceil(dist * 2));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            long k = blockKey(
                    (int) Math.floor(from.getX() + dx * t),
                    (int) Math.floor(from.getY() + dy * t),
                    (int) Math.floor(from.getZ() + dz * t));
            if (ACTIVE_TIMBER.contains(k)) return true;
        }
        return false;
    }
    // Poisonous Roots: red nether logs sitting flush at ground level that poison on step.
    private static final int POISON_PATCHES = 12;
    private static final int POISON_DURATION_TICKS = 200; // 10 seconds on the ground
    private static final int POISON_EFFECT_TICKS = 60;    // 3-second poison debuff on step
    private final java.util.List<PoisonBlock> poisonBlocks = new java.util.ArrayList<>();
    private org.bukkit.scheduler.BukkitTask poisonTask;
    private boolean poisonActive = false;
    private final java.util.Set<Integer> poisonedSessions = new java.util.HashSet<>();
    /** Remaining poison ticks for each poisoned player (started at {@link #POISON_EFFECT_TICKS}). */
    private final java.util.Map<java.util.UUID, Integer> poisonDurations = new java.util.HashMap<>();
    /** Per-player DoT phase accumulator: the step scan runs every 2 ticks, but the poison damage
     *  still lands every 10 ticks (0.5s) — this tracks how many 2-tick steps have accumulated. */
    private final java.util.Map<java.util.UUID, Integer> poisonAccum = new java.util.HashMap<>();
    /** Ambient particle pulse: gates the per-root shimmer to once per second instead of every
     *  2-tick sweep, so the field of roots reads as a slow haze rather than a particle blizzard. */
    private int poisonAmbientTick = 0;

    private static final class RootBlock {
        final org.bukkit.Location loc;
        final org.bukkit.Material original;
        RootBlock(org.bukkit.Location loc, org.bukkit.Material original) { this.loc = loc; this.original = original; }
    }

    /** A single traveling root-burst lane: heading + current tip, its own ground level, and the log
     *  cells it has erupted so they can be restored when the lane explodes, hits a wall, or fades. */
    private static final class RootBurst {
        final double dirX, dirZ;   // unit travel direction
        double x, z;               // current tip cell (block-space)
        final int y;               // block Y the logs sit at for this lane
        boolean done;              // stopped at a wall, waiting out the linger timer
        int timer = 0;             // ticks elapsed while stopped at a wall
        final java.util.List<RootBlock> cells = new java.util.ArrayList<>();
        RootBurst(double dirX, double dirZ, double x, double z, int y) {
            this.dirX = dirX; this.dirZ = dirZ;
            this.x = x; this.z = z;
            this.y = y;
        }
    }

    private static final class WallBlock {
        final org.bukkit.Location loc;
        final org.bukkit.Material original;
        WallBlock(org.bukkit.Location loc, org.bukkit.Material original) { this.loc = loc; this.original = original; }
    }

    private static final class PoisonBlock {
        final int x, y, z;
        final org.bukkit.Material original;
        PoisonBlock(int x, int y, int z, org.bukkit.Material original) { this.x = x; this.y = y; this.z = z; this.original = original; }
    }

    /** Enrage past 50% HP: faster patterns, poison roots unlocked. */
    private boolean enraged() {
        return hp / maxHp <= 0.50;
    }

    public GrovekeeperController(World w, Location center, int floor, Player target, Dung plugin) {
        this(w, center, floor, target, plugin, 1, () -> {});
    }

    public GrovekeeperController(World w, Location center, int floor, Player target, Dung plugin, int partySize) {
        this(w, center, floor, target, plugin, partySize, () -> {});
    }

    public GrovekeeperController(World w, Location center, int floor, Player target, Dung plugin, int partySize, Runnable onDefeated) {
        this.world = w;
        this.floor = floor;
        this.plugin = plugin;
        this.onDefeated = onDefeated;
        this.primary = target;
        this.maxHp = (60 + floor * 25) * Math.max(1, partySize);
        this.hp = maxHp;
        this.boss = w.spawnEntity(center, EntityType.RAVAGER);
        boss.setPersistent(true);
        // Slow the hulking Grovekeeper to ~20% of its normal speed by giving it Slowness (level 5 =
        // 75% reduction -> ~25% remaining, i.e. approx a fifth), and let its native Ravager AI keep
        // chasing so it stays a lumbering, persistent threat instead of a statue.
        if (boss instanceof org.bukkit.entity.LivingEntity le) {
            le.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS,
                    20 * 60 * 60, 4, true, false, false));
            var spd = le.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(0.12);
        }
        boss.addScoreboardTag("dung.entity");
        try { boss.setCustomName("§2Grovekeeper of Floor " + (floor + 1)); boss.setCustomNameVisible(true); } catch (Throwable ignored) {}
        this.barKey = new org.bukkit.NamespacedKey(Dung.instance(), "dung_boss_" + UUID.randomUUID().toString().replace("-", ""));
        this.bar = Bukkit.createBossBar(barKey, "§2The Grovekeeper", BarColor.GREEN, BarStyle.SEGMENTED_10);
        this.bar.setProgress(1.0);
        this.bar.addPlayer(target);
        this.bar.setVisible(true);
    }

    public void addViewer(Player p) {
        if (!viewers.contains(p)) {
            viewers.add(p);
            bar.addPlayer(p);
        }
    }

    public void removeViewer(Player p) {
        if (viewers.remove(p)) {
            bar.removePlayer(p);
        }
    }

    public void tick(Player p) {
        if (hp <= 0) return;
        if (!boss.isValid()) return;
        if (meleeRpCd > 0) meleeRpCd--;
        patternTimer++;
        boolean rage = enraged();
        Location center = boss.getLocation().clone();
        center.setY(p.getY());

        // --- warning phase ---
        if (warning > 0) {
            warning--;
            // Re-aim at the player's CURRENT position every tick so the telegraph (and the message)
            // follow them live, matching the direction the root/wall actually fires.
            if (pending == ATTACK_TIMBER_WALLS) {
                warnAngle = Math.atan2(p.getZ() - center.getZ(), p.getX() - center.getX());
            }
            if (pending == ATTACK_ROOT_BURST) warnRootBursts(center);
            else if (pending == ATTACK_TIMBER_WALLS) warnWall(center, warnAngle);
            else warnRing(center);
            if (warning == 0) fire(p, center, rage);
            return;
        }

        // contact sting
        if (p.getLocation().distance(center) < 1.6) {
            com.lieyabull.dung.game.GameManager.playerHurt(p, 25 + floor * 10);
        }

        if (attackCd > 0) { attackCd--; return; }
        // All three attacks are in the base rotation (Poisonous Roots was enrage-only, which made
        // it so rare it effectively never showed up — now it reliably appears alongside the other two).
        int pool = 3;
        int atk = (attackIndex % pool) + ATTACK_ROOT_BURST;
        attackIndex++;
        if (atk == ATTACK_ROOT_BURST || atk == ATTACK_TIMBER_WALLS) {
            warnAngle = Math.atan2(p.getZ() - center.getZ(), p.getX() - center.getX());
        }
        pending = atk;
        warning = rage ? 14 : 18;
    }

    private void fire(Player p, Location center, boolean rage) {
        // Re-aim at the player's CURRENT position. warnAngle was locked when the attack was chosen
        // (14-18 ticks earlier) and is stale by now, so the root/wall used to launch in a direction
        // that was very, very off after the boss and player kept moving during the telegraph.
        if (pending == ATTACK_TIMBER_WALLS && p != null && p.isOnline()) {
            warnAngle = Math.atan2(p.getZ() - center.getZ(), p.getX() - center.getX());
        }
        switch (pending) {
            case ATTACK_ROOT_BURST: {
                fireRootBurst(rage, center);
                break;
            }
            case ATTACK_TIMBER_WALLS: {
                fireTimberWalls(center, warnAngle, rage);
                break;
            }
            case ATTACK_POISON_ROOTS: {
                firePoisonRoots(p, center, rage);
                break;
            }
        }
        attackCd = rage ? 25 : 40;
    }

    /**
     * Root Burst: spawn a moving line of oak logs (roots) aimed at EVERY party member in the arena
     * at once — one lane per online player. Each lane travels forward, exploding if it reaches its
     * player; if it misses it stops at the nearest wall and lingers for {@link #ROOT_PERSIST_TICKS}
     * before fading away.
     */
    private void fireRootBurst(boolean rage, Location center) {
        clearRootLogs(); // cancel any lingering roots from a previous burst
        rootDamage = (rage ? 55 : 45) + floor * 15;
        rootActive = true;
        boolean any = false;
        for (Player v : candidates()) {
            if (v == null || !v.isOnline() || defeated) continue;
            double ang = Math.atan2(v.getZ() - center.getZ(), v.getX() - center.getX());
            spawnRootBurst(rage, center, ang, v.getLocation().getBlockY());
            any = true;
        }
        if (!any) { rootActive = false; return; }
        rootTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (defeated) { clearRootLogs(); return; }
            if (!rootActive) { if (rootTask != null) rootTask.cancel(); return; }
            tickRootMoves(rage);
        }, ROOT_ADVANCE_TICKS, ROOT_ADVANCE_TICKS);
    }

    /** Create one root-burst lane toward a player and place its first log. */
    private void spawnRootBurst(boolean rage, Location center, double angle, int y) {
        double dx = Math.cos(angle), dz = Math.sin(angle);
        double len = Math.hypot(dx, dz);
        if (len < 0.001) { dx = 1; dz = 0; } else { dx /= len; dz /= len; }
        RootBurst b = new RootBurst(dx, dz, center.getX(), center.getZ(), y);
        rootBursts.add(b);
        placeLogCell(b, (int) Math.floor(b.x), (int) Math.floor(b.z));
        if (checkRootHits(rage, b)) rootBursts.remove(b); // fired into a standing player: popped now
    }

    /** Advance every active root lane one step along its true heading (not snapped to 8 directions),
     *  placing a log column every other cell it crosses so the bursts stay sparse (scarcer) while
     *  still advancing, or waiting out the linger timer if a lane previously stopped at a wall. */
    private void tickRootMoves(boolean rage) {
        for (java.util.Iterator<RootBurst> it = rootBursts.iterator(); it.hasNext(); ) {
            RootBurst b = it.next();
            if (b.done) {
                b.timer++;
                if (b.timer >= ROOT_PERSIST_TICKS) { restoreRoots(b.cells); it.remove(); }
                continue;
            }
            tickRootMove(b);
            if (b.done) continue; // stopped at a wall this step; linger
            if (checkRootHits(rage, b)) it.remove(); // exploded on a player
        }
        if (rootBursts.isEmpty()) {
            rootActive = false;
            if (rootTask != null) { rootTask.cancel(); rootTask = null; }
        }
    }

    /** Advance one root lane's tip, placing logs across the cells it enters and stopping at a wall. */
    private void tickRootMove(RootBurst b) {
        double oldX = b.x, oldZ = b.z;
        double newX = oldX + b.dirX * ROOT_TRAVEL;
        double newZ = oldZ + b.dirZ * ROOT_TRAVEL;
        // Walk the segment, placing a log in each cell it enters and stopping at the first wall.
        double dist = Math.hypot(newX - oldX, newZ - oldZ);
        int steps = Math.max(1, (int) Math.ceil(dist / 0.25));
        int prevX = (int) Math.floor(oldX), prevZ = (int) Math.floor(oldZ);
        double stopX = oldX, stopZ = oldZ;
        boolean hitWall = false;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int cx = (int) Math.floor(oldX + (newX - oldX) * t);
            int cz = (int) Math.floor(oldZ + (newZ - oldZ) * t);
            if (cx == prevX && cz == prevZ) continue; // still inside the previous cell
            prevX = cx;
            prevZ = cz;
            Material ahead = world.getBlockAt(cx, b.y, cz).getType();
            // Ignore our own logs so the root can carve along its own lane, but stop at any wall.
            if (ahead != Material.AIR && ahead != Material.CAVE_AIR && ahead != Material.OAK_LOG) {
                stopX = oldX + (newX - oldX) * Math.max(0.0, t - 0.01);
                stopZ = oldZ + (newZ - oldZ) * Math.max(0.0, t - 0.01);
                hitWall = true;
                break;
            }
            // Place a log in every crossed cell so the root is a single continuous line.
            placeLogCell(b, cx, cz);
            stopX = oldX + (newX - oldX) * t;
            stopZ = oldZ + (newZ - oldZ) * t;
        }
        b.x = stopX;
        b.z = stopZ;
        if (hitWall) stopRootAtWall(b);
    }

    /** Place the root lane's tip cell onto the world (with eruption particles/sound) and record
     *  it so it can be restored later. Skips cells already logged. */
    private void placeLogCell(RootBurst b, int x, int z) {
        // Never erupt a root directly beneath the boss — the Grovekeeper can't stand on its own
        // root line. Looking at the boss's own column keeps the burst line from materializing
        // under it.
        if (x == boss.getLocation().getBlockX() && z == boss.getLocation().getBlockZ()) return;
        for (RootBlock rb : b.cells) {
            if (rb.loc.getBlockX() == x && rb.loc.getBlockZ() == z && rb.loc.getBlockY() == b.y) return;
        }
        if (world.getBlockAt(x, b.y, z).getType() == Material.OAK_LOG) return;
        for (int h = 0; h < ROOT_LOG_HEIGHT; h++) {
            int y = b.y + h;
            if (world.getBlockAt(x, y, z).getType() == Material.OAK_LOG) continue;
            Material orig = world.getBlockAt(x, y, z).getType();
            RootBlock rb = new RootBlock(new Location(world, x + 0.5, y, z + 0.5), orig);
            b.cells.add(rb);
            rootLogs.add(rb);
            world.getBlockAt(x, y, z).setType(Material.OAK_LOG);
        }
        // Erupt out of the ground: a burst of soil/log dirt rises, so the root reads as tearing out of
        // the floor.
        Location cell = new Location(world, x + 0.5, b.y, z + 0.5);
        world.spawnParticle(org.bukkit.Particle.BLOCK, cell, 18, 0.5, 0.9, 0.5, Material.DIRT.createBlockData());
        world.spawnParticle(org.bukkit.Particle.BLOCK, cell.clone().add(0, 1, 0), 12, 0.4, 0.7, 0.4, Material.OAK_LOG.createBlockData());
        world.spawnParticle(org.bukkit.Particle.DUST, cell.clone().add(0, 1.5, 0), 20, 0.5, 0.8, 0.5, 0,
                new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(86, 58, 32), 1.2f));
        world.playSound(cell, org.bukkit.Sound.BLOCK_ROOTS_BREAK, 1.0f, 0.5f);
        world.playSound(cell, org.bukkit.Sound.BLOCK_GRASS_BREAK, 0.9f, 0.7f);
    }

    /** Damage any party member the given lane's leading band is touching. Returns true if it hit. */
    private boolean checkRootHits(boolean rage, RootBurst b) {
        for (Player v : candidates()) {
            if (v == null || !v.isOnline() || defeated) continue;
            double vx = v.getLocation().getX();
            double vz = v.getLocation().getZ();
            double vy = v.getLocation().getY();
            // The root erupts from (y - 1) up through the player's head, so it can catch a
            // player standing on a raised cell or mid-jump rather than only at ground level.
            if (vy < b.y - 1.5 || vy > b.y + 4.0) continue;
            if (Math.abs(vx - b.x) < ROOT_DMG_RADIUS && Math.abs(vz - b.z) < ROOT_DMG_RADIUS) {
                explodeRoot(v, rage, b);
                return true;
            }
        }
        return false;
    }

    private void explodeRoot(Player v, boolean rage, RootBurst b) {
        Location boom = new Location(world, b.x, b.y + 1.0, b.z);
        world.spawnParticle(org.bukkit.Particle.EXPLOSION, boom, 1, 0, 0, 0);
        // The whole erupting column pops, from the floor up past head height.
        for (int h = 0; h <= 3; h++) {
            world.spawnParticle(org.bukkit.Particle.BLOCK, boom.clone().add(0, h, 0), 30, 2, 1, 2, Material.OAK_LOG.createBlockData());
        }
        world.spawnParticle(org.bukkit.Particle.ITEM, boom.clone().add(0, 1, 0), 20, 2, 1, 2, new org.bukkit.inventory.ItemStack(Material.VINE));
        world.playSound(boom, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        com.lieyabull.dung.game.GameManager.playerHurt(v, rootDamage);
        restoreRoots(b.cells);
        b.cells.clear();
    }

    /** The lane reached a wall before its player: keep the roots, fade them after 10s. */
    private void stopRootAtWall(RootBurst b) {
        b.done = true;
        b.timer = 0;
        Location stop = new Location(world, b.x, b.y + 0.5, b.z);
        world.playSound(stop, org.bukkit.Sound.BLOCK_WOOD_STEP, 1.0f, 0.6f);
        world.spawnParticle(org.bukkit.Particle.BLOCK, stop.clone().add(0, 1, 0), 12, 0.5, 0.5, 0.5, Material.OAK_LOG.createBlockData());
    }

    /** All living party members that could be hit by the root: the primary target plus viewers. */
    private java.util.List<Player> candidates() {
        java.util.List<Player> out = new java.util.ArrayList<>(viewers);
        if (primary != null && !out.contains(primary)) out.add(primary);
        return out;
    }

    /** Restore every placed log to its original block and stop the movement task. */
    private void clearRootLogs() {
        restoreRoots(rootLogs);
        rootLogs.clear();
        rootBursts.clear();
        rootActive = false;
        if (rootTask != null) {
            rootTask.cancel();
            rootTask = null;
        }
    }

    /** Restore the given root cells (logs back to their originals) and drop them from the master
     *  ledger so a lane that popped or faded doesn't get double-restored later. */
    private void restoreRoots(java.util.List<RootBlock> cells) {
        for (RootBlock rb : cells) {
            org.bukkit.block.Block b = rb.loc.getBlock();
            if (b.getType() == Material.OAK_LOG) b.setType(rb.original);
        }
        rootLogs.removeAll(cells);
    }

    /** Restore every placed fence block and stop the wall's tick task. */
    private void clearTimberWalls() {
        for (WallBlock wb : wallBlocks) {
            ACTIVE_TIMBER.remove(blockKey(wb.loc.getBlockX(), wb.loc.getBlockY(), wb.loc.getBlockZ()));
            org.bukkit.block.Block b = wb.loc.getBlock();
            if (b.getType() == Material.OAK_FENCE) b.setType(wb.original);
        }
        wallBlocks.clear();
        wallColumns.clear();
        wallActive = false;
        wallYBase = 0;
        if (wallTask != null) {
            wallTask.cancel();
            wallTask = null;
        }
    }

    /** Restore every poisonous root block and stop the poison task. */
    private void clearPoisonRoots() {
        for (PoisonBlock pb : poisonBlocks) {
            org.bukkit.block.Block b = world.getBlockAt(pb.x, pb.y, pb.z);
            if (b.getType() == Material.CRIMSON_STEM) b.setType(pb.original);
        }
        poisonBlocks.clear();
        poisonActive = false;
        poisonedSessions.clear();
        poisonDurations.clear();
        poisonAccum.clear();
        if (poisonTask != null) {
            poisonTask.cancel();
            poisonTask = null;
        }
    }

    /** Compute the footprint cells of a timber wall: a connected WALL_WIDTH-wide column line of
     *  fence placed WALL_DISTANCE ahead of the boss along {@code angle}. Rasterizing the
     *  perpendicular segment keeps the wall a clean, connected barrier at ANY angle (the old
     *  per-offset `floor` collapsed/overlapped cells on diagonals). */
    private java.util.List<int[]> wallCells(Location center, double angle) {
        double dx = Math.cos(angle), dz = Math.sin(angle);
        double len = Math.hypot(dx, dz);
        if (len < 0.001) { dx = 1; dz = 0; } else { dx /= len; dz /= len; }
        double px = -dz, pz = dx;               // perpendicular unit direction
        double cx = center.getX() + dx * WALL_DISTANCE;
        double cz = center.getZ() + dz * WALL_DISTANCE;
        double hw = WALL_WIDTH / 2.0;           // half the wall's horizontal span
        double sx = cx + px * (-hw), sz = cz + pz * (-hw);
        double ex = cx + px * hw,  ez = cz + pz * hw;
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        double seg = Math.hypot(ex - sx, ez - sz);
        int steps = Math.max(1, (int) Math.ceil(seg / 0.25));
        int prevX = Integer.MIN_VALUE, prevZ = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(sx + (ex - sx) * t);
            int z = (int) Math.floor(sz + (ez - sz) * t);
            if (x == prevX && z == prevZ) continue;
            prevX = x; prevZ = z;
            cells.add(new int[]{x, z});
        }
        return cells;
    }

    /** Timber Walls: raise a 5-wide x 4-tall wall of oak fence toward the player. */
    private void fireTimberWalls(Location center, double angle, boolean rage) {
        clearTimberWalls();
        clearPoisonRoots();
        double dx = Math.cos(angle), dz = Math.sin(angle);
        double len = Math.hypot(dx, dz);
        if (len < 0.001) { dx = 1; dz = 0; }
        else { dx /= len; dz /= len; }
        wallCenterX = center.getX() + dx * WALL_DISTANCE;
        wallCenterZ = center.getZ() + dz * WALL_DISTANCE;
        int baseY = center.getBlockY();
        wallYBase = baseY;
        for (int[] c : wallCells(center, angle)) {
            wallColumns.add(c);
            for (int h = 0; h < WALL_HEIGHT; h++) {
                org.bukkit.block.Block b = world.getBlockAt(c[0], baseY + h, c[1]);
                Material orig = b.getType();
                if (orig == Material.OAK_FENCE) orig = Material.AIR;
                wallBlocks.add(new WallBlock(new Location(world, c[0], baseY + h, c[1]), orig));
                b.setType(Material.OAK_FENCE);
                ACTIVE_TIMBER.add(blockKey(c[0], baseY + h, c[1]));
            }
        }
        Location soundLoc = wallColumns.isEmpty()
                ? center.clone().add(dx * WALL_DISTANCE, 0, dz * WALL_DISTANCE)
                : new Location(world, wallColumns.get(0)[0] + 0.5, baseY, wallColumns.get(0)[1] + 0.5);
        world.playSound(soundLoc, org.bukkit.Sound.BLOCK_WOOD_PLACE, 1.0f, 0.8f);
        wallActive = true;
        // Persist window is tracked inside the repeating task (the old separate runTaskLater one-shot
        // was never canceled when the wall was cleared early, so a stale one-shot could wipe out a
        // freshly-spawned wall — the "it disappears right after it spawns" bug).
        final int[] elapsed = {0};
        wallTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (defeated) { clearTimberWalls(); return; }
            if (!wallActive) { if (wallTask != null) wallTask.cancel(); return; }
            elapsed[0] += 2; // firing every 2 ticks
            if (elapsed[0] >= WALL_PERSIST_TICKS) { clearTimberWalls(); return; }
            wallTick(rage);
        }, 1L, 2L);
    }

    /** Each tick, push back + damage players who press INTO the wall, but leave anyone who is merely
     *  walking AWAY from it (e.g. the wall spawned right behind them) unharmed. */
    private void wallTick(boolean rage) {
        if (wallColumns.isEmpty()) return;
        int by0 = wallYBase;
        org.bukkit.util.Vector wallCenter = new org.bukkit.util.Vector(wallCenterX, 0, wallCenterZ);
        for (Player v : candidates()) {
            if (v == null || !v.isOnline() || defeated) continue;
            int vx = v.getLocation().getBlockX();
            int vz = v.getLocation().getBlockZ();
            int vy = v.getLocation().getBlockY();
            if (vy < by0 || vy > by0 + WALL_HEIGHT - 1) continue;
            for (int[] c : wallColumns) {
                if (c[0] != vx || c[1] != vz) continue;
                // Direction from the player toward the wall's center line.
                org.bukkit.util.Vector toWall = wallCenter.clone().subtract(
                        new org.bukkit.util.Vector(v.getLocation().getX(), 0, v.getLocation().getZ())).setY(0);
                double dist = toWall.length();
                if (dist < 0.001) break;
                toWall.normalize();
                // Only hurt players who are pressing INTO the wall. A small standing tolerance still
                // catches someone planted against it, but a player walking AWAY (spawned on/behind the
                // wall) has a negative approach and takes no damage.
                org.bukkit.util.Vector vel = v.getVelocity().clone().setY(0);
                double approach = vel.dot(toWall) + 0.06;
                if (approach <= 0) break;
                com.lieyabull.dung.game.GameManager.playerHurt(v, (rage ? 28 : 20) + floor * 8);
                // Push the player away from the wall's center line (toward whichever side they came
                // from), regardless of the wall's angle.
                v.setVelocity(toWall.clone().multiply(-0.8).setY(0.3));
                world.spawnParticle(org.bukkit.Particle.BLOCK, v.getLocation().clone().add(0, 1, 0), 8, 0.4, 0.4, 0.4, Material.OAK_FENCE.createBlockData());
                break;
            }
        }
    }

    /** Poisonous Roots: send red nether logs (crimson stems) snaking out at ground level. */
    private void firePoisonRoots(Player p, Location center, boolean rage) {
        clearTimberWalls();
        clearPoisonRoots();
        // Reference height at (or above) any floor the roots might cross — the higher of the player's
        // and the boss's height, so per-cell ground scanning works even when the boss is elevated
        // (we scan DOWN from here at each root cell to find that cell's own real floor).
        int refY = (int) Math.ceil(Math.max(center.getY(), boss.getLocation().getY())) + 6;
        java.util.Random rng = new java.util.Random();
        for (int patch = 0; patch < POISON_PATCHES; patch++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double dx = Math.cos(ang), dz = Math.sin(ang);
            int cx = (int) Math.floor(center.getX() + dx * (2 + rng.nextInt(4)));
            int cz = (int) Math.floor(center.getZ() + dz * (2 + rng.nextInt(4)));
            int steps = 4 + rng.nextInt(5);
            for (int s = 0; s <= steps; s++) {
                // Slight random meander so the roots snake and cross each other, filling the floor.
                double wander = (rng.nextDouble() - 0.5) * 3.2;
                int bx = (int) Math.floor(cx + dx * s + (dz * wander));
                int bz = (int) Math.floor(cz + dz * s + (-dx * wander));
                // Each root sits flush on the floor at ITS OWN column, so elevated areas stay level.
                int gy = groundYAt(bx, bz, refY);
                if (world.getBlockAt(bx, gy, bz).getType() == Material.CRIMSON_STEM) continue;
                Material orig = world.getBlockAt(bx, gy, bz).getType();
                poisonBlocks.add(new PoisonBlock(bx, gy, bz, orig));
                world.getBlockAt(bx, gy, bz).setType(Material.CRIMSON_STEM);
            }
        }
        world.spawnParticle(org.bukkit.Particle.BLOCK, center.clone().add(0, 1, 0), 32, 3, 0, 3, Material.CRIMSON_STEM.createBlockData());
        world.spawnParticle(org.bukkit.Particle.ITEM, center.clone().add(0, 1, 0), 24, 3, 0, 3, new org.bukkit.inventory.ItemStack(Material.SPORE_BLOSSOM));
        world.playSound(center, org.bukkit.Sound.BLOCK_GRASS_BREAK, 1.0f, 0.5f);
        poisonActive = true;
        final int[] poisonElapsed = {0};
        poisonTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (defeated) { clearPoisonRoots(); return; }
            if (!poisonActive) { if (poisonTask != null) poisonTask.cancel(); return; }
            poisonElapsed[0] += 2; // firing every 2 ticks
            if (poisonElapsed[0] >= POISON_DURATION_TICKS) { clearPoisonRoots(); return; }
            poisonTick(rage);
        }, 2L, 2L);
    }

    /** Scan down from an above-floor reference Y at column ({@code x},{@code z}) and return the solid
     *  block whose cell above is open air — that column's real ground. Requiring air above means the
     *  scan passes the solid ceiling/canopy (whose own cell above is also solid) and lands on the
     *  floor of the interior, so roots never spawn up inside the roof. This also keeps roots flush
     *  with the floor even where the arena isn't flat (e.g. an elevated boss ledge). */
    private int groundYAt(int x, int z, int fromY) {
        for (int i = 0; i < 48; i++) {
            int ty = fromY - i;
            Material m = world.getBlockAt(x, ty, z).getType();
            if (m.isSolid() || m == Material.WATER || m == Material.LAVA) {
                Material above = world.getBlockAt(x, ty + 1, z).getType();
                if (above == Material.AIR || above == Material.CAVE_AIR) return ty;
            }
        }
        return fromY;
    }

    /** Detect players stepping onto a root (applying the 3-second poison debuff) and tick down the
     *  poison's damage-over-time. Roots sit flush at ground level, so a player stands at pb.y + 1.
     *  The step scan sweeps every 2 ticks so a sprinting player can't cross a root between samples;
     *  the DoT still lands every 10 ticks via the per-player {@link #poisonAccum} accumulator. */
private void poisonTick(boolean rage) {
        if (poisonBlocks.isEmpty()) return;
        for (Player v : candidates()) {
            if (v == null || !v.isOnline() || defeated) continue;
            double vy = v.getLocation().getY();
            double vx = v.getLocation().getX();
            double vz = v.getLocation().getZ();
            // The player's body must overlap the root cell horizontally (block half-width + player
            // half-width) and their feet must be level with the root up through a jump peak, so
            // stepping onto the edge or hopping over the column still registers the touch.
            boolean stepped = false;
            for (PoisonBlock pb : poisonBlocks) {
                if (Math.abs(vx - (pb.x + 0.5)) > 1.0 || Math.abs(vz - (pb.z + 0.5)) > 1.0) continue;
                if (vy < pb.y - 0.4 || vy > pb.y + 2.5) continue;
                int key = v.getUniqueId().hashCode() * 31 + pb.x * 17 + pb.z;
                if (poisonedSessions.add(key)) stepped = true;
            }
            if (stepped) {
                // Apply the 3-second poison debuff (real DoT + light-green particle trail).
                poisonDurations.put(v.getUniqueId(), POISON_EFFECT_TICKS);
                world.spawnParticle(org.bukkit.Particle.DUST, v.getLocation().clone().add(0, 0.4, 0), 4,
                        0.25, 0.3, 0.25, 0, new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(170, 255, 190), 0.4f));
                world.playSound(v.getLocation(), org.bukkit.Sound.BLOCK_GRASS_STEP, 0.8f, 0.4f);
            }
            // Damage-over-time for anyone currently poisoned; lands every 10 game ticks (0.5s)
            // even though this step scan runs every 2 ticks.
            Integer dur = poisonDurations.get(v.getUniqueId());
            if (dur != null) {
                // While poisoned: a soft haze of green smoke slowly falls and swirls around the head,
                // spawned each 2-tick sweep so it reads as one continuous cloud over the whole 3s debuff.
                world.spawnParticle(org.bukkit.Particle.WITCH, v.getLocation().clone().add(0, 1.9, 0), 3,
                        0.4, 0.3, 0.4, 0.02, org.bukkit.Color.fromRGB(70, 200, 110));
                int accum = poisonAccum.getOrDefault(v.getUniqueId(), 0) + 2;
                if (accum >= 10) {
                    poisonAccum.put(v.getUniqueId(), accum - 10);
                    poisonDurations.put(v.getUniqueId(), dur - 10);
                    if (dur - 10 <= 0) {
                        poisonDurations.remove(v.getUniqueId());
                        poisonAccum.remove(v.getUniqueId());
                    }
                    com.lieyabull.dung.game.GameManager.playerHurtBypassInvuln(v, poisonTickDamage(rage));
                }
            }
        }
        // Slow ambient shimmer: one soft green speck per root, once per second, so the whole patch
        // glows gently instead of raining spore particles.
        poisonAmbientTick++;
        if (poisonAmbientTick % 10 != 0) return;
        for (PoisonBlock pb : poisonBlocks) {
            org.bukkit.block.Block b = world.getBlockAt(pb.x, pb.y, pb.z);
            if (b.getType() == Material.CRIMSON_STEM) {
                world.spawnParticle(org.bukkit.Particle.DUST, new Location(world, pb.x + 0.5, pb.y + 1.05, pb.z + 0.5), 1,
                        0.05, 0.05, 0.05, 0, new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(150, 230, 170), 0.3f));
            }
        }
    }

    /** Per-tick (0.5s) poison DoT strength, scaled by floor and enrage. */
    private double poisonTickDamage(boolean rage) {
        return (rage ? 5 : 4) + floor * 1.2;
    }

    /** Show timber wall placement: fence blocks rising along the threatened line. Uses the same
     *  {@link #wallCells} footprint as {@link #fireTimberWalls} so the telegraph matches the wall. */
    private void warnWall(Location center, double angle) {
        int baseY = center.getBlockY();
        for (int[] c : wallCells(center, angle)) {
            for (int h = 0; h < WALL_HEIGHT; h++) {
                world.spawnParticle(org.bukkit.Particle.BLOCK, new Location(world, c[0] + 0.5, baseY + h, c[1] + 0.5), 1, 0.2, 0.2, 0.2, Material.OAK_FENCE.createBlockData());
            }
        }
    }

    /** Show root burst direction: green particles along the threatened lane. */
    private void warnRootBurst(Location center, double angle) {
        double dx = Math.cos(angle), dz = Math.sin(angle);
        for (int i = 1; i <= 12; i++) {
            Location l = center.clone().add(dx * i, 1, dz * i);
            world.spawnParticle(org.bukkit.Particle.ITEM, l, 2, 0, 0, 0, new org.bukkit.inventory.ItemStack(Material.VINE));
        }
    }

    /** Telegraph a root burst lane toward every online party member. */
    private void warnRootBursts(Location center) {
        for (Player v : candidates()) {
            if (v == null || !v.isOnline() || defeated) continue;
            double ang = Math.atan2(v.getZ() - center.getZ(), v.getX() - center.getX());
            warnRootBurst(center, ang);
        }
    }

    /** Show an expanding ring for timber walls / poison roots. */
    private void warnRing(Location center) {
        double r = 2 + (warning % 4);
        world.spawnParticle(org.bukkit.Particle.BLOCK, center.clone().add(0, 1, 0), 20, r, 0, r, Material.OAK_LEAVES.createBlockData());
    }

    public boolean isActive() {
        return hp > 0 && boss.isValid();
    }

    public Location location() {
        return boss.getLocation();
    }

    public Entity entity() {
        return boss;
    }

    public void damage(double dmg, Player attacker) {
        damage(dmg, attacker, false);
    }

    public void damage(double dmg, Player attacker, boolean melee) {
        if (defeated) return;
        hp -= dmg;
        bar.setProgress(Math.max(0, hp / maxHp));
        bar.setTitle("§2The Grovekeeper §8" + Math.max(0, (int) hp) + "/" + (int) maxHp);
        // Hit feedback
        world.playSound(boss.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
        world.spawnParticle(org.bukkit.Particle.BLOCK,
                boss.getLocation().clone().add(0, 1.5, 0), 12, 0.5, 0.8, 0.5, Material.OAK_LEAVES.createBlockData());
        // A basic melee hit provokes the Grovekeeper: it hurls the attacker away.
        if (melee && attacker != null && attacker.isOnline() && !attacker.isDead() && boss.isValid() && !defeated) {
            meleeRetribution(attacker);
        }
        // Flanking dash: move behind the attacker
        if (attacker != null && attacker.isOnline() && boss.isValid()) {
            flankBehind(attacker);
        }
        if (hp <= 0) {
            defeated = true;
            boss.remove();
            bar.removeAll();
            bar.setVisible(false);
            Bukkit.removeBossBar(barKey);
            clearRootLogs();
            clearTimberWalls();
            clearPoisonRoots();
            onDefeated.run();
        }
    }

    private void flankBehind(Player target) {
        Location tLoc = target.getLocation();
        Location bLoc = boss.getLocation();
        double dx = tLoc.getX() - bLoc.getX();
        double dz = tLoc.getZ() - bLoc.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist < 0.001) return;
        double nx = dx / dist, nz = dz / dist;
        double behindX = tLoc.getX() - nx * 2.0;
        double behindZ = tLoc.getZ() - nz * 2.0;
        Location behind = new Location(world, behindX, tLoc.getY(), behindZ, tLoc.getYaw() + 180, 0);
        if (isWalkable(behind)) {
            for (int step = 1; step <= 4; step++) {
                double t = step / 4.0;
                double sx = bLoc.getX() + (behindX - bLoc.getX()) * t;
                double sz = bLoc.getZ() + (behindZ - bLoc.getZ()) * t;
                Location stepLoc = new Location(world, sx, behind.getY(), sz, behind.getYaw(), 0);
                boss.teleport(stepLoc);
            }
            world.spawnParticle(org.bukkit.Particle.BLOCK, behind.clone().add(0, 1, 0), 8, 0.5, 0.5, 0.5, Material.OAK_LEAVES.createBlockData());
            world.playSound(behind, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);
        }
    }

    /** Melee retribution: a player who lands a basic melee hit on the Grovekeeper is hurled away
     *  from it and takes 3% max-HP damage (mitigated by the usual shield/defense path). Throttled
     *  to once per second so rapid swings don't chain the launch. */
    private void meleeRetribution(Player attacker) {
        if (meleeRpCd > 0) return;
        meleeRpCd = 20;
        Location b = boss.getLocation();
        Location a = attacker.getLocation();
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist > 0.01) {
            dx /= dist;
            dz /= dist;
        } else {
            dx = 0;
            dz = 1;
        }
        attacker.setVelocity(new org.bukkit.util.Vector(dx * 1.5, 0.85, dz * 1.5));
        int maxHp = 100;
        com.lieyabull.dung.game.DungeonInstance di = com.lieyabull.dung.game.GameManager.instance().instanceOf(attacker);
        if (di != null && di.run() != null) {
            var st = di.run().playerStateOf(attacker.getUniqueId());
            if (st != null) maxHp = st.maxHearts;
        }
        com.lieyabull.dung.game.GameManager.playerHurt(attacker, maxHp * 0.03);
        world.playSound(a, org.bukkit.Sound.ENTITY_GENERIC_HURT, 1.0f, 0.8f);
    }

    private boolean isWalkable(Location l) {
        Material m = l.getWorld().getBlockAt(l).getType();
        Material up = l.getWorld().getBlockAt(l.clone().add(0, 1, 0)).getType();
        return m != Material.BEDROCK && !m.isSolid() && !up.isSolid();
    }

    public void despawn() {
        if (boss != null && boss.isValid()) boss.remove();
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
            Bukkit.removeBossBar(barKey);
        }
        clearRootLogs();
        clearTimberWalls();
        clearPoisonRoots();
    }

    /** Drop a Forest Transmutation Elixir as a reward. Called from onBossDefeated. */
    public static ItemStack createForestPotionReward() {
        return PotionFactory.createForestPotion();
    }
}