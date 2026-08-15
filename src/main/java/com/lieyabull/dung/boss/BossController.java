package com.lieyabull.dung.boss;

import com.lieyabull.dung.Dung;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Floor boss: a large, telegraphed combatant with an HP bar and a few patterns. Defeating it
 * opens the way down. Arena stays locked while alive.
 */
public final class BossController {
    private final World world;
    private final Entity boss;
    private final double maxHp;
    private double hp;
    private final int floor;
    private final Player target;
    private final Dung plugin;
    private final KeyedBossBar bar;
    private final org.bukkit.NamespacedKey barKey;
    private int patternTimer = 0;
    // attack state machine (boss stays still; each attack is telegraphed before it lands)
    private static final int ATTACK_BEAM = 1, ATTACK_SLAM = 2, ATTACK_RADIAL = 3;
    private int attackIndex = 0;
    private int attackCd = 0;    // ticks between attacks
    private int warning = 0;     // warning ticks remaining (boss holds still during these)
    private int pending = 0;     // attack to fire once the warning ends
    private double warnAngle = 0;// threatened direction for the telegraphed beam
    private final Location anchor;

    /** Enrage past 50% HP: faster patterns, extra hurt. */
    private boolean enraged() {
        return hp / maxHp <= 0.50;
    }

    public BossController(World w, Location center, int floor, Player target, Dung plugin) {
        this.world = w;
        this.floor = floor;
        this.target = target;
        this.plugin = plugin;
        this.anchor = center;
        this.maxHp = 60 + floor * 25;
        this.hp = maxHp;
        this.boss = w.spawnEntity(center, EntityType.ZOGLIN);
        boss.setPersistent(true);
        // Tag so the damage listener blocks the boss's native vanilla melee (Dung applies its own
        // PlayerState-based damage in tick()/fire(), so the vanilla hit must not reach real HP).
        boss.addScoreboardTag("dung.entity");
        try { boss.setCustomName("§4Warden of Floor " + (floor + 1)); boss.setCustomNameVisible(true); } catch (Throwable ignored) {}
        this.barKey = new org.bukkit.NamespacedKey(Dung.instance(), "dung_boss_" + ThreadLocalRandom.current().nextInt(100000));
        this.bar = Bukkit.createBossBar(barKey, "§4The Warden", BarColor.RED, BarStyle.SEGMENTED_10);
        this.bar.setProgress(1.0);
        this.bar.addPlayer(target);
        this.bar.setVisible(true);
    }

    public void tick(Player p) {
        if (hp <= 0) return;
        if (!boss.isValid()) return;
        patternTimer++;
        boolean rage = enraged();
        Location center = anchor.clone();
        center.setY(p.getY());

        // --- warning phase: boss holds still and shows the threatened direction ---
        if (warning > 0) {
            warning--;
            if (pending == ATTACK_BEAM) warnLane(center, warnAngle);
            else warnRing(center);
            if (warning == 0) fire(p, center, rage);
            return;
        }

        // boss stays still; only a small contact sting if the player walks into it
        if (p.getLocation().distance(center) < 1.6) {
            com.lieyabull.dung.game.GameManager.playerHurt(p, 25 + floor * 10);
        }

        // wait out the cooldown, then pick the next telegraphed attack
        if (attackCd > 0) { attackCd--; return; }
        int pool = rage ? 3 : 2;                       // radial burst is enrage-only
        int atk = (attackIndex % pool) + ATTACK_BEAM;  // 1..pool -> BEAM, SLAM, (RADIAL)
        attackIndex++;
        if (atk == ATTACK_BEAM) {
            // telegraph toward the player's current direction so they can dodge the lane
            warnAngle = Math.atan2(center.getZ() - p.getZ(), center.getX() - p.getX());
        }
        pending = atk;
        warning = rage ? 14 : 18;
        p.sendMessage(telegraphMsg(atk, warnAngle));
    }

    /** Resolve the telegraphed attack once its warning ends. */
    private void fire(Player p, Location center, boolean rage) {
        switch (pending) {
            case ATTACK_BEAM: {
                double dx = Math.cos(warnAngle), dz = Math.sin(warnAngle);
                double px = p.getLocation().getX() - center.getX();
                double pz = p.getLocation().getZ() - center.getZ();
                double along = px * dx + pz * dz;
                double perp = Math.abs(px * dz - pz * dx);
                world.spawnParticle(org.bukkit.Particle.EXPLOSION, center.clone().add(dx * 5, 1, dz * 5), 1, 1, 0, 1);
                if (along > -1 && along < 12 && perp < 2.0) {
                    com.lieyabull.dung.game.GameManager.playerHurt(p, (rage ? 55 : 45) + floor * 15);
                    p.sendMessage("§cThe Warden's beam strikes through you!");
                }
                break;
            }
            case ATTACK_SLAM:
                world.spawnParticle(org.bukkit.Particle.EXPLOSION, center.clone().add(0, 1, 0), 1, 1, 0, 1);
                if (p.getLocation().distance(center) < 3.0) {
                    com.lieyabull.dung.game.GameManager.playerHurt(p, 30 + floor * 10);
                }
                break;
            case ATTACK_RADIAL:
                world.spawnParticle(org.bukkit.Particle.EXPLOSION, center.clone().add(0, 1, 0), 1, 1, 0, 1);
                world.spawnParticle(org.bukkit.Particle.FLAME, center.clone().add(0, 1, 0), 16, 3, 0, 3, 0.1);
                if (p.getLocation().distance(center) < 5.0) {
                    com.lieyabull.dung.game.GameManager.playerHurt(p, 35 + floor * 12);
                }
                break;
        }
        attackCd = rage ? 25 : 40;
    }

    /** Show which direction a beam will fire: a red lane across the arena from the boss. */
    private void warnLane(Location center, double angle) {
        double dx = Math.cos(angle), dz = Math.sin(angle);
        for (int i = 1; i <= 12; i++) {
            Location l = center.clone().add(dx * i, 1, dz * i);
            world.spawnParticle(org.bukkit.Particle.FLAME, l, 1, 0, 0, 0);
        }
    }

    /** Show an expanding ring for radial/slam (no safe direction). */
    private void warnRing(Location center) {
        double r = 2 + (warning % 4);
        world.spawnParticle(org.bukkit.Particle.CRIT, center.clone().add(0, 1, 0), 20, r, 0, r, 0);
    }

    private String telegraphMsg(int atk, double angle) {
        if (atk == ATTACK_BEAM) {
            String dir = directionName(angle);
            return "§cThe Warden telegraphs a beam to the §4" + dir + "§c!";
        }
        return "§cThe Warden's core flares!";
    }

    private String directionName(double angle) {
        String[] names = {"East", "South-East", "South", "South-West", "West", "North-West", "North", "North-East"};
        int idx = (int) Math.round(angle / (Math.PI / 4)) % 8;
        if (idx < 0) idx += 8;
        return names[idx];
    }

    public boolean isActive() {
        return hp > 0 && boss.isValid();
    }

    public Location location() {
        return boss.getLocation();
    }

    public void damage(double dmg) {
        hp -= dmg;
        bar.setProgress(Math.max(0, hp / maxHp));
        bar.setTitle("§4The Warden §8" + Math.max(0, (int) hp) + "/" + (int) maxHp);
        if (hp <= 0) {
            boss.remove();
            bar.removeAll();
            bar.setVisible(false);
            Bukkit.removeBossBar(barKey);
            com.lieyabull.dung.game.GameManager.instance().onBossDefeated();
        }
    }

    public void despawn() {
        if (boss != null && boss.isValid()) boss.remove();
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
            Bukkit.removeBossBar(barKey);
        }
    }
}