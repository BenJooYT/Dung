package com.lieyabull.dung.game;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.dungeon.Floor;
import com.lieyabull.dung.dungeon.FloorGenerator;
import com.lieyabull.dung.dungeon.RoomGen;
import com.lieyabull.dung.dungeon.RoomType;
import com.lieyabull.dung.entity.Enemy;
import com.lieyabull.dung.entity.MobType;
import com.lieyabull.dung.items.ItemPool;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.ui.HUD;
import com.lieyabull.dung.ui.TabUI;
import com.lieyabull.dung.boss.BossController;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns a single live run: lifecycle, per-floor generation + world build, per-room spawning,
 * combat tick, room-clearing rewards, boss encounters, HUD/Tab refresh, and death handling.
 */
public final class GameManager {
    public static final int BASE_Y = 80;
    /** Center-to-center room distance. Rooms can be 13x13 or 17x13/13x17, so spacing is kept
     *  generous to leave a 3..9-block corridor for any shape. Randomized per floor. */
    public static final int MIN_SPACING = 22;
    public static final int MAX_SPACING = 28;
    private int spacing = 25;

    public int spacing() { return spacing; }

    private final Dung plugin;
    private Run run;
    private Player player;
    private World world;
    private Floor.RoomNode curRoom;
    private org.bukkit.scoreboard.Scoreboard board; // single shared board: sidebar + tab coexist
    private final Map<Long, List<Enemy>> roomEnemies = new HashMap<>();
    private final Map<Long, Boolean> roomLocked = new HashMap<>();
    private int fireCd = 0;
    // action-bar throttle cache: only resend when the displayed values actually changed
    private int barTick = 0;
    private double lastBarHearts = -1;
    private double lastBarMana = -1;
    private ItemStack[] lastGear;
    private BossController boss;
    private HUD hud;
    private TabUI tab;
    private boolean running;
    private static GameManager instance;

    public static GameManager instance() {
        return instance;
    }

    public GameManager(Dung plugin) {
        this.plugin = plugin;
        this.hud = new HUD();
        this.tab = new TabUI();
        instance = this;
        startTicker();
    }

    public boolean isRunning() {
        return running && player != null && player.isOnline();
    }

    public Run run() { return run; }
    public Player player() { return player; }
    public World world() { return world; }
    public org.bukkit.scoreboard.Scoreboard board() { return board; }
    public Floor.RoomNode curRoom() { return curRoom; }
    public BossController boss() { return boss; }

    // ---------- lifecycle ----------

    public void startRun(Player p, long seed) {
        if (running) {
            p.sendMessage("§cYou're already in a run. Use /dung leave first.");
            return;
        }
        // reset per-run instance state that would otherwise leak from a prior run
        fireCd = 0;
        lastGear = null;
        lastBarHearts = -1;
        lastBarMana = -1;
        barTick = 0;
        curRoom = null;
        world = plugin.world();
        player = p;
        run = new Run(seed);
        PlayerState ps = new PlayerState(p);
        ps.classId = plugin.meta().profile(p.getUniqueId()).classId;
        ps.upgrades.putAll(plugin.meta().profile(p.getUniqueId()).upgrades);
        ps.recomputeStats(); // apply held weapon/armor + class passives immediately
        run.setPlayerState(ps);
        GameManagerRef.set(this);
        board = org.bukkit.Bukkit.getScoreboardManager().getNewScoreboard();
        player.setScoreboard(board);
        hud.reset(p, board);
        tab.reset(board);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setHealth(20);
        player.setFoodLevel(20);
        running = true;
        enterFloor(0);
    }

    public void enterFloor(int floorIndex) {
        if (run == null) return;
        run.floorIndex = floorIndex;
        clearRoomEntities();
        spacing = ThreadLocalRandom.current().nextInt(MIN_SPACING, MAX_SPACING + 1);
        // generate + build floor
        FloorGenerator gen = new FloorGenerator(new java.util.Random(run.rng.nextLong()), 9, 9, plugin.getConfig().getInt("rooms-per-floor", 7));
        run.floor = gen.generate();
        // build all rooms
        for (Floor.RoomNode n : run.floor.rooms()) {
            RoomGen.build(world, n, BASE_Y, spacing);
        }
        curRoom = run.floor.start;
        enterRoom(curRoom);
        run.floor.visited.clear();
        player.teleport(RoomGen.center(world, run.floor.start, BASE_Y, spacing));
        hud.update(this, board);
        tab.refresh(this, board);
    }

    public void recomputeStats() {
        if (run == null) return;
        run.playerState().recomputeStats();
    }

    public void enterRoom(Floor.RoomNode n) {
        if (curRoom != null) curRoom.visited = true;
        curRoom = n;
        n.visited = true;
        run.floor.visited.add(n);
        roomLocked.put(run.floor.key(n.x, n.z), false);
        // 2.5s grace period on entering a room so spawns never instantly clip you
        run.playerState().invulnUntil = System.currentTimeMillis() + 2500;
        // spawn enemies for combat rooms not yet cleared (secret rooms are reward alcoves)
        if (!n.cleared && (n.type == RoomType.COMBAT || n.type == RoomType.ELITE)) {
            spawnEnemies(n);
            lockDoors(n);
        }
        spawnRoomPickups(n);
        if (n.type == RoomType.SHOP) {
            Location c = RoomGen.center(world, n, BASE_Y, spacing);
            world.getBlockAt(c.getBlockX(), BASE_Y, c.getBlockZ()).setType(Material.EMERALD_BLOCK);
        }
        if (n.type == RoomType.BOSS && !n.cleared && boss == null) {
            onRoomEnterBossCheck();
        }
    }

    /** Detect room crossings from player movement: a room only "becomes current" once the
     *  player is actually inside its interior footprint, so approaching a door never snaps
     *  them to the destination's center. */
    public void onPlayerMoved(Location loc) {
        if (run == null || run.floor == null) return;
        Floor.RoomNode target = null;
        for (Floor.RoomNode rn : run.floor.rooms()) {
            if (insideRoom(loc, rn)) { target = rn; break; }
        }
        if (target == null || target == curRoom) return;
        // only cross if physically adjacent WITH a real door opening, or already visited this floor
        int dir = dirTo(curRoom, target);
        if (!run.floor.visited.contains(target)) {
            if (dir < 0 || !curRoom.doors[dir]) return;
        }
        enterRoom(target);
    }

    /** Compass direction from a to b (d0=N, d1=E, d2=S, d3=W), or -1 if not orthogonal. */
    private int dirTo(Floor.RoomNode a, Floor.RoomNode b) {
        int dx = b.x - a.x, dz = b.z - a.z;
        if (dx == 1) return 1;
        if (dx == -1) return 3;
        if (dz == 1) return 2;
        if (dz == -1) return 0;
        return -1;
    }

    /** True if a location is within the room's interior footprint (walls excluded). */
    private boolean insideRoom(Location loc, Floor.RoomNode rn) {
        double ox = loc.getX() - (rn.x * spacing + RoomGen.WALL);
        double oz = loc.getZ() - (rn.z * spacing + RoomGen.WALL);
        return ox >= 0 && ox < rn.sizeW && oz >= 0 && oz < rn.sizeH;
    }

    private void spawnEnemies(Floor.RoomNode n) {
        List<Enemy> list = new ArrayList<>();
        long k = run.floor.key(n.x, n.z);
        boolean elite = n.type == RoomType.ELITE;
        int count = elite ? 3 : 2 + Math.min(run.floorIndex, 2);
        MobType[] comp = composeMobs(elite, count);
        Location pl = player.getLocation();
        double yaw = Math.toRadians(pl.getYaw());
        for (int i = 0; i < count; i++) {
            Location l = placeInFov(pl, yaw, n, i, count);
            MobType mt = comp[i];
            list.add(new Enemy(world, l, mt, run.floorIndex, n.x * 100 + n.z, player));
        }
        roomEnemies.put(k, list);
        if (elite) {
            // guarantee one elite (buff the first elite spawn so it reads as a serious threat);
            // scale BOTH hp and maxHp so the name bar and health stay consistent
            Enemy top = list.get(0);
            top.hp = top.maxHp * 1.6;
            top.maxHp = top.hp;
            top.entity.setCustomName(top.type.name + " §c" + (int) top.hp + "/" + (int) top.maxHp);
            if (top.entity instanceof org.bukkit.entity.LivingEntity le) {
                le.setMaxHealth(top.maxHp);
                le.setHealth(top.maxHp);
            }
        }
    }

    // Weak/medium pools keep combat fair; heavies are gated so a room can't stack a wall of
    // speed-7 chargers. Elite rooms step up: a guaranteed elite + tankier support.
    private static final MobType[] WEAK = {MobType.GAPER, MobType.FLY, MobType.SPIDER};
    private static final MobType[] STRONG = {MobType.MULLIBOOM, MobType.CHARGER, MobType.MAW};
    private static final MobType[] ELITES = {MobType.ELITE_GAPER, MobType.ELITE_CHARGER};

    /** Build a per-room composition with controlled difficulty, not a fully random dice roll. */
    private MobType[] composeMobs(boolean elite, int count) {
        MobType[] out = new MobType[count];
        if (elite) {
            out[0] = ELITES[ThreadLocalRandom.current().nextInt(ELITES.length)];
            for (int i = 1; i < count; i++) out[i] = pickWeighted(STRONG);
            return out;
        }
        out[0] = pickWeighted(WEAK); // guarantee at least one soft enemy so no room starts unfair
        int chargers = 0, maws = 0;
        for (int i = 1; i < count; i++) {
            MobType m;
            double r = ThreadLocalRandom.current().nextDouble();
            if (r < 0.45) {
                m = pickWeighted(WEAK);
            } else if (r < 0.72) {
                m = MobType.MULLIBOOM;
            } else if (chargers == 0) {
                m = MobType.CHARGER; chargers++;
            } else if (maws == 0) {
                m = MobType.MAW; maws++;
            } else {
                m = MobType.SPIDER;
            }
            out[i] = m;
        }
        return out;
    }

    private MobType pickWeighted(MobType[] pool) {
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    /** Place an enemy inside the room, within the player's 100° facing cone, min 1.5 blocks away. */
    private Location placeInFov(Location pl, double yaw, Floor.RoomNode n, int i, int count) {
        double halfFov = Math.toRadians(50);
        double span = count <= 1 ? 0 : (2 * halfFov / (count - 1)) * (i - (count - 1) / 2.0);
        double ang = yaw + span;
        double dist = 2.5 + ThreadLocalRandom.current().nextDouble() * 5.0;
        Location l = pl.clone().add(Math.sin(ang) * dist, 0, Math.cos(ang) * dist);
        l.setY(BASE_Y + 1);
        // clamp into the room's interior so they always land on the locked room floor
        double minX = n.x * spacing + RoomGen.WALL;
        double minZ = n.z * spacing + RoomGen.WALL;
        l.setX(Math.max(minX, Math.min(minX + n.sizeW - 1, l.getX())));
        l.setZ(Math.max(minZ, Math.min(minZ + n.sizeH - 1, l.getZ())));
        return l;
    }

    private void lockDoors(Floor.RoomNode n) {
        long k = run.floor.key(n.x, n.z);
        roomLocked.put(k, true);
        sealDoors(n, true);
    }

    /** Seal (true) or open (false) every outward door of a room with a barrier wall. The barrier
     *  must sit on the SAME fixed perpendicular line RoomGen carved the doorway (PERP_CENTER), not
     *  the room's geometric center, or square rooms' doors are sealed off by 2 blocks and leak. */
    private void sealDoors(Floor.RoomNode n, boolean close) {
        int[] DX = {0, 1, 0, -1};
        int[] DZ = {-1, 0, 1, 0};
        Location c = RoomGen.center(world, n, BASE_Y, spacing);
        int baseX = n.x * spacing, baseZ = n.z * spacing;
        for (int d = 0; d < 4; d++) {
            if (!n.doors[d]) continue;
            boolean horiz = d == 1 || d == 3;            // E/W: along x, doorway z is the perpendicular
            int half = horiz ? n.sizeW / 2 : n.sizeH / 2;
            // along-axis wall face from the room's geometric center
            int wallX = c.getBlockX() + DX[d] * (half + RoomGen.WALL);
            int wallZ = c.getBlockZ() + DZ[d] * (half + RoomGen.WALL);
            // fixed perpendicular center = where RoomGen actually carved the 3-wide doorway
            int perpC = horiz ? (baseZ + RoomGen.PERP_CENTER) : (baseX + RoomGen.PERP_CENTER);
            for (int off = -1; off <= 1; off++) {
                for (int y = BASE_Y + 1; y <= BASE_Y + RoomGen.ROOM_HEIGHT; y++) {
                    int px = horiz ? wallX : (perpC + off);
                    int pz = horiz ? (perpC + off) : wallZ;
                    if (close) {
                        world.getBlockAt(px, y, pz).setType(Material.BARRIER);
                    } else if (world.getBlockAt(px, y, pz).getType() == Material.BARRIER) {
                        world.getBlockAt(px, y, pz).setType(Material.AIR);
                    }
                }
            }
        }
    }

    /** Snapshot of the gear that affects stats: main hand + the four armor slots. */
    private static ItemStack[] gearSnapshot(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] s = new ItemStack[5];
        s[0] = inv.getItemInMainHand();
        System.arraycopy(inv.getArmorContents(), 0, s, 1, 4);
        return s;
    }

    private void spawnRoomPickups(Floor.RoomNode n) {
        // treasure/secret rooms hand out their reward exactly once, tracked independently of
        // `visited` (which enterRoom sets before this runs)
        if (!n.looted && n.type == RoomType.TREASURE) {
            n.looted = true;
            Location c = RoomGen.center(world, n, BASE_Y, spacing);
            dropGear(c, 3, 2);
        }
        if (!n.looted && n.type == RoomType.SECRET) {
            n.looted = true;
            player.sendMessage("§dYou found a hidden room!");
            Location c = RoomGen.center(world, n, BASE_Y, spacing);
            world.dropItem(c.clone().add(1, 1, 0), ItemPool.randomWeapon(run.floorIndex)).setPickupDelay(0);
            world.dropItem(c.clone().add(-1, 1, 0), ItemPool.randomArmor(run.floorIndex, 0)).setPickupDelay(0);
        }
    }

    private void dropGear(Location c, int count, int roomKind) {
        List<ItemStack> loot = ItemPool.roomReward(run.floorIndex, roomKind);
        for (int i = 0; i < count; i++) {
            ItemStack s = (i < loot.size()) ? loot.get(i) : null;
            if (s == null) s = ItemPool.randomArmor(run.floorIndex, i % 4);
            world.dropItem(c.clone().add(0, 1, 0), s).setPickupDelay(0);
        }
    }

    // ---------- combat tick ----------

    public void tick() {
        if (!isRunning()) return;
        PlayerState st = run.playerState();
        if (st.dead) {
            onDeath();
            resetPlayerToSpawn(); // our-system death: no vanilla screen, so restore the player at spawn
            return;
        }
        // Vanilla death (void/fall/suffocation): tear down the run, then force an immediate respawn
        // so the player is never stranded on the death screen. onRespawn sets the spawn location.
        if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL && player.isDead()) {
            onDeath();
            if (player.isDead()) player.spigot().respawn();
            return;
        }
        // keep combat stats in sync with the ACTUAL gear (main hand + armor) even when it was
        // changed by pickup/drop/click without a held-slot-index change event (prevents stale stats)
        ItemStack[] gearNow = gearSnapshot(player);
        if (!java.util.Arrays.equals(gearNow, lastGear)) {
            lastGear = gearNow;
            st.recomputeStats();
        }
        // drain melee cooldown so repeated swings work
        fireCd = Math.max(0, fireCd - 1);
        // apply gear/class speed multiplier
        float ws = (float) Math.min(0.3, 0.2 * st.speedMult);
        player.setWalkSpeed(ws);
        // room cleared check
        long k = run.floor.key(curRoom.x, curRoom.z);
        List<Enemy> list = roomEnemies.get(k);
        if (list != null && roomLocked.getOrDefault(k, false)) {
            // evict dead OR disappeared entities so the room can always clear (no softlock)
            int before = list.size();
            list.removeIf(e -> e.dead || !e.alive());
            if (list.size() < before && run != null) run.kills += (before - list.size());
            if (list.isEmpty()) {
                onRoomClear(curRoom, k);
            }
        }
        // move + hurt ONLY the current room's enemies (room confinement)
        if (list != null && !list.isEmpty()) {
            int before = list.size();
            list.removeIf(e -> e.dead || !e.alive());
            if (list.size() < before && run != null) run.kills += (before - list.size());
            for (Enemy e : list) e.tick(player, 50);
        }
        if (boss != null) boss.tick(player);
        // resources: mana regen + HP regen + HP sync (PlayerState is the single source of truth)
        st.regenMana();
        st.regenHearts();
        // Sync real HP. Keep hunger below 18 with no saturation so Minecraft's natural
        // hunger-regen never fights our value (that caused the hearts to flicker); food stays
        // high enough that the player cannot starve. Only set when it actually changed.
// Sync real HP into the vanilla heart bar so it reflects PlayerState (the single source
        // of truth): 100 hearts -> the full 20-point bar, so bar = hearts/5. Keep food high with
        // no saturation so Minecraft's natural hunger-regen never fights our value.
        // Sync real HP. The vanilla bar maxes at 20.0 HP, so clamp to that even when gear grants
        // more max hearts (the extra pool is tracked in the numeric HUD, not extra vanilla hearts).
        final double real = Math.min(20.0, Math.max(0.5, st.hearts / 5.0));
        if (Math.abs(player.getHealth() - real) > 1.0E-4) player.setHealth(real);
        player.setSaturation(0);
        player.setFoodLevel(10);
        hud.update(this, board);
        // throttle the per-tick action bar: resend only when the shown values changed or ~every
        // 5 ticks, so the client isn't flooded with identical bar updates every single tick.
        barTick++;
        if (barTick % 5 == 0 || st.hearts != lastBarHearts || st.mana != lastBarMana) {
            hud.sendBar(player, st);
            lastBarHearts = st.hearts;
            lastBarMana = st.mana;
        }
        tab.refresh(this, board);
    }

    public void registerAttack() {
        if (!isRunning() || fireCd > 0) return;
        fireCd = run.playerState().fireRateTicks;
        PlayerState ps = run.playerState();
        org.bukkit.util.Vector dir = player.getEyeLocation().getDirection().normalize();
        // melee arc: probe at each enemy's Y plane so grounded mobs are hittable
        long k = run.floor.key(curRoom.x, curRoom.z);
        List<Enemy> roomList = roomEnemies.getOrDefault(k, List.of());
        Location eyeBase = player.getEyeLocation().clone();
        for (Enemy e : roomList) {
            if (e.dead) continue;
            Location el = e.entity.getLocation().clone();
            // horizontal distance within reach and vertical within reach
            double horiz = Math.hypot(el.getX() - eyeBase.getX(), el.getZ() - eyeBase.getZ());
            double vert = Math.abs(el.getY() - eyeBase.getY());
            if (horiz < ps.reach && vert < 2.0) {
                double dmg = ps.damage * (Math.random() < ps.critChance ? ps.critMult : 1.0);
                e.damage(dmg, player, dir.getX(), dir.getZ());
            }
        }
        if (boss != null && boss.isActive()) {
            Location bl = boss.location();
            double horiz = Math.hypot(bl.getX() - eyeBase.getX(), bl.getZ() - eyeBase.getZ());
            double vert = Math.abs(bl.getY() - eyeBase.getY());
            if (horiz < ps.reach + 0.5 && vert < 3.0) {
                double dmg = ps.damage * (Math.random() < ps.critChance ? ps.critMult : 1.0);
                boss.damage(dmg);
            }
        }
    }

    /** Per-ability [cost, cooldownMs] tuning: mobility is cheap, full-room damage is expensive. */
    private static final Map<String, long[]> ABILITY_COST_CD = Map.of(
        "Rush",        new long[]{ 5, 1000 },
        "Slash",       new long[]{ 12, 2500 },
        "Cleave",      new long[]{ 15, 3000 },
        "Smash",       new long[]{ 18, 3500 },
        "Blade Storm", new long[]{ 25, 4500 },
        "Arcane Bolt", new long[]{ 20, 3500 },
        "Ravage",      new long[]{ 40, 8000 }
    );
    private static final long[] DEFAULT_ABILITY_COST_CD = new long[]{ 15, 3500 };

    /** Cast a weapon ability from the held main-hand item (triggered by sneak key). */
    public void tryCastAbility(Player p, org.bukkit.inventory.ItemStack item) {
        if (!isRunning() || item == null) return;
        PlayerState st = run.playerState();
        var pdc = item.getItemMeta() == null ? null : item.getItemMeta().getPersistentDataContainer();
        if (pdc == null || !pdc.has(org.bukkit.NamespacedKey.minecraft("dung.ability"),
                org.bukkit.persistence.PersistentDataType.STRING)) {
            p.sendMessage("§cYour hand item has no ability.");
            return;
        }
        String id = pdc.get(org.bukkit.NamespacedKey.minecraft("dung.ability"),
                org.bukkit.persistence.PersistentDataType.STRING);
        Integer costI = pdc.get(org.bukkit.NamespacedKey.minecraft("dung.cost"),
                org.bukkit.persistence.PersistentDataType.INTEGER);
        // per-ability cost/cooldown tuning; an explicit per-item dung.cost overrides the table
        long[] cfg = ABILITY_COST_CD.get(id);
        if (cfg == null) cfg = DEFAULT_ABILITY_COST_CD;
        double cost = costI != null ? costI : cfg[0];
        long cd = cfg[1];
        if (!st.canCast(id, cost, cd)) {
            p.sendMessage("§cNot enough mana or on cooldown.");
            return;
        }
        st.spendMana(cost);
        st.startCooldown(id, cd);
        dispatchAbility(id, st);
    }

    /** Distinct weapon abilities (SkyBlock-flavor: each changes how you fight). */
    private void dispatchAbility(String id, PlayerState st) {
        long k = run.floor.key(curRoom.x, curRoom.z);
        List<Enemy> roomList = roomEnemies.getOrDefault(k, List.of());
        double dmg = st.damage * (Math.random() < st.critChance ? st.critMult : 1.0);
        org.bukkit.util.Vector dir = player.getEyeLocation().getDirection().normalize();

        // AOE damage also reaches the living boss in range so it is never ability-immune
        java.util.function.DoubleConsumer hitBoss = (radius) -> {
            if (boss != null && boss.isActive() && boss.location().distance(player.getLocation()) < radius) {
                boss.damage(dmg);
            }
        };

        switch (id) {
            case "Rush":
                player.setVelocity(dir.clone().multiply(1.2).setY(0.4));
                // dash grants a brief window of invulnerability so the lunge-in isn't traded for a
                // hit (keeps any longer existing invulnerability, e.g. the spawn grace period).
                st.invulnUntil = Math.max(st.invulnUntil, System.currentTimeMillis() + 600);
                player.sendMessage("§6Rush!");
                break;
            case "Slash":
                hitBoss.accept(2.5);
                // a single powerful forward strike in a short cone
                for (Enemy e : roomList) {
                    if (e.dead) continue;
                    org.bukkit.util.Vector to = e.entity.getLocation().toVector().subtract(player.getLocation().toVector());
                    to.setY(0);
                    if (to.length() < 2.5 && to.clone().normalize().dot(dir) > 0.4) {
                        e.damage(dmg * 2.0, player, dir.getX(), dir.getZ());
                    }
                }
                player.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, player.getEyeLocation().add(dir.clone().multiply(1.5)), 4, 0.5, 0, 0.5);
                player.sendMessage("§6Slash!");
                break;
            case "Cleave":
                hitBoss.accept(3.0);
                // cone in front
                for (Enemy e : roomList) {
                    if (e.dead) continue;
                    org.bukkit.util.Vector to = e.entity.getLocation().toVector().subtract(player.getLocation().toVector());
                    to.setY(0);
                    if (to.length() < 3 && to.clone().normalize().dot(dir) > 0.5) {
                        e.damage(dmg * 1.5, player, dir.getX(), dir.getZ());
                    }
                }
                player.sendMessage("§6Cleave!");
                break;
            case "Smash":
                hitBoss.accept(4.0);
                for (Enemy e : roomList) {
                    if (!e.dead && e.entity.getLocation().distance(player.getLocation()) < 4) {
                        e.damage(dmg * 1.8, player, 0, 0);
                    }
                }
                world.spawnParticle(org.bukkit.Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 1, 1, 0, 1);
                player.sendMessage("§6Smash!");
                break;
            case "Blade Storm":
                hitBoss.accept(5.0);
                for (Enemy e : roomList) {
                    if (!e.dead && e.entity.getLocation().distance(player.getLocation()) < 5) {
                        e.damage(dmg * 1.2, player, 0, 0);
                    }
                }
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    player.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                            player.getLocation().clone().add(Math.cos(a) * 2, 1, Math.sin(a) * 2), 0);
                }
                player.sendMessage("§6Blade Storm!");
                break;
            case "Arcane Bolt":
                hitBoss.accept(8.0);
                // pierce: damage everything in a line
                for (Enemy e : roomList) {
                    if (e.dead) continue;
                    org.bukkit.util.Vector to = e.entity.getLocation().toVector().subtract(player.getLocation().toVector());
                    to.setY(0);
                    if (to.length() < 8 && to.clone().normalize().dot(dir) > 0.6) {
                        e.damage(dmg * 2.2, player, dir.getX(), dir.getZ());
                    }
                }
                player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, player.getEyeLocation().add(dir.clone().multiply(1.5)), 8, 0.2, 0.2, 0.2);
                player.sendMessage("§6Arcane Bolt!");
                break;
            case "Ravage":
                hitBoss.accept(99.0);
                // every enemy in room, heavy
                for (Enemy e : roomList) {
                    if (!e.dead) e.damage(dmg * 1.5, player, dir.getX(), dir.getZ());
                }
                player.sendMessage("§6Ravage!");
                break;
            default:
                // generic burst
                for (Enemy e : roomList) {
                    if (!e.dead && e.entity.getLocation().distance(player.getLocation()) < 3.5) {
                        e.damage(dmg * 1.2, player, 0, 0);
                    }
                }
                player.sendMessage("§6Ability!");
                break;
        }
    }

    /** In-room shop: spend run coins on gear. Called from a shop block interact. */
    public void openShop() {
        if (!isRunning() || curRoom == null || curRoom.type != RoomType.SHOP) return;
        PlayerState st = run.playerState();
        player.sendMessage("§6--- Shop (Floor " + (run.floorIndex + 1) + ") ---");
        player.sendMessage("§7Coins: §e" + st.coins);
        if (curRoom.shopBought) {
            player.sendMessage("§7(You've already bought here this floor.)");
            return;
        }
        if (st.coins < 8) {
            player.sendMessage("§cYou need 8 coins. Clear combat rooms to earn them.");
            return;
        }
        st.coins -= 8;
        curRoom.shopBought = true;
        int slot = ThreadLocalRandom.current().nextInt(2);
        ItemStack s = slot == 0 ? ItemPool.randomWeapon(run.floorIndex)
                : ItemPool.randomArmor(run.floorIndex, ThreadLocalRandom.current().nextInt(4));
        world.dropItem(RoomGen.center(world, curRoom, BASE_Y, spacing).add(0, 1, 0), s).setPickupDelay(0);
        player.sendMessage("§aPurchased! §7(-§e8 coins§7)");
    }

    private void onRoomClear(Floor.RoomNode n, long k) {
        n.cleared = true;
        roomLocked.put(k, false);
        openDoors(n);
        // reward: coins + chance gear
        int coins = 2 + run.floorIndex;
        run.playerState().coins += coins;
        run.runCoinsEarned += coins;
        List<ItemStack> loot = ItemPool.roomReward(run.floorIndex, n.type.kind);
        for (ItemStack s : loot) {
            world.dropItem(RoomGen.center(world, n, BASE_Y, spacing).add(0, 1, 0), s).setPickupDelay(0);
        }
        player.sendMessage("§aRoom cleared! §7(+§e" + coins + " coins§7)");
    }

    private void openDoors(Floor.RoomNode n) {
        sealDoors(n, false);
    }

    // ---------- boss ----------

    public void onRoomEnterBossCheck() {
        if (curRoom != null && curRoom.type == RoomType.BOSS && !curRoom.cleared && boss == null) {
            boss = new BossController(world, RoomGen.center(world, curRoom, BASE_Y, spacing), run.floorIndex, player, plugin);
            lockDoors(curRoom);
            player.sendMessage("§4The Warden of Floor " + (run.floorIndex + 1) + " awakens!");
        }
    }

    public void onBossDefeated() {
        boss = null;
        curRoom.cleared = true;
        openDoors(curRoom);
        player.sendMessage("§6Boss slain!");
        // guaranteed rare+ loot + big coins
        int coins = 8 + run.floorIndex * 4;
        run.playerState().coins += coins;
        run.runCoinsEarned += coins;
        dropGear(RoomGen.center(world, curRoom, BASE_Y, spacing), 2, 6);
        // bank run coins into permanent progression (survives death). Bank only the coins earned
        // THIS floor (delta since the last bank), not the cumulative wallet — otherwise the same
        // run coins get banked again every floor.
        MetaManager.MetaProfile prof = plugin.meta().profile(player.getUniqueId());
        prof.clears++;
        prof.bestFloor = Math.max(prof.bestFloor, run.floorIndex + 1);
        int earned = run.runCoinsEarned - run.bankedCoins;
        int bank = Math.min(40, Math.max(0, earned));
        run.bankedCoins += bank;
        plugin.meta().addPersistentCoins(player.getUniqueId(), bank);
        plugin.meta().save();
        player.sendMessage("§dYou banked §6" + bank + "§d coins into your persistent wallet.");
        // descend opportunity: placeholder door to next floor
        player.sendMessage("§dA crack opens below... use /dung descend to continue.");
    }

    public void descend() {
        if (curRoom == null || !curRoom.cleared || curRoom.type != RoomType.BOSS) {
            player.sendMessage("§cDefeat the boss first!");
            return;
        }
        enterFloor(run.floorIndex + 1);
    }

    // ---------- utilities ----------

    public static boolean playerHurt(Player p, double dmg) {
        Run r = GameManagerRef.running();
        if (r == null) return false;
        PlayerState ps = r.playerState();
        if (ps.isInvuln() || ps.dead) return false;
        ps.hurt(dmg); // defense mitigation applied inside; HP synced to real player each tick
        // vanilla damage lookalike: red hurt vignette + punch sound without touching real HP
        p.playHurtAnimation(0.0f);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 1.0f, 0.9f);
        if (ps.dead) {
            // death handled in tick
        }
        return true;
    }

    /** Static ref so Enemy can reach the run without circular constructor params. */
    private static final class GameManagerRef {
        static GameManager gm;
        static Run running() { return gm == null ? null : gm.run; }
        static void set(GameManager g) { gm = g; }
    }

    public void onDeath() {
        if (!running) return;
        running = false;
        int floorReached = run.floorIndex + 1;
        int kills = run.kills;
        int runCoins = run.playerState() != null ? run.playerState().coins : 0;
        endRun(false);
        MetaManager.MetaProfile prof = plugin.meta().profile(player.getUniqueId());
        prof.deaths++;
        prof.kills += kills;
        // keep a share of the run's banked-but-unspent persistent coins out of the run pool
        plugin.meta().save();
        String cls = prof.classId;
        if (cls != null && !cls.isEmpty() && Character.isLowerCase(cls.charAt(0))) {
            cls = Character.toUpperCase(cls.charAt(0)) + cls.substring(1);
        }
        player.sendMessage("§c§lYOU DIED §8— Floor " + floorReached);
        if (kills > 0) player.sendMessage("§7  Kills this run: §f" + kills);
        if (runCoins > 0) player.sendMessage("§7  Run coins lost: §e" + runCoins + " §7(run gear + coins are gone)");
        player.sendMessage("");
        player.sendMessage("§7Unlocks you keep:");
        player.sendMessage("§7  Class: §f" + cls);
        player.sendMessage("§7  Persistent coins: §6" + prof.persistentCoins);
        player.sendMessage("§7  Progress: §f" + prof.clears + "§7 floors cleared, best §f" + prof.bestFloor + "§7, §f" + prof.kills + "§7 kills");
        if (prof.persistentCoins >= 20) {
            player.sendMessage("§a  You have enough for: §f/dung give rareweapon");
        } else {
            player.sendMessage("§8  Need §6" + (20 - prof.persistentCoins) + "§8 more coins for a RARE weapon (/dung give rareweapon)");
        }
        player.sendMessage("§7  Try /dung shop, /dung class, /dung start to go again.");
        hud.reset(player, board);
        tab.reset(board);
        clearRoomEntities();
    }

    /** Bring the player back to world spawn after a NON-vanilla death (our st.dead path, where
     *  the player's real HP never hits 0 so there is no death screen). Vanilla deaths (void/fall)
     *  go through the respawn event instead — we must NOT teleport/revive while the player is on
     *  the vanilla death screen, or the client strands there and the Respawn button stops working. */
    private void resetPlayerToSpawn() {
        if (player == null || !player.isOnline()) return;
        player.setHealth(20);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setWalkSpeed(0.2f);
        player.teleport(world.getSpawnLocation());
    }

    public void endRun(boolean playerQuit) {
        running = false;
        if (player != null && player.isOnline()) {
            hud.reset(player, board);
            tab.reset(board);
            // Only clear the inventory when the run ENDS by the player quitting intentionally
            // (or /dung leave). On death we keep the inventory so items bought with permanent
            // coins (/dung give rareweapon) are never destroyed; run gear/coins are lost to the
            // run itself rather than wiped out of the persistent inventory.
            if (playerQuit) player.getInventory().clear();
        }
        clearRoomEntities();
        if (boss != null) { boss.despawn(); boss = null; }
        if (playerQuit && player != null && run != null && run.playerState() != null) {
            // persist progress on quit via save system (elsewhere)
        }
    }

    private void clearRoomEntities() {
        for (List<Enemy> es : roomEnemies.values()) for (Enemy e : es) e.despawn();
        roomEnemies.clear();
        roomLocked.clear();
        if (boss != null) { boss.despawn(); boss = null; }
        // strip any lingering sealed barriers from the old floor so a new run starts clean
        if (world != null && run != null && run.floor != null) {
            for (Floor.RoomNode n : run.floor.rooms()) sealDoors(n, false);
        }
        tearDownDungeon();
    }

    /** Reset every block built by this run back to air so an inactive dungeon disappears. */
    private void tearDownDungeon() {
        if (world == null || run == null || run.floor == null) return;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Floor.RoomNode n : run.floor.rooms()) {
            minX = Math.min(minX, n.x * spacing);
            minZ = Math.min(minZ, n.z * spacing);
            maxX = Math.max(maxX, n.x * spacing + RoomGen.WALL + n.sizeW);
            maxZ = Math.max(maxZ, n.z * spacing + RoomGen.WALL + n.sizeH);
        }
        maxX += spacing; maxZ += spacing; // include the connecting corridors
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = BASE_Y; y <= BASE_Y + RoomGen.ROOM_HEIGHT + 1; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
    }

    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() { tick(); }
        }.runTaskTimer(plugin, 0, 1);
    }

    public void shutdown() {
        if (running) endRun(false);
    }

    // bridge so Run can hold PlayerState; attach in enterFloor
    static {
        // ensure static ref wiring
    }
}
