package com.lieyabull.dung.entity;

import com.lieyabull.dung.dungeon.Floor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Runtime enemy. A spawned Minecraft mob is resized/scaled and steered each tick toward the
 * player based on its AI kind. Movement is incremental (small steps) so enemies can't jump
 * across a room in one tick; knockback and speeds are tuned to ~9-wide rooms.
 */
public final class Enemy {
    public final MobType type;
    public double maxHp;
    public double hp;
    public final double damage;
    public final double speed;
    public final double knockback;
    public final int room; // room id for scoping
    public final Entity entity;
    public boolean dead;
    private long attackCd = 0;      // game ticks before the next attack is allowed
    private long movePause = 0;     // game ticks frozen after attacking (gives dodge window)
    private long knockTicks = 0;    // game ticks where knockback velocity pushes the mob (no homing)
    private long dashCd = 0;        // charger: ticks until the next dash may begin
    private long dashWindup = 0;    // charger: telegraph pause before the lunge
    private int dashTicks = 0;      // charger: active lunge duration in ticks
    private boolean dashing = false;

    public Enemy(World w, Location loc, MobType type, int floor, int room, Player target) {
        this.type = type;
        this.maxHp = type.hpAt(floor);
        this.hp = maxHp;
        this.damage = type.damageAt(floor);
        this.speed = type.baseSpeed;
        this.knockback = type.baseSpeed * 0.5;
        this.room = room;
        this.entity = w.spawnEntity(loc, entityType(type));
        entity.setPersistent(true);
        // Tag so the damage listener can recognise Dung-created mobs and block their native vanilla
        // attacks (which would otherwise hit the real HP bar and bypass the Dung HP/defense system).
        entity.addScoreboardTag("dung.entity");
        entity.setCustomName(type.name + " §c" + (int) hp + "/" + (int) maxHp);
        entity.setCustomNameVisible(true);
        ((LivingEntity) entity).setMaxHealth(maxHp);
        ((LivingEntity) entity).setHealth(maxHp);
        ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 200, false, false));
        scaleFor(type);
    }

    private EntityType entityType(MobType t) {
        if (t.ai == 2 || t.ai == 3) return EntityType.PHANTOM;
        if (t.ai == 5) return EntityType.PIG;
        if (t.ai == 6) return EntityType.BLAZE;
        return EntityType.ZOMBIE;
    }

    private void scaleFor(MobType t) {
        double s = t.isElite() ? 1.4 : (t.ai == 2 ? 0.5 : 1.0);
        try {
            if (entity instanceof org.bukkit.entity.Phantom ph) {
                ph.setSize((int) (s * 2));
            } else if (entity instanceof org.bukkit.entity.Slime sl) {
                sl.setSize((int) s);
            }
        } catch (Throwable ignored) {}
    }

    public void tick(Player p, long deltaMs) {
        if (dead || !entity.isValid() || p == null || !p.isOnline()) return;
        if (attackCd > 0) attackCd--;
        if (knockTicks > 0) {      // while being knocked back, let physics carry them, no homing
            knockTicks--;
            return;
        }
        if (movePause > 0) {
            movePause--;
            faceTarget(p);
            return;
        }
        // CHARGER (ai==5): telegraphed dash — brief pause, then a fast lunging jump along the
        // line to the player. Falls back to normal homing/melee while on cooldown or in melee range.
        if (type.ai == 5) {
            if (dashing) {
                if (--dashTicks <= 0) dashing = false;
                Location el = entity.getLocation().clone();
                Location pl = p.getLocation().clone();
                el.setY(pl.getY());
                double dx = pl.getX() - el.getX(), dz = pl.getZ() - el.getZ();
                double dist = Math.hypot(dx, dz);
                if (dist > 0.001) {
                    double nx = dx / dist, nz = dz / dist;
                    double lunge = Math.min(speed * 2.5 * (deltaMs / 1000.0), 0.8);
                    Location next = el.clone().add(nx * lunge, 0, nz * lunge);
                    if (isWalkable(next)) entity.teleport(next);
                }
                faceTarget(p);
                return;
            }
            if (dashWindup > 0) { // telegraph: freeze and face the player before lunging
                dashWindup--;
                faceTarget(p);
                return;
            }
            if (dashCd > 0) dashCd--;
            Location el0 = entity.getLocation().clone();
            Location pl0 = p.getLocation().clone();
            el0.setY(pl0.getY());
            double dx0 = pl0.getX() - el0.getX(), dz0 = pl0.getZ() - el0.getZ();
            double dist = Math.hypot(dx0, dz0);
            if (dashCd <= 0 && dist > 1.8) {
                dashWindup = 15;  // ~0.75s warning
                dashCd = 45;      // ~2.25s between dashes
                return;
            }
        }
        double step = speed * (deltaMs / 1000.0) * (type.ai == 2 ? 1.5 : 1.0); // fly is fast
        Location el = entity.getLocation().clone();
        Location pl = p.getLocation().clone();
        el.setY(pl.getY());
        double dx = pl.getX() - el.getX();
        double dz = pl.getZ() - el.getZ();
        double dist = Math.hypot(dx, dz);
        // Elites attack via the default branch at dist < 1.8; their stop distance must be BELOW
        // that or they halt out of reach and never land a hit. 1.2 lets them close in and swing.
        double stopRange = type.isElite() ? 1.2 : 1.5; // stop chasing once close enough to swing
        // track toward the player, but halt at a stalking distance so they don't pile onto you
        if (dist > stopRange && dist > 0.001) {
            double nx = dx / dist, nz = dz / dist;
            double mx = nx * step, mz = nz * step;
            // cap step so it can't cross walls
            double cap = Math.min(0.4, step);
            double sx = Math.signum(mx) * Math.min(Math.abs(mx), cap);
            double sz = Math.signum(mz) * Math.min(Math.abs(mz), cap);
            Location next = el.clone().add(sx, 0, sz);
            if (isWalkable(next)) {
                entity.teleport(next);
            }
        }
        faceTarget(p);
        // AI behaviors: only attack when off cooldown, then freeze briefly to give a dodge window
        boolean attacked = false;
        if (attackCd <= 0) {
            switch (type.ai) {
                case 4 -> { if (dist < 2.2 && p.isValid()) { attackPlayer(p); attacked = true; } }
                case 6 -> { if (dist < 3.2) { attackPlayer(p); attacked = true; } }
                default -> { if (dist < 1.8) { attackPlayer(p); attacked = true; } }
            }
        }
        if (attacked) {
            attackCd = 24 + type.id % 8;   // ~1.2s between hits
            movePause = 10;                // freeze ~0.5s so the player can sidestep
        }
    }

    private boolean isWalkable(Location l) {
        Material m = l.getWorld().getBlockAt(l).getType();
        Material up = l.getWorld().getBlockAt(l.clone().add(0, 1, 0)).getType();
        return m != Material.STONE_BRICKS && up != Material.STONE_BRICKS && m != Material.BEDROCK;
    }

    private void faceTarget(Player p) {
        try {
            Location dir = p.getLocation().subtract(entity.getLocation());
            ((org.bukkit.entity.LivingEntity) entity).setRotation(
                    (float) (Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()))), 0);
        } catch (Throwable ignored) {}
    }

    private void attackPlayer(Player p) {
        // damage handled via Combat to apply i-frames/defense; here just record intent
        if (com.lieyabull.dung.game.GameManager.playerHurt(p, damage)) {
            // brief pause so contact doesn't insta-stack
        }
    }

    public void damage(double dmg, Player source, double knockDirX, double knockDirZ) {
        hp -= dmg;
        if (knockDirX != 0 || knockDirZ != 0) {
            entity.setVelocity(entity.getVelocity().add(new org.bukkit.util.Vector(
                    knockDirX * knockback * 0.3, 0.3, knockDirZ * knockback * 0.3)));
            knockTicks = 4; // ~0.2s of knockback before they resume homing
        }
        if (hp <= 0) {
            dead = true;
            playDeathAnimation();
            entity.remove();
        } else {
            entity.setCustomName(type.name + " §c" + Math.max(0, (int) hp) + "/" + (int) maxHp);
        }
    }

    /** Vanilla-style kill effect: white poof burst + the mob's death sound, then vanish. */
    private void playDeathAnimation() {
        World w = entity.getWorld();
        w.spawnParticle(org.bukkit.Particle.POOF,
                entity.getLocation().clone().add(0, 1, 0), 26, 0.4, 0.4, 0.4, 0.08);
        w.playSound(entity.getLocation(), deathSound(), 1.0f, 0.85f);
    }

    private org.bukkit.Sound deathSound() {
        return switch (entity.getType()) {
            case BLAZE -> org.bukkit.Sound.ENTITY_BLAZE_DEATH;
            case PIG -> org.bukkit.Sound.ENTITY_PIG_DEATH;
            case PHANTOM -> org.bukkit.Sound.ENTITY_PHANTOM_DEATH;
            default -> org.bukkit.Sound.ENTITY_ZOMBIE_DEATH;
        };
    }

    /** True while the backing entity is still valid (not dead nor despawned). */
    public boolean alive() {
        return !dead && entity.isValid();
    }

    public void despawn() {
        if (entity.isValid()) entity.remove();
    }
}