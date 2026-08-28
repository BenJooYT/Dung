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
 * Uses a Ravager entity with 3 attacks: Root Burst (beam), Timber Walls (slam), Poisonous Roots (radial).
 * Drops a Forest Transmutation Elixir on defeat.
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
    private int warning = 0;
    private int pending = 0;
    private double warnAngle = 0;

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
        this.maxHp = (60 + floor * 25) * Math.max(1, partySize);
        this.hp = maxHp;
        this.boss = w.spawnEntity(center, EntityType.RAVAGER);
        boss.setPersistent(true);
        // Slightly above base player walk speed but below sprint
        if (boss instanceof org.bukkit.entity.LivingEntity le) {
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
        patternTimer++;
        boolean rage = enraged();
        Location center = boss.getLocation().clone();
        center.setY(p.getY());

        // --- warning phase ---
        if (warning > 0) {
            warning--;
            if (pending == ATTACK_ROOT_BURST) warnRootBurst(center, warnAngle);
            else warnRing(center);
            if (warning == 0) fire(p, center, rage);
            return;
        }

        // contact sting
        if (p.getLocation().distance(center) < 1.6) {
            com.lieyabull.dung.game.GameManager.playerHurt(p, 25 + floor * 10);
        }

        if (attackCd > 0) { attackCd--; return; }
        int pool = rage ? 3 : 2;
        int atk = (attackIndex % pool) + ATTACK_ROOT_BURST;
        attackIndex++;
        if (atk == ATTACK_ROOT_BURST) {
            warnAngle = Math.atan2(p.getZ() - center.getZ(), p.getX() - center.getX());
        }
        pending = atk;
        warning = rage ? 14 : 18;
        p.sendMessage(telegraphMsg(p, atk, warnAngle));
    }

    private void fire(Player p, Location center, boolean rage) {
        switch (pending) {
            case ATTACK_ROOT_BURST: {
                double dx = Math.cos(warnAngle), dz = Math.sin(warnAngle);
                double px = p.getLocation().getX() - center.getX();
                double pz = p.getLocation().getZ() - center.getZ();
                double along = px * dx + pz * dz;
                double perp = Math.abs(px * dz - pz * dx);
                // Root burst visual: green particles at impact
                world.spawnParticle(org.bukkit.Particle.BLOCK, center.clone().add(dx * 5, 1, dz * 5), 16, 1, 0, 1, Material.OAK_LEAVES.createBlockData());
                if (along > -1 && along < 12 && perp < 2.0) {
                    com.lieyabull.dung.game.GameManager.playerHurt(p, (rage ? 55 : 45) + floor * 15);
                    p.sendMessage("§2The Grovekeeper's roots strike through you!");
                }
                break;
            }
            case ATTACK_TIMBER_WALLS:
                world.spawnParticle(org.bukkit.Particle.BLOCK, center.clone().add(0, 1, 0), 24, 1, 0, 1, Material.OAK_LOG.createBlockData());
                world.spawnParticle(org.bukkit.Particle.ITEM, center.clone().add(0, 1, 0), 16, 1, 0, 1, new org.bukkit.inventory.ItemStack(Material.OAK_SAPLING));
                if (p.getLocation().distance(center) < 3.0) {
                    com.lieyabull.dung.game.GameManager.playerHurt(p, 30 + floor * 10);
                }
                break;
            case ATTACK_POISON_ROOTS:
                world.spawnParticle(org.bukkit.Particle.BLOCK, center.clone().add(0, 1, 0), 32, 3, 0, 3, Material.MOSS_BLOCK.createBlockData());
                world.spawnParticle(org.bukkit.Particle.ITEM, center.clone().add(0, 1, 0), 24, 3, 0, 3, new org.bukkit.inventory.ItemStack(Material.SPORE_BLOSSOM));
                if (p.getLocation().distance(center) < 5.0) {
                    com.lieyabull.dung.game.GameManager.playerHurt(p, 35 + floor * 12);
                    // Apply poison damage over time (3 ticks of 5 + floor damage)
                    p.setFireTicks(0); // not fire, just damage
                    // Schedule 3 ticks of poison damage
                    for (int i = 1; i <= 3; i++) {
                        int delay = i * 20; // 1 second per tick
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (p.isOnline() && !defeated) {
                                com.lieyabull.dung.game.GameManager.playerHurt(p, 5 + floor * 3);
                                world.spawnParticle(org.bukkit.Particle.ITEM, p.getLocation().clone().add(0, 1, 0), 4, 0.5, 0.5, 0.5, new org.bukkit.inventory.ItemStack(Material.SPORE_BLOSSOM));
                            }
                        }, delay);
                    }
                }
                break;
        }
        attackCd = rage ? 25 : 40;
    }

    /** Show root burst direction: green particles along the threatened lane. */
    private void warnRootBurst(Location center, double angle) {
        double dx = Math.cos(angle), dz = Math.sin(angle);
        for (int i = 1; i <= 12; i++) {
            Location l = center.clone().add(dx * i, 1, dz * i);
            world.spawnParticle(org.bukkit.Particle.ITEM, l, 2, 0, 0, 0, new org.bukkit.inventory.ItemStack(Material.VINE));
        }
    }

    /** Show an expanding ring for timber walls / poison roots. */
    private void warnRing(Location center) {
        double r = 2 + (warning % 4);
        world.spawnParticle(org.bukkit.Particle.BLOCK, center.clone().add(0, 1, 0), 20, r, 0, r, Material.OAK_LEAVES.createBlockData());
    }

    private String telegraphMsg(Player p, int atk, double angle) {
        if (atk == ATTACK_ROOT_BURST) {
            String dir = directionName(p, angle);
            return "§2The Grovekeeper's roots reach toward the " + dir + "!";
        }
        return "§2The Grovekeeper's bark trembles!";
    }

    private String directionName(Player p, double angle) {
        String[] keys = {"East", "South-East", "South", "South-West",
                "West", "North-West", "North", "North-East"};
        int idx = (int) Math.round(angle / (Math.PI / 4)) % 8;
        if (idx < 0) idx += 8;
        return keys[idx];
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
        if (defeated) return;
        hp -= dmg;
        bar.setProgress(Math.max(0, hp / maxHp));
        bar.setTitle("§2The Grovekeeper §8" + Math.max(0, (int) hp) + "/" + (int) maxHp);
        // Hit feedback
        world.playSound(boss.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
        world.spawnParticle(org.bukkit.Particle.BLOCK,
                boss.getLocation().clone().add(0, 1.5, 0), 12, 0.5, 0.8, 0.5, Material.OAK_LEAVES.createBlockData());
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
    }

    /** Drop a Forest Transmutation Elixir as a reward. Called from onBossDefeated. */
    public static ItemStack createForestPotionReward() {
        return PotionFactory.createForestPotion();
    }
}