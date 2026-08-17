package com.lieyabull.dung.entity;

import com.lieyabull.dung.dungeon.Floor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Runtime enemy. Each mob type has a distinct AI behavior that goes beyond simple chasing:
 * Gaper — shambles toward the player, occasionally spits a short-range projectile
 * Fly — swarms erratically around the player, flees at low HP
 * Spider — climbs walls/ceiling, leaps at the player from range
 * Mulliboom — walks slowly toward the player, explodes on death dealing AoE damage
 * Charger — telegraphed dash attack with brief windup
 * Maw — stationary ranged enemy that fires projectiles at the player
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
    // Charger dash state
    private long dashCd = 0;        // ticks until the next dash may begin
    private long dashWindup = 0;    // telegraph pause before the lunge
    private int dashTicks = 0;      // active lunge duration in ticks
    private boolean dashing = false;
    // Gaper spit state
    private long spitCd = 0;        // ticks until the next spit attack
    // Fly swarm state
    private long fleeUntil = 0;     // system time until which the fly flees
    private long dirChangeCd = 0;   // ticks until next erratic direction change
    private double swarmAngle = 0;  // current orbit angle for swarm movement
    // Spider leap state
    private long leapCd = 0;        // ticks until the next leap is allowed
    private boolean leaping = false;
    private int leapTicks = 0;      // active leap duration
    // Maw state
    private long shootCd = 0;       // ticks until next projectile volley

    public Enemy(World w, Location loc, MobType type, int floor, int room, Player target, double hpMult) {
        this.type = type;
        this.maxHp = type.hpAt(floor) * hpMult;
        this.hp = maxHp;
        this.damage = type.damageAt(floor);
        this.speed = type.baseSpeed;
        this.knockback = Math.max(0.5, type.baseSpeed * 0.5);
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
        // Elite mobs glow with a yellow outline so players can spot them at a glance
        if (type.isElite()) {
            ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
        }
        scaleFor(type);
        // Maw starts with an initial shoot cooldown so players have a moment before the first volley
        if (type.ai == 6) {
            shootCd = 30;
            // Suppress the Warden's native AI (Darkness effect, sonic boom attacks, etc.)
            // so only the custom tickMaw AI runs.
            if (entity instanceof Warden warden) {
                warden.setAware(false);
            }
        }
    }

    private EntityType entityType(MobType t) {
        return switch (t.ai) {
            case 1 -> EntityType.ZOMBIE;       // Gaper
            case 2 -> EntityType.BEE;           // Fly
            case 3 -> EntityType.SPIDER;        // Spider
            case 4 -> EntityType.CREEPER;       // Mulliboom
            case 5 -> EntityType.RAVAGER;       // Charger
            case 6 -> EntityType.WARDEN;        // Maw
            default -> EntityType.ZOMBIE;
        };
    }

    private void scaleFor(MobType t) {
        double s = t.isElite() ? 1.4 : 1.0;
        try {
            if (entity instanceof org.bukkit.entity.Bee bee) {
                // Bees are naturally small — no setSize method, they're fine as-is
            } else if (entity instanceof org.bukkit.entity.Spider sp) {
                // Spiders are naturally the right size
            } else if (entity instanceof org.bukkit.entity.Creeper cr) {
                // Creepers are naturally the right size
            } else if (entity instanceof org.bukkit.entity.Ravager rv) {
                // Ravagers are naturally large
            } else if (entity instanceof org.bukkit.entity.Warden wd) {
                // Wardens are naturally large
            }
        } catch (Throwable ignored) {}
    }

    public void tick(Player p, long deltaMs) {
        if (dead || !entity.isValid() || p == null || !p.isOnline()) return;
        // Don't target spectators (dead players)
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (attackCd > 0) attackCd--;
        if (knockTicks > 0) {
            knockTicks--;
            if (knockTicks == 0) {
                if (entity instanceof org.bukkit.entity.LivingEntity le) le.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            }
            return;
        }
        if (movePause > 0) {
            movePause--;
            faceTarget(p);
            return;
        }

        // Decrement per-type cooldowns
        if (spitCd > 0) spitCd--;
        if (leapCd > 0) leapCd--;
        if (shootCd > 0) shootCd--;

        Location el = entity.getLocation().clone();
        Location pl = p.getLocation().clone();
        el.setY(pl.getY());
        double dx = pl.getX() - el.getX(), dz = pl.getZ() - el.getZ();
        double dist = Math.hypot(dx, dz);

        switch (type.ai) {
            case 1 -> tickGaper(p, el, pl, dx, dz, dist, deltaMs);
            case 2 -> tickFly(p, el, pl, dx, dz, dist, deltaMs);
            case 3 -> tickSpider(p, el, pl, dx, dz, dist, deltaMs);
            case 4 -> tickMulliboom(p, el, pl, dx, dz, dist, deltaMs);
            case 5 -> tickCharger(p, el, pl, dx, dz, dist, deltaMs);
            case 6 -> tickMaw(p, el, pl, dx, dz, dist, deltaMs);
        }
    }

    // ---- GAPER (ai=1): shambles toward player, occasionally spits a short-range projectile ----
    private void tickGaper(Player p, Location el, Location pl, double dx, double dz, double dist, long deltaMs) {
        // Shamble slowly toward the player
        if (dist > 1.5 && dist > 0.001) {
            moveToward(dx, dz, dist, speed, deltaMs);
        }
        faceTarget(p);

        // Spit attack: stop and fire a projectile at medium range
        if (spitCd <= 0 && dist > 1.8 && dist < 6.0 && attackCd <= 0) {
            spitCd = 60;  // ~3s between spits
            attackCd = 25; // pause after spitting
            movePause = 15;
            faceTarget(p);
            // Launch a snowball projectile as the spit visual
            org.bukkit.entity.Snowball spit = entity.getWorld().spawn(
                    entity.getLocation().clone().add(0, 1.2, 0), org.bukkit.entity.Snowball.class);
            spit.setShooter((org.bukkit.projectiles.ProjectileSource) entity);
            spit.setVelocity(pl.clone().add(0, 1, 0).subtract(entity.getLocation().clone().add(0, 1.2, 0))
                    .toVector().normalize().multiply(0.8));
            // Play spit sound
            entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.ENTITY_LLAMA_SPIT, 0.6f, 1.2f);
            return;
        }

        // Melee attack when close
        if (attackCd <= 0 && dist < 1.8 && p.isValid()) {
            attackPlayer(p);
            attackCd = 20;  // ~1s between melee hits
            movePause = 8;
        }
    }

    // ---- FLY (ai=2): swarms erratically around the player, flees at low HP ----
    private void tickFly(Player p, Location el, Location pl, double dx, double dz, double dist, long deltaMs) {
        // Flee when low HP
        boolean lowHp = hp < maxHp * 0.3;
        if (lowHp && fleeUntil == 0) {
            fleeUntil = System.currentTimeMillis() + 3000; // flee for 3 seconds
        }
        if (fleeUntil > 0 && System.currentTimeMillis() > fleeUntil) {
            fleeUntil = 0;
        }

        if (fleeUntil > 0) {
            // Flee away from the player
            if (dist > 0.001 && dist < 10) {
                double fx = -dx / dist, fz = -dz / dist;
                Location fleeLoc = el.clone().add(fx * speed * 0.15, 0, fz * speed * 0.15);
                if (isWalkable(fleeLoc)) entity.teleport(fleeLoc);
            }
            faceTarget(p);
            return;
        }

        // Erratic swarm movement: orbit around the player at a distance
        if (dirChangeCd <= 0) {
            swarmAngle = Math.random() * Math.PI * 2;
            dirChangeCd = 10 + (int) (Math.random() * 15); // change direction every 0.5-1.25s
        } else {
            dirChangeCd--;
        }

        double orbitDist = 2.5 + Math.random() * 1.5; // orbit radius
        double targetX = pl.getX() + Math.cos(swarmAngle) * orbitDist;
        double targetZ = pl.getZ() + Math.sin(swarmAngle) * orbitDist;
        double tdx = targetX - el.getX(), tdz = targetZ - el.getZ();
        double tDist = Math.hypot(tdx, tdz);
        if (tDist > 0.3 && tDist > 0.001) {
            moveToward(tdx, tdz, tDist, speed * 0.8, deltaMs);
        }
        faceTarget(p);

        // Dive-bomb attack: quickly close distance and hit
        if (attackCd <= 0 && dist < 2.0 && p.isValid()) {
            attackPlayer(p);
            attackCd = 15;
            movePause = 5;
        } else if (attackCd <= 0 && dist > 3.0 && Math.random() < 0.02) {
            // Occasionally swoop toward the player
            moveToward(dx, dz, dist, speed * 1.5, deltaMs);
        }
    }

    // ---- SPIDER (ai=3): climbs walls/ceiling, leaps at the player from range ----
    private void tickSpider(Player p, Location el, Location pl, double dx, double dz, double dist, long deltaMs) {
        // Spiders move faster when not in melee range
        double moveSpeed = dist > 3.0 ? speed * 1.2 : speed * 0.7;

        // Leap attack from range
        if (leapCd <= 0 && dist > 2.0 && dist < 7.0 && !leaping) {
            leaping = true;
            leapTicks = 8;
            leapCd = 50; // ~2.5s between leaps
            faceTarget(p);
            return;
        }

        if (leaping) {
            if (--leapTicks <= 0) {
                leaping = false;
                // Launch toward the player
                if (dist > 0.001) {
                    double nx = dx / dist, nz = dz / dist;
                    double leapDist = Math.min(dist, 3.0);
                    Location leapTarget = el.clone().add(nx * leapDist, 0.5, nz * leapDist);
                    if (isWalkable(leapTarget)) {
                        entity.teleport(leapTarget);
                        // AoE check: damage player if close enough after landing
                        Location after = entity.getLocation();
                        after.setY(pl.getY());
                        if (after.distance(pl) < 2.5 && p.isValid()) {
                            attackPlayer(p);
                        }
                        entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.ENTITY_SPIDER_AMBIENT, 0.8f, 0.7f);
                    }
                }
            }
            faceTarget(p);
            return;
        }

        // Normal movement toward player
        if (dist > 1.5 && dist > 0.001) {
            moveToward(dx, dz, dist, moveSpeed, deltaMs);
        }
        faceTarget(p);

        // Melee attack
        if (attackCd <= 0 && dist < 1.8 && p.isValid()) {
            attackPlayer(p);
            attackCd = 15;
            movePause = 6;
        }
    }

    // ---- MULLIBOOM (ai=4): walks slowly toward player, explodes on death ----
    private void tickMulliboom(Player p, Location el, Location pl, double dx, double dz, double dist, long deltaMs) {
        // Slow walk toward player
        if (dist > 1.5 && dist > 0.001) {
            moveToward(dx, dz, dist, speed, deltaMs);
        }
        faceTarget(p);

        // Short-range burst attack
        if (attackCd <= 0 && dist < 2.2 && p.isValid()) {
            attackPlayer(p);
            attackCd = 30; // slow attack rate
            movePause = 12;
        }
    }

    // ---- CHARGER (ai=5): telegraphed dash — brief pause, then fast lunge ----
    private void tickCharger(Player p, Location el, Location pl, double dx, double dz, double dist, long deltaMs) {
        if (dashing) {
            if (--dashTicks <= 0) dashing = false;
            el.setY(pl.getY());
            if (dist > 0.001) {
                double nx = dx / dist, nz = dz / dist;
                double lunge = Math.min(speed * 2.5 * (deltaMs / 1000.0), 0.8);
                Location next = el.clone().add(nx * lunge, 0, nz * lunge);
                if (isWalkable(next)) entity.teleport(next);
            }
            faceTarget(p);
            return;
        }
        if (dashWindup > 0) {
            dashWindup--;
            faceTarget(p);
            return;
        }
        if (dashCd > 0) dashCd--;
        el.setY(pl.getY());
        if (dashCd <= 0 && dist > 1.8) {
            dashWindup = 15;  // ~0.75s warning
            dashCd = 45;      // ~2.25s between dashes
            return;
        }
        // Walk toward player while on cooldown
        if (dist > 1.5 && dist > 0.001) {
            moveToward(dx, dz, dist, speed * 0.5, deltaMs);
        }
        faceTarget(p);
        // Melee when close
        if (attackCd <= 0 && dist < 1.8 && p.isValid()) {
            attackPlayer(p);
            attackCd = 12;
            movePause = 8;
        }
    }

    // ---- MAW (ai=6): stationary ranged enemy that fires projectiles ----
    private void tickMaw(Player p, Location el, Location pl, double dx, double dz, double dist, long deltaMs) {
        // Maw does not move — it stays in place and fires projectiles
        faceTarget(p);

        // Fire a volley of projectiles at the player
        if (shootCd <= 0 && dist < 12.0) {
            shootCd = 35; // ~1.75s between volleys
            attackCd = 10;
            movePause = 5;

            // Launch a sonic boom-style projectile (use a snowball as the visual projectile)
            org.bukkit.entity.Snowball projectile = entity.getWorld().spawn(
                    entity.getLocation().clone().add(0, 1.5, 0), org.bukkit.entity.Snowball.class);
            projectile.setShooter((org.bukkit.projectiles.ProjectileSource) entity);
            projectile.setVelocity(pl.clone().add(0, 1, 0).subtract(entity.getLocation().clone().add(0, 1.5, 0))
                    .toVector().normalize().multiply(1.2));
            // Maw attack sound
            entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.0f);
            // Visual telegraph: a burst of particles from the Maw's mouth
            entity.getWorld().spawnParticle(org.bukkit.Particle.SONIC_BOOM,
                    entity.getLocation().clone().add(0, 1.5, 0), 1, 0, 0, 0, 0);
        }
    }

    // ---- Shared helpers ----

    /** Move the entity toward a target by a step proportional to speed. */
    private void moveToward(double dx, double dz, double dist, double spd, long deltaMs) {
        double step = spd * (deltaMs / 1000.0);
        double nx = dx / dist, nz = dz / dist;
        double mx = nx * step, mz = nz * step;
        double cap = Math.min(0.4, step);
        double sx = Math.signum(mx) * Math.min(Math.abs(mx), cap);
        double sz = Math.signum(mz) * Math.min(Math.abs(mz), cap);
        Location next = entity.getLocation().clone().add(sx, 0, sz);
        if (isWalkable(next)) entity.teleport(next);
    }

    private boolean isWalkable(Location l) {
        Material m = l.getWorld().getBlockAt(l).getType();
        Material up = l.getWorld().getBlockAt(l.clone().add(0, 1, 0)).getType();
        return m != Material.STONE_BRICKS && up != Material.STONE_BRICKS
                && m != Material.DEEPSLATE_BRICKS && up != Material.DEEPSLATE_BRICKS
                && m != Material.BEDROCK;
    }

    private void faceTarget(Player p) {
        try {
            Location dir = p.getLocation().subtract(entity.getLocation());
            ((org.bukkit.entity.LivingEntity) entity).setRotation(
                    (float) (Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()))), 0);
        } catch (Throwable ignored) {}
    }

    private void attackPlayer(Player p) {
        if (com.lieyabull.dung.game.GameManager.playerHurt(p, damage)) {
            // brief pause so contact doesn't insta-stack
        }
    }

    public void damage(double dmg, Player source, double knockDirX, double knockDirZ) {
        hp -= dmg;
        if (knockDirX != 0 || knockDirZ != 0) {
            entity.setVelocity(entity.getVelocity().add(new org.bukkit.util.Vector(
                    knockDirX * knockback * 0.2, 0.25, knockDirZ * knockback * 0.2)));
            knockTicks = 8;
        }
        if (hp <= 0) {
            dead = true;
            // Mulliboom explodes on death
            if (type.ai == 4) {
                mulliboomExplode(source);
            }
            playDeathAnimation();
            entity.remove();
        } else {
            entity.setCustomName(type.name + " §c" + Math.max(0, (int) hp) + "/" + (int) maxHp);
            org.bukkit.World w = entity.getWorld();
            w.playSound(entity.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.6f, 1.2f);
            w.spawnParticle(org.bukkit.Particle.CRIT,
                    entity.getLocation().clone().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
        }
    }

    /** Mulliboom death explosion: AoE damage to nearby players. */
    private void mulliboomExplode(Player killer) {
        World w = entity.getWorld();
        Location loc = entity.getLocation();
        // Visual explosion
        w.createExplosion(loc, 0f, false, false); // 0 power = visual only, no block damage
        w.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, loc.clone().add(0, 1, 0), 1);
        w.spawnParticle(org.bukkit.Particle.FLAME, loc.clone().add(0, 1, 0), 20, 1.5, 0.5, 1.5, 0.05);
        w.playSound(loc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        // Damage all players within 3 blocks, bypassing invulnerability frames
        double explosionDamage = damage * 9.0;
        for (org.bukkit.entity.Player p : w.getNearbyPlayers(loc, 3.0)) {
            if (p.isValid() && !p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                com.lieyabull.dung.game.GameManager.playerHurtBypassInvuln(p, explosionDamage);
            }
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
            case BEE -> org.bukkit.Sound.ENTITY_BEE_DEATH;
            case SPIDER -> org.bukkit.Sound.ENTITY_SPIDER_DEATH;
            case CREEPER -> org.bukkit.Sound.ENTITY_CREEPER_DEATH;
            case RAVAGER -> org.bukkit.Sound.ENTITY_RAVAGER_DEATH;
            case WARDEN -> org.bukkit.Sound.ENTITY_WARDEN_DEATH;
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