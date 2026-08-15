package com.lieyabull.dung.game;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.boss.BossController;
import com.lieyabull.dung.dungeon.Floor;
import com.lieyabull.dung.dungeon.FloorGenerator;
import com.lieyabull.dung.dungeon.RoomGen;
import com.lieyabull.dung.dungeon.RoomType;
import com.lieyabull.dung.entity.Enemy;
import com.lieyabull.dung.entity.MobType;
import com.lieyabull.dung.items.ItemPool;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.party.Party;
import com.lieyabull.dung.ui.HUD;
import com.lieyabull.dung.ui.TabUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A single dungeon instance shared by a party of players. Each instance has its own floor,
 * room state, enemies, boss, and per-player state. Multiple instances can run concurrently.
 */
public final class DungeonInstance {
    public static final int BASE_Y = 80;
    public static final int MIN_SPACING = 22;
    public static final int MAX_SPACING = 28;
    private int spacing = 25;

    private final Dung plugin;
    private final UUID instanceId;
    private final Party party;
    private Run run;
    private World world;
    private Floor.RoomNode curRoom;
    private org.bukkit.scoreboard.Scoreboard board;
    private final Map<Long, List<Enemy>> roomEnemies = new HashMap<>();
    private final Map<Long, Boolean> roomLocked = new HashMap<>();
    private int fireCd = 0;
    private int barTick = 0;
    private final Map<UUID, Double> lastBarHearts = new HashMap<>();
    private final Map<UUID, Double> lastBarMana = new HashMap<>();
    private final Map<UUID, ItemStack[]> lastGear = new HashMap<>();
    private BossController boss;
    private final Map<UUID, HUD> huds = new HashMap<>();
    private final Map<UUID, TabUI> tabs = new HashMap<>();
    private final Set<UUID> deadPlayers = new HashSet<>();
    private boolean running;

    public DungeonInstance(Dung plugin, Party party) {
        this.plugin = plugin;
        this.instanceId = UUID.randomUUID();
        this.party = party;
    }

    public UUID instanceId() { return instanceId; }
    public Party party() { return party; }
    public Run run() { return run; }
    public World world() { return world; }
    public org.bukkit.scoreboard.Scoreboard board() { return board; }
    public Floor.RoomNode curRoom() { return curRoom; }
    public BossController boss() { return boss; }
    public boolean isRunning() { return running; }

    /** Get the PlayerState for a specific player in this instance. */
    public PlayerState playerStateOf(Player p) {
        return run == null ? null : run.playerStateOf(p.getUniqueId());
    }

    /** Check if a player is part of this dungeon instance. */
    public boolean hasPlayer(Player p) {
        return party.isMember(p.getUniqueId());
    }

    /** Check if any party member is online. */
    private boolean anyOnline() {
        return !party.onlineMembers().isEmpty();
    }

    // ---------- lifecycle ----------

    public void startRun(long seed) {
        if (running) return;
        fireCd = 0;
        lastBarHearts.clear();
        lastBarMana.clear();
        lastGear.clear();
        barTick = 0;
        curRoom = null;
        world = plugin.world();
        run = new Run(seed);

        // Create PlayerState for each party member
        for (Player p : party.onlineMembers()) {
            PlayerState ps = new PlayerState(p);
            ps.classId = plugin.meta().profile(p.getUniqueId()).classId;
            ps.upgrades.putAll(plugin.meta().profile(p.getUniqueId()).upgrades);
            ps.recomputeStats();
            run.addPlayerState(p.getUniqueId(), ps);
        }

        board = Bukkit.getScoreboardManager().getNewScoreboard();
        for (Player p : party.onlineMembers()) {
            p.setScoreboard(board);
            HUD hud = new HUD();
            hud.reset(p, board);
            huds.put(p.getUniqueId(), hud);
            TabUI tab = new TabUI();
            tab.reset(board);
            tabs.put(p.getUniqueId(), tab);
            p.setGameMode(org.bukkit.GameMode.SURVIVAL);
            p.setHealth(20);
            p.setFoodLevel(20);
        }

        running = true;
        grantStarters();
        enterFloor(0);
    }

    private void grantStarters() {
        for (Player p : party.onlineMembers()) {
            if (!hasDungGear(p)) {
                ItemStack[] kit = com.lieyabull.dung.items.GearFactory.starter();
                PlayerInventory inv = p.getInventory();
                inv.addItem(kit[0]);
                org.bukkit.inventory.EquipmentSlot[] slots = {
                        org.bukkit.inventory.EquipmentSlot.HEAD,
                        org.bukkit.inventory.EquipmentSlot.CHEST,
                        org.bukkit.inventory.EquipmentSlot.LEGS,
                        org.bukkit.inventory.EquipmentSlot.FEET
                };
                ItemStack[] armor = inv.getArmorContents();
                for (int i = 0; i < 4; i++) {
                    if (armor[i] == null || armor[i].getType().isAir()) inv.setItem(slots[i], kit[i + 1]);
                }
                p.sendMessage("§7You were given a starter kit: §fFrayed Blade§7 + cloth armor.");
            }
            MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
            if (!prof.hasSeenTutorial) {
                prof.hasSeenTutorial = true;
                p.sendTitle("§cDUNGEON", "§7Clear every room. Find the Warden. Descend deeper.", 10, 70, 10);
                p.sendMessage("§6Clear rooms to earn coins and gear. Find the boss room to go deeper.");
                p.sendMessage("§7Attack: §fLeft-Click    §7Weapon Ability: §fSneak + Right-Click");
                p.sendMessage("§7Class Ability: §fSneak + Drop (Q)    §7Heal: pick up §c♥§7 hearts");
                p.sendMessage("§7Salvage spare armor: §f/salvage§7. Exit: §f/dung leave");
            }
        }
        plugin.meta().save();
    }

    private boolean hasDungGear(Player p) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack s : inv.getContents()) if (isDungGear(s)) return true;
        for (ItemStack s : inv.getArmorContents()) if (isDungGear(s)) return true;
        return isDungGear(inv.getItemInOffHand());
    }

    private static boolean isDungGear(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return false;
        return s.getItemMeta().getPersistentDataContainer().has(
                org.bukkit.NamespacedKey.minecraft(ItemTags.GEAR),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    public void enterFloor(int floorIndex) {
        if (run == null) return;
        run.floorIndex = floorIndex;
        clearRoomEntities();
        spacing = ThreadLocalRandom.current().nextInt(MIN_SPACING, MAX_SPACING + 1);
        FloorGenerator gen = new FloorGenerator(new java.util.Random(run.rng.nextLong()), 9, 9,
                plugin.getConfig().getInt("rooms-per-floor", 7));
        run.floor = gen.generate();
        for (Floor.RoomNode n : run.floor.rooms()) {
            RoomGen.build(world, n, BASE_Y, spacing);
        }
        curRoom = run.floor.start;
        enterRoom(curRoom);
        run.floor.visited.clear();

        // Teleport all party members to the start
        Location startLoc = RoomGen.center(world, run.floor.start, BASE_Y, spacing);
        for (Player p : party.onlineMembers()) {
            p.teleport(startLoc);
        }
        refreshUI();
    }

    public void recomputeStats() {
        if (run == null) return;
        for (Player p : party.onlineMembers()) {
            PlayerState ps = run.playerStateOf(p.getUniqueId());
            if (ps != null) ps.recomputeStats();
        }
    }

    public void enterRoom(Floor.RoomNode n) {
        if (curRoom != null) curRoom.visited = true;
        curRoom = n;
        n.visited = true;
        run.floor.visited.add(n);
        roomLocked.put(run.floor.key(n.x, n.z), false);

        long invulnMs = (n == run.floor.start ? 2500 : 1000);
        for (Player p : party.onlineMembers()) {
            PlayerState ps = run.playerStateOf(p.getUniqueId());
            if (ps != null) {
                ps.invulnUntil = System.currentTimeMillis() + invulnMs;
            }
        }

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

    /** Detect room crossings from any party member's movement. */
    public void onPlayerMoved(Player p, Location loc) {
        if (run == null || run.floor == null) return;
        Floor.RoomNode target = null;
        for (Floor.RoomNode rn : run.floor.rooms()) {
            if (insideRoom(loc, rn)) { target = rn; break; }
        }
        if (target == null || target == curRoom) return;
        int dir = dirTo(curRoom, target);
        if (!run.floor.visited.contains(target)) {
            if (dir < 0 || !curRoom.doors[dir]) return;
        }
        enterRoom(target);
    }

    private int dirTo(Floor.RoomNode a, Floor.RoomNode b) {
        int dx = b.x - a.x, dz = b.z - a.z;
        if (dx == 1) return 1;
        if (dx == -1) return 3;
        if (dz == 1) return 2;
        if (dz == -1) return 0;
        return -1;
    }

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

        // Use the first online player as reference for spawn placement
        Player refPlayer = party.onlineMembers().stream().findFirst().orElse(null);
        if (refPlayer == null) return;

        Location pl = refPlayer.getLocation();
        double yaw = Math.toRadians(pl.getYaw());
        for (int i = 0; i < count; i++) {
            Location l = placeInFov(pl, yaw, n, i, count);
            MobType mt = comp[i];
            list.add(new Enemy(world, l, mt, run.floorIndex, n.x * 100 + n.z, refPlayer));
        }
        roomEnemies.put(k, list);
        if (elite) {
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

    private static final MobType[] WEAK = {MobType.GAPER, MobType.FLY, MobType.SPIDER};
    private static final MobType[] STRONG = {MobType.MULLIBOOM, MobType.CHARGER, MobType.MAW};
    private static final MobType[] ELITES = {MobType.ELITE_GAPER, MobType.ELITE_CHARGER};

    private MobType[] composeMobs(boolean elite, int count) {
        MobType[] out = new MobType[count];
        if (elite) {
            out[0] = ELITES[ThreadLocalRandom.current().nextInt(ELITES.length)];
            for (int i = 1; i < count; i++) out[i] = pickWeighted(STRONG);
            return out;
        }
        out[0] = pickWeighted(WEAK);
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

    private Location placeInFov(Location pl, double yaw, Floor.RoomNode n, int i, int count) {
        double halfFov = Math.toRadians(50);
        double span = count <= 1 ? 0 : (2 * halfFov / (count - 1)) * (i - (count - 1) / 2.0);
        double ang = yaw + span;
        double dist = 2.5 + ThreadLocalRandom.current().nextDouble() * 5.0;
        Location l = pl.clone().add(Math.sin(ang) * dist, 0, Math.cos(ang) * dist);
        l.setY(BASE_Y + 1);
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
        Location c = RoomGen.center(world, n, BASE_Y, spacing);
        world.playSound(c, org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.9f);
        world.spawnParticle(org.bukkit.Particle.CRIT, c.clone().add(0, 1.5, 0), 24, 1.5, 1.5, 8);
        for (Player p : party.onlineMembers()) {
            p.sendActionBar("§cRoom locked — defeat all enemies!");
        }
    }

    private void sealDoors(Floor.RoomNode n, boolean close) {
        int[] DX = {0, 1, 0, -1};
        int[] DZ = {-1, 0, 1, 0};
        Location c = RoomGen.center(world, n, BASE_Y, spacing);
        int baseX = n.x * spacing, baseZ = n.z * spacing;
        for (int d = 0; d < 4; d++) {
            if (!n.doors[d]) continue;
            boolean horiz = d == 1 || d == 3;
            int half = horiz ? n.sizeW / 2 : n.sizeH / 2;
            int wallX = c.getBlockX() + DX[d] * (half + RoomGen.WALL);
            int wallZ = c.getBlockZ() + DZ[d] * (half + RoomGen.WALL);
            int perpC = horiz ? (baseZ + RoomGen.PERP_CENTER) : (baseX + RoomGen.PERP_CENTER);
            for (int off = -1; off <= 1; off++) {
                for (int y = BASE_Y + 1; y <= BASE_Y + RoomGen.ROOM_HEIGHT; y++) {
                    int px = horiz ? wallX : (perpC + off);
                    int pz = horiz ? (perpC + off) : wallZ;
                    if (close) {
                        world.getBlockAt(px, y, pz).setType(Material.IRON_BARS);
                    } else if (world.getBlockAt(px, y, pz).getType() == Material.IRON_BARS) {
                        world.getBlockAt(px, y, pz).setType(Material.AIR);
                    }
                }
            }
        }
    }

    private static ItemStack[] gearSnapshot(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] s = new ItemStack[5];
        s[0] = inv.getItemInMainHand();
        System.arraycopy(inv.getArmorContents(), 0, s, 1, 4);
        return s;
    }

    private void spawnRoomPickups(Floor.RoomNode n) {
        if (!n.looted && n.type == RoomType.TREASURE) {
            n.looted = true;
            Location c = RoomGen.center(world, n, BASE_Y, spacing);
            dropGear(c, 3, 2);
        }
        if (!n.looted && n.type == RoomType.SECRET) {
            n.looted = true;
            for (Player p : party.onlineMembers()) {
                p.sendMessage("§dYou found a hidden room!");
            }
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
        if (!running || !anyOnline()) return;

        // Check death for each player
        for (Player p : party.onlineMembers()) {
            PlayerState st = run.playerStateOf(p.getUniqueId());
            if (st == null) continue;
            if (st.dead) {
                onPlayerDeath(p);
                continue;
            }
            if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL && p.isDead()) {
                onPlayerDeath(p);
                if (p.isDead()) p.spigot().respawn();
                continue;
            }

            // Sync gear
            ItemStack[] gearNow = gearSnapshot(p);
            ItemStack[] last = lastGear.get(p.getUniqueId());
            if (!java.util.Arrays.equals(gearNow, last)) {
                lastGear.put(p.getUniqueId(), gearNow);
                st.recomputeStats();
            }

            // Apply speed
            float ws = (float) Math.min(0.3, 0.2 * st.speedMult);
            p.setWalkSpeed(ws);
        }

        // fireCd for melee (shared across the instance)
        fireCd = Math.max(0, fireCd - 1);

        // Room cleared check
        long k = run.floor.key(curRoom.x, curRoom.z);
        List<Enemy> list = roomEnemies.get(k);
        if (list != null && roomLocked.getOrDefault(k, false)) {
            int before = list.size();
            list.removeIf(e -> e.dead || !e.alive());
            if (list.size() < before && run != null) run.kills += (before - list.size());
            if (list.isEmpty()) {
                onRoomClear(curRoom, k);
            }
        }

        // Tick enemies
        if (list != null && !list.isEmpty()) {
            int before = list.size();
            list.removeIf(e -> e.dead || !e.alive());
            if (list.size() < before && run != null) run.kills += (before - list.size());
            // Tick enemies against the nearest party member
            for (Enemy e : list) {
                Player nearest = nearestPlayer(e.entity.getLocation());
                if (nearest != null) e.tick(nearest, 50);
            }
        }

        if (boss != null) {
            Player nearest = nearestPlayer(boss.location());
            if (nearest != null) boss.tick(nearest);
        }

        // Resources and HP sync for each player
        for (Player p : party.onlineMembers()) {
            PlayerState st = run.playerStateOf(p.getUniqueId());
            if (st == null) continue;
            st.regenMana();
            st.regenHearts();
            final double real = Math.min(20.0, Math.max(0.1, st.hearts / st.maxHearts * 20.0));
            if (Math.abs(p.getHealth() - real) > 1.0E-4) p.setHealth(real);
            p.setSaturation(0);
            p.setFoodLevel(10);
        }

        refreshUI();
    }

    /** Find the nearest online party member to a location. */
    private Player nearestPlayer(Location loc) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player p : party.onlineMembers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d < best) { best = d; nearest = p; }
        }
        return nearest;
    }

    public void registerAttack(Player p) {
        if (!running || fireCd > 0) return;
        PlayerState ps = run.playerStateOf(p.getUniqueId());
        if (ps == null) return;
        fireCd = ps.fireRateTicks;

        // Apply damage boost (War Cry) and guaranteed crit (Shadow Step)
        double baseDmg = ps.damage;
        if (ps.hasDamageBoost()) baseDmg *= ps.damageBoostMult;
        boolean guaranteeCrit = ps.hasGuaranteedCrit();

        org.bukkit.util.Vector dir = p.getEyeLocation().getDirection().normalize();
        long k = run.floor.key(curRoom.x, curRoom.z);
        List<Enemy> roomList = roomEnemies.getOrDefault(k, List.of());
        Location eyeBase = p.getEyeLocation().clone();
        for (Enemy e : roomList) {
            if (e.dead) continue;
            Location el = e.entity.getLocation().clone();
            double horiz = Math.hypot(el.getX() - eyeBase.getX(), el.getZ() - eyeBase.getZ());
            double vert = Math.abs(el.getY() - eyeBase.getY());
            if (horiz < ps.reach && vert < 2.0) {
                boolean crit = guaranteeCrit || Math.random() < ps.critChance;
                double dmg = baseDmg * (crit ? ps.critMult : 1.0);
                e.damage(dmg, p, dir.getX(), dir.getZ());
            }
        }
        if (boss != null && boss.isActive()) {
            Location bl = boss.location();
            double horiz = Math.hypot(bl.getX() - eyeBase.getX(), bl.getZ() - eyeBase.getZ());
            double vert = Math.abs(bl.getY() - eyeBase.getY());
            if (horiz < ps.reach + 0.5 && vert < 3.0) {
                boolean crit = guaranteeCrit || Math.random() < ps.critChance;
                double dmg = baseDmg * (crit ? ps.critMult : 1.0);
                boss.damage(dmg);
            }
        }
    }

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

    // Class-specific active abilities: [manaCost, cooldownMs]
    private static final Map<String, long[]> CLASS_ABILITY_COST_CD = Map.of(
        "warrior", new long[]{ 10, 8000 },
        "mage",    new long[]{ 25, 6000 },
        "ranger",  new long[]{ 15, 5000 }
    );

    public void tryCastAbility(Player p, ItemStack item) {
        if (!running || item == null) return;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) return;
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
        dispatchAbility(id, st, p);
    }

    /**
     * Cast the player's class-specific active ability.
     * Triggered by sneak + drop (Q) while in a run.
     */
    public void tryCastClassAbility(Player p) {
        if (!running) return;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) return;

        String classId = st.classId;
        long[] cfg = CLASS_ABILITY_COST_CD.get(classId);
        if (cfg == null) {
            p.sendMessage("§cYour class has no active ability.");
            return;
        }
        double cost = cfg[0];
        long cd = cfg[1];
        String abilityKey = "class_" + classId;
        if (!st.canCast(abilityKey, cost, cd)) {
            p.sendMessage("§cNot enough mana or on cooldown.");
            return;
        }
        st.spendMana(cost);
        st.startCooldown(abilityKey, cd);
        dispatchClassAbility(classId, st, p);
    }

    private void dispatchClassAbility(String classId, PlayerState st, Player caster) {
        long k = run.floor.key(curRoom.x, curRoom.z);
        List<Enemy> roomList = roomEnemies.getOrDefault(k, List.of());
        double dmg = st.damage * (Math.random() < st.critChance ? st.critMult : 1.0);
        org.bukkit.util.Vector dir = caster.getEyeLocation().getDirection().normalize();

        switch (classId) {
            case "warrior":
                // War Cry: boost all party members' damage by 30% for 5 seconds, brief invuln
                long warCryUntil = System.currentTimeMillis() + 5000;
                for (Player pm : party.onlineMembers()) {
                    PlayerState pst = run.playerStateOf(pm.getUniqueId());
                    if (pst != null) {
                        pst.damageBoostUntil = warCryUntil;
                        pst.damageBoostMult = 1.3;
                        pm.sendMessage("§6War Cry! Damage boosted by 30% for 5s!");
                    }
                }
                st.invulnUntil = Math.max(st.invulnUntil, System.currentTimeMillis() + 1000);
                world.spawnParticle(org.bukkit.Particle.FLASH, caster.getLocation().add(0, 1, 0), 1, 0, 0, 0);
                world.spawnParticle(org.bukkit.Particle.CRIT, caster.getLocation().add(0, 1, 0), 20, 1.5, 1, 1.5);
                caster.sendMessage("§6§lWAR CRY!");
                break;

            case "mage":
                // Arcane Nova: AoE damage (2x) to all enemies within 5 blocks
                java.util.function.DoubleConsumer hitBossNova = (radius) -> {
                    if (boss != null && boss.isActive() && boss.location().distance(caster.getLocation()) < radius) {
                        boss.damage(dmg * 2.0);
                    }
                };
                hitBossNova.accept(5.0);
                for (Enemy e : roomList) {
                    if (!e.dead && e.entity.getLocation().distance(caster.getLocation()) < 5) {
                        e.damage(dmg * 2.0, caster, 0, 0);
                    }
                }
                world.spawnParticle(org.bukkit.Particle.CRIT, caster.getLocation().add(0, 1, 0), 40, 2, 1, 2);
                world.spawnParticle(org.bukkit.Particle.PORTAL, caster.getLocation().add(0, 1, 0), 20, 2, 1, 2);
                world.playSound(caster.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.7f);
                caster.sendMessage("§d§lARCANE NOVA!");
                break;

            case "ranger":
                // Shadow Step: teleport behind the nearest enemy, guarantee next crit
                Enemy target = null;
                double bestDist = Double.MAX_VALUE;
                for (Enemy e : roomList) {
                    if (e.dead) continue;
                    double dist = e.entity.getLocation().distanceSquared(caster.getLocation());
                    if (dist < bestDist) { bestDist = dist; target = e; }
                }
                if (target != null) {
                    Location el = target.entity.getLocation();
                    org.bukkit.util.Vector away = el.toVector().subtract(caster.getLocation().toVector()).setY(0).normalize();
                    Location behind = el.clone().add(away.clone().multiply(-2));
                    behind.setY(el.getY());
                    behind.setYaw(caster.getLocation().getYaw());
                    behind.setPitch(caster.getLocation().getPitch());
                    caster.teleport(behind);
                    caster.sendMessage("§aShadow Stepped behind " + target.type.name + "!");
                } else if (boss != null && boss.isActive()) {
                    Location bl = boss.location();
                    org.bukkit.util.Vector away = bl.toVector().subtract(caster.getLocation().toVector()).setY(0).normalize();
                    Location behind = bl.clone().add(away.clone().multiply(-3));
                    behind.setY(bl.getY());
                    behind.setYaw(caster.getLocation().getYaw());
                    behind.setPitch(caster.getLocation().getPitch());
                    caster.teleport(behind);
                    caster.sendMessage("§aShadow Stepped behind the Warden!");
                } else {
                    // No enemies — short forward dash
                    caster.setVelocity(dir.clone().multiply(1.0).setY(0.3));
                    caster.sendMessage("§aShadow Step — no enemies nearby.");
                }
                // Guarantee next crit within 1.5 seconds
                st.guaranteedCritUntil = System.currentTimeMillis() + 1500;
                world.spawnParticle(org.bukkit.Particle.SMOKE, caster.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5);
                world.spawnParticle(org.bukkit.Particle.CRIT, caster.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
                world.playSound(caster.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                caster.sendMessage("§b§lSHADOW STEP!");
                break;
        }
    }

    private void dispatchAbility(String id, PlayerState st, Player caster) {
        long k = run.floor.key(curRoom.x, curRoom.z);
        List<Enemy> roomList = roomEnemies.getOrDefault(k, List.of());
        double dmg = st.damage * (Math.random() < st.critChance ? st.critMult : 1.0);
        org.bukkit.util.Vector dir = caster.getEyeLocation().getDirection().normalize();

        java.util.function.DoubleConsumer hitBoss = (radius) -> {
            if (boss != null && boss.isActive() && boss.location().distance(caster.getLocation()) < radius) {
                boss.damage(dmg);
            }
        };

        switch (id) {
            case "Rush":
                caster.setVelocity(dir.clone().multiply(1.2).setY(0.4));
                st.invulnUntil = Math.max(st.invulnUntil, System.currentTimeMillis() + 600);
                caster.sendMessage("§6Rush!");
                break;
            case "Slash":
                hitBoss.accept(2.5);
                for (Enemy e : roomList) {
                    if (e.dead) continue;
                    org.bukkit.util.Vector to = e.entity.getLocation().toVector().subtract(caster.getLocation().toVector());
                    to.setY(0);
                    if (to.length() < 2.5 && to.clone().normalize().dot(dir) > 0.4) {
                        e.damage(dmg * 2.0, caster, dir.getX(), dir.getZ());
                    }
                }
                caster.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, caster.getEyeLocation().add(dir.clone().multiply(1.5)), 4, 0.5, 0, 0.5);
                caster.sendMessage("§6Slash!");
                break;
            case "Cleave":
                hitBoss.accept(3.0);
                for (Enemy e : roomList) {
                    if (e.dead) continue;
                    org.bukkit.util.Vector to = e.entity.getLocation().toVector().subtract(caster.getLocation().toVector());
                    to.setY(0);
                    if (to.length() < 3 && to.clone().normalize().dot(dir) > 0.5) {
                        e.damage(dmg * 1.5, caster, dir.getX(), dir.getZ());
                    }
                }
                caster.sendMessage("§6Cleave!");
                break;
            case "Smash":
                hitBoss.accept(4.0);
                for (Enemy e : roomList) {
                    if (!e.dead && e.entity.getLocation().distance(caster.getLocation()) < 4) {
                        e.damage(dmg * 1.8, caster, 0, 0);
                    }
                }
                world.spawnParticle(org.bukkit.Particle.EXPLOSION, caster.getLocation().add(0, 1, 0), 1, 1, 0, 1);
                caster.sendMessage("§6Smash!");
                break;
            case "Blade Storm":
                hitBoss.accept(5.0);
                for (Enemy e : roomList) {
                    if (!e.dead && e.entity.getLocation().distance(caster.getLocation()) < 5) {
                        e.damage(dmg * 1.2, caster, 0, 0);
                    }
                }
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    caster.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                            caster.getLocation().clone().add(Math.cos(a) * 2, 1, Math.sin(a) * 2), 0);
                }
                caster.sendMessage("§6Blade Storm!");
                break;
            case "Arcane Bolt":
                hitBoss.accept(8.0);
                for (Enemy e : roomList) {
                    if (e.dead) continue;
                    org.bukkit.util.Vector to = e.entity.getLocation().toVector().subtract(caster.getLocation().toVector());
                    to.setY(0);
                    if (to.length() < 8 && to.clone().normalize().dot(dir) > 0.6) {
                        e.damage(dmg * 2.2, caster, dir.getX(), dir.getZ());
                    }
                }
                caster.getWorld().spawnParticle(org.bukkit.Particle.CRIT, caster.getEyeLocation().add(dir.clone().multiply(1.5)), 8, 0.2, 0.2, 0.2);
                caster.sendMessage("§6Arcane Bolt!");
                break;
            case "Ravage":
                hitBoss.accept(99.0);
                for (Enemy e : roomList) {
                    if (!e.dead) e.damage(dmg * 1.5, caster, dir.getX(), dir.getZ());
                }
                caster.sendMessage("§6Ravage!");
                break;
            default:
                for (Enemy e : roomList) {
                    if (!e.dead && e.entity.getLocation().distance(caster.getLocation()) < 3.5) {
                        e.damage(dmg * 1.2, caster, 0, 0);
                    }
                }
                caster.sendMessage("§6Ability!");
                break;
        }
    }

    /** In-room shop: open the chest GUI shop. */
    public void openShop(Player p) {
        if (!running || curRoom == null || curRoom.type != RoomType.SHOP) return;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) return;
        plugin.shopUI().openRunShop(p, this);
    }

    private void onRoomClear(Floor.RoomNode n, long k) {
        n.cleared = true;
        roomLocked.put(k, false);
        openDoors(n);
        int coins = 2 + run.floorIndex;
        // Give coins to all party members
        for (Player p : party.onlineMembers()) {
            PlayerState st = run.playerStateOf(p.getUniqueId());
            if (st != null) {
                st.coins += coins;
                run.runCoinsEarned += coins;
                p.sendMessage("§aRoom cleared! §7(+§e" + coins + " coins§7)");
            }
        }
        List<ItemStack> loot = ItemPool.roomReward(run.floorIndex, n.type.kind);
        for (ItemStack s : loot) {
            world.dropItem(RoomGen.center(world, n, BASE_Y, spacing).add(0, 1, 0), s).setPickupDelay(0);
        }
    }

    private void openDoors(Floor.RoomNode n) {
        sealDoors(n, false);
        Location c = RoomGen.center(world, n, BASE_Y, spacing);
        world.playSound(c, org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.2f);
        world.spawnParticle(org.bukkit.Particle.CRIT, c.clone().add(0, 1.5, 0), 12, 8, 1.5, 8);
        for (Player p : party.onlineMembers()) {
            p.sendActionBar("§aDoors opened!");
        }
    }

    // ---------- boss ----------

    public void onRoomEnterBossCheck() {
        if (curRoom != null && curRoom.type == RoomType.BOSS && !curRoom.cleared && boss == null) {
            Player leader = Bukkit.getPlayer(party.leader());
            if (leader == null) leader = party.onlineMembers().stream().findFirst().orElse(null);
            if (leader == null) return;
            // Scale boss HP by party size
            int partySize = Math.max(1, party.onlineMembers().size());
            boss = new BossController(world, RoomGen.center(world, curRoom, BASE_Y, spacing),
                    run.floorIndex, leader, plugin, partySize, this::onBossDefeated);
            // Add all party members as boss bar viewers
            for (Player p : party.onlineMembers()) {
                if (!p.equals(leader)) boss.addViewer(p);
            }
            lockDoors(curRoom);
            for (Player p : party.onlineMembers()) {
                p.sendMessage("§4The Warden of Floor " + (run.floorIndex + 1) + " awakens!");
            }
        }
    }

    public void onBossDefeated() {
        boss = null;
        curRoom.cleared = true;
        openDoors(curRoom);
        for (Player p : party.onlineMembers()) {
            p.sendMessage("§6Boss slain!");
        }
        int coins = 8 + run.floorIndex * 4;
        for (Player p : party.onlineMembers()) {
            PlayerState st = run.playerStateOf(p.getUniqueId());
            if (st != null) {
                st.coins += coins;
                run.runCoinsEarned += coins;
            }
        }
        dropGear(RoomGen.center(world, curRoom, BASE_Y, spacing), 2, 6);
        // Bank coins for each player — calculate once, distribute to all
        int earned = run.runCoinsEarned - run.bankedCoins;
        int bank = Math.min(40, Math.max(0, earned));
        run.bankedCoins += bank;
        for (Player p : party.onlineMembers()) {
            MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
            prof.clears++;
            prof.bestFloor = Math.max(prof.bestFloor, run.floorIndex + 1);
            plugin.meta().addPersistentCoins(p.getUniqueId(), bank);
            p.sendMessage("§dYou banked §6" + bank + "§d coins into your persistent wallet.");
        }
        plugin.meta().save();
        for (Player p : party.onlineMembers()) {
            p.sendMessage("§dA crack opens below... use /dung descend to continue.");
        }
    }

    public void descend() {
        if (curRoom == null || !curRoom.cleared || curRoom.type != RoomType.BOSS) {
            for (Player p : party.onlineMembers()) {
                p.sendMessage("§cDefeat the boss first!");
            }
            return;
        }
        enterFloor(run.floorIndex + 1);
    }

    // ---------- utilities ----------

    public boolean playerHurt(Player p, double dmg) {
        PlayerState ps = run == null ? null : run.playerStateOf(p.getUniqueId());
        if (ps == null) return false;
        if (ps.isInvuln() || ps.dead) return false;
        ps.hurt(dmg);
        p.playHurtAnimation(0.0f);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 1.0f, 0.9f);
        return true;
    }

    public void onPlayerDeath(Player p) {
        if (!running) return;
        UUID pid = p.getUniqueId();
        if (deadPlayers.contains(pid)) return;
        deadPlayers.add(pid);
        PlayerState st = run.playerStateOf(pid);
        if (st == null) return;

        stripRunGear(p);
        int floorReached = run.floorIndex + 1;
        int kills = run.kills;
        int runCoins = st.coins;

        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        prof.deaths++;
        prof.kills += kills;
        plugin.meta().save();

        String cls = prof.classId;
        if (cls != null && !cls.isEmpty() && Character.isLowerCase(cls.charAt(0))) {
            cls = Character.toUpperCase(cls.charAt(0)) + cls.substring(1);
        }
        p.sendMessage("§c§lYOU DIED §8— Floor " + floorReached);
        if (kills > 0) p.sendMessage("§7  Kills this run: §f" + kills);
        if (runCoins > 0) p.sendMessage("§7  Run coins lost: §e" + runCoins + " §7(run gear + coins are gone)");
        p.sendMessage("");
        p.sendMessage("§7Unlocks you keep:");
        p.sendMessage("§7  Class: §f" + cls);
        p.sendMessage("§7  Persistent coins: §6" + prof.persistentCoins);
        p.sendMessage("§7  Progress: §f" + prof.clears + "§7 floors cleared, best §f" + prof.bestFloor + "§7, §f" + prof.kills + "§7 kills");
        if (prof.persistentCoins >= 20) {
            p.sendMessage("§a  You have enough for: §f/shop weapon");
        } else {
            p.sendMessage("§8  Need §6" + (20 - prof.persistentCoins) + "§8 more coins for a weapon (/shop weapon)");
        }
        p.sendMessage("§7  Try /shop, /upgrades, or /dung start to go again.");

        // Reset player
        p.setHealth(20);
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        p.setWalkSpeed(0.2f);
        p.teleport(world.getSpawnLocation());

        HUD hud = huds.get(p.getUniqueId());
        if (hud != null) hud.reset(p, board);
        TabUI tab = tabs.get(p.getUniqueId());
        if (tab != null) tab.reset(board);

        // Clean up per-player state
        lastGear.remove(pid);
        huds.remove(pid);
        tabs.remove(pid);
        // Remove player from the instance
        party.removeMember(pid);
        if (party.isEmpty()) {
            endRun();
        }
    }

    private static void stripRunGear(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack s = inv.getItem(slot);
            if (s != null && isRunOnly(s)) inv.setItem(slot, null);
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && isRunOnly(off)) inv.setItemInOffHand(null);
        org.bukkit.inventory.EquipmentSlot[] slots = {
                org.bukkit.inventory.EquipmentSlot.FEET,
                org.bukkit.inventory.EquipmentSlot.LEGS,
                org.bukkit.inventory.EquipmentSlot.CHEST,
                org.bukkit.inventory.EquipmentSlot.HEAD
        };
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && isRunOnly(armor[i])) inv.setItem(slots[i], null);
        }
    }

    private static boolean isRunOnly(ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return false;
        if (s.getType() == Material.GOLD_NUGGET) return true;
        if (s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        boolean isGear = pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.GEAR),
                org.bukkit.persistence.PersistentDataType.STRING);
        boolean persistent = pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.PERSISTENT),
                org.bukkit.persistence.PersistentDataType.STRING);
        return isGear && !persistent;
    }

    public void endRun() {
        running = false;
        for (Player p : party.onlineMembers()) {
            HUD hud = huds.get(p.getUniqueId());
            if (hud != null) hud.reset(p, board);
            TabUI tab = tabs.get(p.getUniqueId());
            if (tab != null) tab.reset(board);
            stripRunGear(p);
        }
        clearRoomEntities();
        huds.clear();
        tabs.clear();
        deadPlayers.clear();
        // Remove this instance from the GameManager registry
        GameManager.instance().removeInstance(this);
    }

    private void clearRoomEntities() {
        for (List<Enemy> es : roomEnemies.values()) for (Enemy e : es) e.despawn();
        roomEnemies.clear();
        roomLocked.clear();
        if (boss != null) { boss.despawn(); boss = null; }
        if (world != null && run != null && run.floor != null) {
            for (Floor.RoomNode n : run.floor.rooms()) sealDoors(n, false);
        }
        tearDownDungeon();
    }

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
        maxX += spacing; maxZ += spacing;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = BASE_Y; y <= BASE_Y + RoomGen.ROOM_HEIGHT + 1; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    // ---------- UI ----------

    private void refreshUI() {
        for (Player p : party.onlineMembers()) {
            HUD hud = huds.get(p.getUniqueId());
            TabUI tab = tabs.get(p.getUniqueId());
            if (hud != null) hud.update(p, this, board);
            if (tab != null) tab.refresh(p, this, board);

            // Throttled action bar
            PlayerState st = run.playerStateOf(p.getUniqueId());
            if (st == null) continue;
            barTick++;
            Double lastH = lastBarHearts.get(p.getUniqueId());
            Double lastM = lastBarMana.get(p.getUniqueId());
            if (barTick % 5 == 0 || st.hearts != (lastH != null ? lastH : -1) || st.mana != (lastM != null ? lastM : -1)) {
                hud.sendBar(p, st);
                lastBarHearts.put(p.getUniqueId(), st.hearts);
                lastBarMana.put(p.getUniqueId(), st.mana);
            }
        }
    }
}