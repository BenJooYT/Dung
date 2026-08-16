package com.lieyabull.dung.party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for {@link PartyManager} using dynamic proxies for Player objects.
 * Only methods actually called by PartyManager/Party are implemented:
 * - getUniqueId() -> UUID
 * - getName() -> String
 * - sendMessage(String) -> void
 */
public class PartyManagerTest {

    private PartyManager manager;
    private PlayerProxy leader;
    private PlayerProxy member1;
    private PlayerProxy member2;
    private PlayerProxy member3;
    private PlayerProxy outsider;

    /** Creates a dynamic proxy for org.bukkit.entity.Player with the given name. */
    private static PlayerProxy createPlayer(String name) {
        return new PlayerProxy(name);
    }

    /** Wrapper around a dynamic Player proxy that provides access to UUID and name. */
    private static class PlayerProxy {
        final UUID uuid;
        final String name;
        final org.bukkit.entity.Player player;

        PlayerProxy(String name) {
            this.uuid = UUID.randomUUID();
            this.name = name;
            this.player = (org.bukkit.entity.Player) Proxy.newProxyInstance(
                PlayerProxy.class.getClassLoader(),
                new Class<?>[]{
                    org.bukkit.entity.Player.class,
                    org.bukkit.entity.HumanEntity.class,
                    org.bukkit.entity.LivingEntity.class,
                    org.bukkit.entity.Entity.class,
                    org.bukkit.command.CommandSender.class,
                    org.bukkit.OfflinePlayer.class,
                    org.bukkit.permissions.Permissible.class,
                    org.bukkit.metadata.Metadatable.class,
                    org.bukkit.plugin.messaging.PluginMessageRecipient.class,
                    org.bukkit.persistence.PersistentDataHolder.class,
                    org.bukkit.Nameable.class,
                    org.bukkit.Keyed.class,
                    net.kyori.adventure.identity.Identified.class,
                    net.kyori.adventure.bossbar.BossBarViewer.class,
                    com.destroystokyo.paper.network.NetworkClient.class
                },
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getUniqueId".equals(methodName)) return uuid;
                    if ("getName".equals(methodName)) return name;
                    if ("sendMessage".equals(methodName)) return null; // no-op
                    if ("getPlayer".equals(methodName)) return proxy;
                    if ("getEntity".equals(methodName)) return proxy;
                    if ("getLocation".equals(methodName)) return new org.bukkit.Location(null, 0, 0, 0);
                    if ("getWorld".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getInventory".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getScoreboard".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getPlayerProfile".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getPersistentDataContainer".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getOpenInventory".equals(methodName)) return null;
                    if ("getInventoryView".equals(methodName)) return null;
                    if ("getAddress".equals(methodName)) return null;
                    if ("getVirtualHost".equals(methodName)) return null;
                    if ("getDisplayName".equals(methodName)) return name;
                    if ("getPlayerListName".equals(methodName)) return name;
                    if ("getCustomName".equals(methodName)) return name;
                    if ("getLocale".equals(methodName)) return "en_US";
                    if ("getPing".equals(methodName)) return 0;
                    if ("getProtocolVersion".equals(methodName)) return 0;
                    if ("getClientViewDistance".equals(methodName)) return 0;
                    if ("getViewDistance".equals(methodName)) return 0;
                    if ("getSimulationDistance".equals(methodName)) return 0;
                    if ("getSendViewDistance".equals(methodName)) return 0;
                    if ("getGameMode".equals(methodName)) return org.bukkit.GameMode.SURVIVAL;
                    if ("getHealth".equals(methodName)) return 20.0;
                    if ("getMaxHealth".equals(methodName)) return 20.0;
                    if ("getFoodLevel".equals(methodName)) return 20;
                    if ("getLevel".equals(methodName)) return 0;
                    if ("getExp".equals(methodName)) return 0f;
                    if ("getTotalExperience".equals(methodName)) return 0;
                    if ("getFlySpeed".equals(methodName)) return 0.1f;
                    if ("getWalkSpeed".equals(methodName)) return 0.2f;
                    if ("getEntityId".equals(methodName)) return 0;
                    if ("getEntityUniqueId".equals(methodName)) return uuid;
                    if ("getType".equals(methodName)) return org.bukkit.entity.EntityType.PLAYER;
                    if ("getHeight".equals(methodName)) return 1.8;
                    if ("getWidth".equals(methodName)) return 0.6;
                    if ("getBoundingBox".equals(methodName)) return new org.bukkit.util.BoundingBox(0, 0, 0, 0, 0, 0);
                    if ("getVelocity".equals(methodName)) return new org.bukkit.util.Vector(0, 0, 0);
                    if ("getFallDistance".equals(methodName)) return 0f;
                    if ("getTicksLived".equals(methodName)) return 0;
                    if ("getMaxFireTicks".equals(methodName)) return 20;
                    if ("getFireTicks".equals(methodName)) return 0;
                    if ("getNoDamageTicks".equals(methodName)) return 0;
                    if ("getMaximumNoDamageTicks".equals(methodName)) return 20;
                    if ("getRemainingAir".equals(methodName)) return 300;
                    if ("getMaximumAir".equals(methodName)) return 300;
                    if ("getArrowCooldown".equals(methodName)) return 0;
                    if ("getArrowsInBody".equals(methodName)) return 0;
                    if ("getLastDamage".equals(methodName)) return 0.0;
                    if ("getDamage".equals(methodName)) return 0.0;
                    if ("getAbsorptionAmount".equals(methodName)) return 0.0;
                    if ("getSleepTicks".equals(methodName)) return 0;
                    if ("getPlayerTime".equals(methodName)) return 0L;
                    if ("getPlayerTimeOffset".equals(methodName)) return 0L;
                    if ("getExhaustion".equals(methodName)) return 0f;
                    if ("getSaturation".equals(methodName)) return 5f;
                    if ("getSaturatedRegenRate".equals(methodName)) return 0;
                    if ("getUnsaturatedRegenRate".equals(methodName)) return 0;
                    if ("getStarvationRate".equals(methodName)) return 0;
                    if ("getCooldownPeriod".equals(methodName)) return 0f;
                    if ("getCooledAttackStrength".equals(methodName)) return 0f;
                    if ("getAttackCooldown".equals(methodName)) return 0f;
                    if ("getAttackCooldownProgressPerTick".equals(methodName)) return 0f;
                    if ("getAttackCooldownProgress".equals(methodName)) return 0f;
                    if ("getExpToLevel".equals(methodName)) return 0;
                    if ("getEnchantmentSeed".equals(methodName)) return 0;
                    if ("getFirstPlayed".equals(methodName)) return 0L;
                    if ("getLastPlayed".equals(methodName)) return 0L;
                    if ("getLastLogin".equals(methodName)) return 0L;
                    if ("getLastSeen".equals(methodName)) return 0L;
                    if ("getPlayerListHeader".equals(methodName)) return null;
                    if ("getPlayerListFooter".equals(methodName)) return null;
                    if ("getPlayerListOrder".equals(methodName)) return 0;
                    if ("getCompassTarget".equals(methodName)) return null;
                    if ("getBedSpawnLocation".equals(methodName)) return null;
                    if ("getLastDeathLocation".equals(methodName)) return null;
                    if ("getSpectatorTarget".equals(methodName)) return null;
                    if ("getResourcePackHash".equals(methodName)) return null;
                    if ("getResourcePackStatus".equals(methodName)) return org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
                    if ("getClientBrandName".equals(methodName)) return "vanilla";
                    if ("getClientOption".equals(methodName)) return null;
                    if ("getLastDamageCause".equals(methodName)) return null;
                    if ("getLeashHolder".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getVehicle".equals(methodName)) return null;
                    if ("getPassenger".equals(methodName)) return null;
                    if ("getPassengers".equals(methodName)) return java.util.List.of();
                    if ("getNearbyEntities".equals(methodName)) return java.util.List.of();
                    if ("getLineOfSight".equals(methodName)) return java.util.List.of();
                    if ("getTargetBlock".equals(methodName)) return null;
                    if ("getTargetBlockExact".equals(methodName)) return null;
                    if ("getOrigin".equals(methodName)) return null;
                    if ("getOriginEntity".equals(methodName)) return null;
                    if ("getRootVehicle".equals(methodName)) return proxy;
                    if ("getMergedEntity".equals(methodName)) return proxy;
                    if ("getMergedEntities".equals(methodName)) return java.util.Set.of();
                    if ("getTrackedBy".equals(methodName)) return java.util.Set.of();
                    if ("getTrackedPlayers".equals(methodName)) return java.util.Set.of();
                    if ("getScoreboardTags".equals(methodName)) return java.util.Set.of();
                    if ("getMetadataKeys".equals(methodName)) return java.util.Set.of();
                    if ("getMetadata".equals(methodName)) return java.util.List.of();
                    if ("getListeningPluginChannels".equals(methodName)) return java.util.Set.of();
                    if ("getEffectivePermissions".equals(methodName)) return java.util.Set.of();
                    if ("getAttachments".equals(methodName)) return java.util.Map.of();
                    if ("getCollidableExemptions".equals(methodName)) return java.util.Set.of();
                    if ("getDiscoveredRecipes".equals(methodName)) return java.util.Set.of();
                    if ("getCookies".equals(methodName)) return java.util.Set.of();
                    if ("getSentChunkKeys".equals(methodName)) return java.util.Set.of();
                    if ("getSentChunks".equals(methodName)) return java.util.Set.of();
                    if ("getActivePotionEffects".equals(methodName)) return java.util.List.of();
                    if ("getEnderPearls".equals(methodName)) return java.util.List.of();
                    if ("getCurrentInput".equals(methodName)) return null;
                    if ("getClientViewDistance".equals(methodName)) return 0;
                    if ("getDeathScreenScore".equals(methodName)) return 0;
                    if ("getWardenWarningCooldown".equals(methodName)) return 0;
                    if ("getWardenTimeSinceLastWarning".equals(methodName)) return 0;
                    if ("getWardenWarningLevel".equals(methodName)) return 0;
                    if ("getIdleDuration".equals(methodName)) return java.time.Duration.ZERO;
                    if ("getHAProxyAddress".equals(methodName)) return null;
                    if ("getConnection".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getStatistic".equals(methodName)) return 0;
                    if ("getAdvancementProgress".equals(methodName)) return null;
                    if ("getAttribute".equals(methodName)) return null;
                    if ("getPotionEffect".equals(methodName)) return null;
                    if ("getEquipment".equals(methodName)) return null;
                    if ("getItemInHand".equals(methodName)) return null;
                    if ("getItemOnCursor".equals(methodName)) return null;
                    if ("getItemInUse".equals(methodName)) return null;
                    if ("getItemInUseTicks".equals(methodName)) return 0;
                    if ("getOpenInventory".equals(methodName)) return null;
                    if ("getInventoryView".equals(methodName)) return null;
                    if ("getPlayerWeather".equals(methodName)) return null;
                    if ("getResourcePackHash".equals(methodName)) return null;
                    if ("getMotive".equals(methodName)) return null;
                    if ("getOriginalMotive".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getSwimSound".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getSwimSplashSound".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getSwimHighSpeedSplashSound".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getCategory".equals(methodName)) return org.bukkit.entity.EntityCategory.NONE;
                    if ("getSpawnCategory".equals(methodName)) return org.bukkit.entity.SpawnCategory.MISC;
                    if ("getAsString".equals(methodName)) return name;
                    if ("getPlayerListName".equals(methodName)) return name;
                    if ("getDisplayName".equals(methodName)) return name;
                    if ("getCustomName".equals(methodName)) return name;
                    if ("name".equals(methodName)) return net.kyori.adventure.text.Component.text(name);
                    if ("displayName".equals(methodName)) return net.kyori.adventure.text.Component.text(name);
                    if ("playerListName".equals(methodName)) return net.kyori.adventure.text.Component.text(name);
                    if ("playerListHeader".equals(methodName)) return net.kyori.adventure.text.Component.text("");
                    if ("playerListFooter".equals(methodName)) return net.kyori.adventure.text.Component.text("");
                    if ("customName".equals(methodName)) return net.kyori.adventure.text.Component.text(name);
                    if ("identity".equals(methodName)) return net.kyori.adventure.identity.Identity.identity(uuid);
                    if ("activeBossBars".equals(methodName)) return java.util.List.of();
                    if ("isOnline".equals(methodName)) return true;
                    if ("isBanned".equals(methodName)) return false;
                    if ("isWhitelisted".equals(methodName)) return true;
                    if ("isOp".equals(methodName)) return false;
                    if ("isPermissionSet".equals(methodName)) return true;
                    if ("hasPermission".equals(methodName)) return true;
                    if ("isSneaking".equals(methodName)) return false;
                    if ("isSprinting".equals(methodName)) return false;
                    if ("isSwimming".equals(methodName)) return false;
                    if ("isGliding".equals(methodName)) return false;
                    if ("isGlidingWithElytra".equals(methodName)) return false;
                    if ("isClimbing".equals(methodName)) return false;
                    if ("isFlying".equals(methodName)) return false;
                    if ("isSleeping".equals(methodName)) return false;
                    if ("isDeeplySleeping".equals(methodName)) return false;
                    if ("isBlocking".equals(methodName)) return false;
                    if ("isHandRaised".equals(methodName)) return false;
                    if ("isDead".equals(methodName)) return false;
                    if ("isValid".equals(methodName)) return true;
                    if ("isEmpty".equals(methodName)) return false;
                    if ("isPersistent".equals(methodName)) return true;
                    if ("isPersist".equals(methodName)) return true;
                    if ("isInvulnerable".equals(methodName)) return false;
                    if ("isCollidable".equals(methodName)) return true;
                    if ("isCustomNameVisible".equals(methodName)) return false;
                    if ("isVisible".equals(methodName)) return true;
                    if ("isVisibleByDefault".equals(methodName)) return true;
                    if ("isGlowing".equals(methodName)) return false;
                    if ("isTicking".equals(methodName)) return true;
                    if ("isFreezeTicking".equals(methodName)) return false;
                    if ("isFrozen".equals(methodName)) return false;
                    if ("isInWater".equals(methodName)) return false;
                    if ("isInWaterOrBubbleColumn".equals(methodName)) return false;
                    if ("isInWaterOrRainOrBubbleColumn".equals(methodName)) return false;
                    if ("isInRain".equals(methodName)) return false;
                    if ("isInBubbleColumn".equals(methodName)) return false;
                    if ("isInWaterOrRain".equals(methodName)) return false;
                    if ("isInLava".equals(methodName)) return false;
                    if ("isUnderWater".equals(methodName)) return false;
                    if ("isInCloud".equals(methodName)) return false;
                    if ("isInsideVehicle".equals(methodName)) return false;
                    if ("isOnGround".equals(methodName)) return true;
                    if ("isInWorld".equals(methodName)) return true;
                    if ("isInWorldSpawnBorder".equals(methodName)) return true;
                    if ("isInWorldOrSpawnBorder".equals(methodName)) return true;
                    if ("isVisualFire".equals(methodName)) return false;
                    if ("isVisualFlames".equals(methodName)) return false;
                    if ("isInvisible".equals(methodName)) return false;
                    if ("isInvisibleTo".equals(methodName)) return false;
                    if ("isConversing".equals(methodName)) return false;
                    if ("isNewPlayer".equals(methodName)) return false;
                    if ("isHealthScaled".equals(methodName)) return false;
                    if ("isSleepingIgnored".equals(methodName)) return false;
                    if ("isPlayerTimeRelative".equals(methodName)) return true;
                    if ("isAllowingServerListings".equals(methodName)) return true;
                    if ("isTransferred".equals(methodName)) return false;
                    if ("isChunkSent".equals(methodName)) return false;
                    if ("isMerged".equals(methodName)) return false;
                    if ("isMerging".equals(methodName)) return false;
                    if ("isTickingEntities".equals(methodName)) return false;
                    if ("hasPlayedBefore".equals(methodName)) return false;
                    if ("hasAchievement".equals(methodName)) return false;
                    if ("hasDiscoveredRecipe".equals(methodName)) return false;
                    if ("hasMetadata".equals(methodName)) return false;
                    if ("hasLineOfSight".equals(methodName)) return true;
                    if ("hasPotionEffect".equals(methodName)) return false;
                    if ("hasPassenger".equals(methodName)) return false;
                    if ("hasResourcePack".equals(methodName)) return false;
                    if ("hasCookie".equals(methodName)) return false;
                    if ("canSee".equals(methodName)) return true;
                    if ("canMove".equals(methodName)) return true;
                    if ("canTick".equals(methodName)) return true;
                    if ("shouldSave".equals(methodName)) return true;
                    if ("shouldBeSaved".equals(methodName)) return proxy;
                    if ("fromMobSpawner".equals(methodName)) return false;
                    if ("leaveVehicle".equals(methodName)) return false;
                    if ("eject".equals(methodName)) return false;
                    if ("toString".equals(methodName)) return "PlayerStub{" + name + "}";
                    if ("hashCode".equals(methodName)) return uuid.hashCode();
                    if ("equals".equals(methodName)) return proxy == args[0];
                    if ("clone".equals(methodName)) throw new CloneNotSupportedException();
                    if ("serialize".equals(methodName)) return java.util.Map.of();
                    if ("spigot".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getPlayer".equals(methodName)) return proxy;
                    if ("getEntity".equals(methodName)) return proxy;
                    if ("copy".equals(methodName)) throw new UnsupportedOperationException();
                    if ("launchProjectile".equals(methodName)) throw new UnsupportedOperationException();
                    if ("addAttachment".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getPersistentDataContainer".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getInventory".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getScoreboard".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getPlayerProfile".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getWorld".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getChunk".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getLeashHolder".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getSwimSound".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getSwimSplashSound".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getSwimHighSpeedSplashSound".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getOriginalMotive".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getConnection".equals(methodName)) throw new UnsupportedOperationException();
                    if ("getClientOption".equals(methodName)) return null;
                    // For any other method, return a default value based on return type
                    Class<?> ret = method.getReturnType();
                    if (ret == void.class || ret == Void.class) return null;
                    if (ret == boolean.class) return false;
                    if (ret == int.class) return 0;
                    if (ret == long.class) return 0L;
                    if (ret == float.class) return 0f;
                    if (ret == double.class) return 0.0;
                    if (ret == byte.class) return (byte) 0;
                    if (ret == short.class) return (short) 0;
                    if (ret == char.class) return (char) 0;
                    if (ret == String.class) return "";
                    if (ret == UUID.class) return uuid;
                    if (ret == org.bukkit.Location.class) return new org.bukkit.Location(null, 0, 0, 0);
                    if (ret == org.bukkit.util.Vector.class) return new org.bukkit.util.Vector(0, 0, 0);
                    if (ret == org.bukkit.util.BoundingBox.class) return new org.bukkit.util.BoundingBox(0, 0, 0, 0, 0, 0);
                    if (ret == org.bukkit.entity.Entity.class) return proxy;
                    if (ret == org.bukkit.entity.Player.class) return proxy;
                    if (java.util.Collection.class.isAssignableFrom(ret)) return java.util.List.of();
                    if (java.util.Set.class.isAssignableFrom(ret)) return java.util.Set.of();
                    if (java.util.Map.class.isAssignableFrom(ret)) return java.util.Map.of();
                    if (java.util.Optional.class.isAssignableFrom(ret)) return java.util.Optional.empty();
                    if (java.util.concurrent.CompletableFuture.class.isAssignableFrom(ret)) return java.util.concurrent.CompletableFuture.completedFuture(null);
                    if (net.kyori.adventure.text.Component.class.isAssignableFrom(ret)) return net.kyori.adventure.text.Component.text("");
                    if (net.kyori.adventure.identity.Identity.class.isAssignableFrom(ret)) return net.kyori.adventure.identity.Identity.identity(uuid);
                    if (ret.isEnum()) return ret.getEnumConstants()[0];
                    return null;
                }
            );
        }
    }

    @BeforeEach
    void setUp() {
        manager = new PartyManager();
        leader = createPlayer("Leader");
        member1 = createPlayer("Member1");
        member2 = createPlayer("Member2");
        member3 = createPlayer("Member3");
        outsider = createPlayer("Outsider");
    }

    // ---- Creating a party ----

    @Test
    void createPartyReturnsNewParty() {
        Party p = manager.createParty(leader.player);
        assertNotNull(p);
        assertEquals(leader.uuid, p.leader());
        assertEquals(1, p.size());
        assertTrue(p.isMember(leader.uuid));
    }

    @Test
    void createPartyReturnsNullIfAlreadyInParty() {
        manager.createParty(leader.player);
        assertNull(manager.createParty(leader.player));
    }

    @Test
    void partyOfReturnsPartyForLeader() {
        Party p = manager.createParty(leader.player);
        assertSame(p, manager.partyOf(leader.player));
    }

    // ---- Inviting a player ----

    @Test
    void inviteSucceeds() {
        manager.createParty(leader.player);
        assertTrue(manager.invite(leader.player, member1.player));
    }

    @Test
    void inviteCreatesPartyIfInviterNotInParty() {
        assertTrue(manager.invite(leader.player, member1.player));
        assertNotNull(manager.partyOf(leader.player));
        assertTrue(manager.partyOf(leader.player).isLeader(leader.player.getUniqueId()));
    }

    @Test
    void inviteFailsIfInviterNotLeader() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.acceptInvite(member1.player);
        assertFalse(manager.invite(member1.player, member2.player));
    }

    @Test
    void inviteFailsIfInviteeAlreadyInParty() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.acceptInvite(member1.player);
        assertFalse(manager.invite(leader.player, member1.player));
    }

    @Test
    void inviteFailsIfPartyIsFull() {
        manager.createParty(leader.player);
        PlayerProxy m2 = createPlayer("M2");
        PlayerProxy m3 = createPlayer("M3");
        PlayerProxy m4 = createPlayer("M4");
        manager.invite(leader.player, member1.player); manager.acceptInvite(member1.player);
        manager.invite(leader.player, m2.player); manager.acceptInvite(m2.player);
        manager.invite(leader.player, m3.player); manager.acceptInvite(m3.player);
        // Party has 4 members (leader + 3), should be full
        assertFalse(manager.invite(leader.player, m4.player));
        assertFalse(manager.acceptInvite(m4.player));
    }

    // ---- Accepting an invitation ----

    @Test
    void acceptInviteAddsPlayerToParty() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        assertTrue(manager.acceptInvite(member1.player));
        assertSame(manager.partyOf(leader.player), manager.partyOf(member1.player));
        assertEquals(2, manager.partyOf(leader.player).size());
    }

    @Test
    void acceptInviteFailsIfNoInvite() {
        assertFalse(manager.acceptInvite(member1.player));
    }

    @Test
    void acceptInviteFailsIfPartyDisbandedBeforeAccept() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.disband(leader.player);
        assertFalse(manager.acceptInvite(member1.player));
    }

    // ---- Declining an invitation ----

    @Test
    void declineInviteRemovesPendingInvite() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        assertTrue(manager.hasPendingInvite(member1.player));
        manager.declineInvite(member1.player);
        assertFalse(manager.hasPendingInvite(member1.player));
    }

    @Test
    void declineInviteThenAcceptFails() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.declineInvite(member1.player);
        assertFalse(manager.acceptInvite(member1.player));
    }

    // ---- Leaving a party ----

    @Test
    void leavePartyRemovesMember() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.acceptInvite(member1.player);
        manager.leaveParty(member1.player);
        assertNull(manager.partyOf(member1.player));
        assertNotNull(manager.partyOf(leader.player));
        assertEquals(1, manager.partyOf(leader.player).size());
    }

    @Test
    void leavePartyWhenNotInPartyDoesNothing() {
        manager.leaveParty(outsider.player);
    }

    @Test
    void leavePartyDisbandsWhenEmpty() {
        manager.createParty(leader.player);
        manager.leaveParty(leader.player);
        assertNull(manager.partyOf(leader.player));
    }

    // ---- Kicking a player ----

    @Test
    void kickRemovesMember() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.acceptInvite(member1.player);
        assertTrue(manager.kick(leader.player, member1.player));
        assertNull(manager.partyOf(member1.player));
        assertEquals(1, manager.partyOf(leader.player).size());
    }

    @Test
    void kickFailsIfKickerNotLeader() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.acceptInvite(member1.player);
        assertFalse(manager.kick(member1.player, leader.player));
    }

    @Test
    void kickFailsIfTargetNotInParty() {
        manager.createParty(leader.player);
        assertFalse(manager.kick(leader.player, outsider.player));
    }

    @Test
    void kickFailsIfKickerNotInParty() {
        assertFalse(manager.kick(outsider.player, leader.player));
    }

    // ---- Disbanding a party ----

    @Test
    void disbandRemovesAllMembers() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.acceptInvite(member1.player);
        manager.invite(leader.player, member2.player);
        manager.acceptInvite(member2.player);
        assertTrue(manager.disband(leader.player));
        assertNull(manager.partyOf(leader.player));
        assertNull(manager.partyOf(member1.player));
        assertNull(manager.partyOf(member2.player));
    }

    @Test
    void disbandFailsIfNotLeader() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.acceptInvite(member1.player);
        assertFalse(manager.disband(member1.player));
    }

    @Test
    void disbandFailsIfNotInParty() {
        assertFalse(manager.disband(outsider.player));
    }

    // ---- Party max size ----

    @Test
    void partyMaxSizeIsFour() {
        assertEquals(4, Party.MAX_SIZE);
    }

    @Test
    void cannotExceedMaxPartySize() {
        manager.createParty(leader.player);
        PlayerProxy m2 = createPlayer("M2");
        PlayerProxy m3 = createPlayer("M3");
        PlayerProxy m4 = createPlayer("M4");
        manager.invite(leader.player, member1.player); manager.acceptInvite(member1.player);
        manager.invite(leader.player, m2.player); manager.acceptInvite(m2.player);
        manager.invite(leader.player, m3.player); manager.acceptInvite(m3.player);
        assertFalse(manager.invite(leader.player, m4.player));
        assertFalse(manager.acceptInvite(m4.player));
    }

    // ---- Leadership transfer when leader leaves ----

    @Test
    void leadershipTransfersWhenLeaderLeaves() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.acceptInvite(member1.player);
        manager.invite(leader.player, member2.player);
        manager.acceptInvite(member2.player);
        manager.leaveParty(leader.player);
        assertNull(manager.partyOf(leader.player));
        Party p = manager.partyOf(member1.player);
        assertNotNull(p);
        assertEquals(member1.uuid, p.leader());
        assertTrue(p.isMember(member2.uuid));
    }

    @Test
    void leadershipTransfersWhenLeaderLeavesWithMultipleMembers() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.acceptInvite(member1.player);
        manager.invite(leader.player, member2.player);
        manager.acceptInvite(member2.player);
        manager.leaveParty(leader.player);
        Party p = manager.partyOf(member1.player);
        assertEquals(member1.uuid, p.leader());
    }

    // ---- Cannot invite a player already in a party ----

    @Test
    void cannotInvitePlayerAlreadyInAParty() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.acceptInvite(member1.player);
        PlayerProxy otherLeader = createPlayer("OtherLeader");
        manager.createParty(otherLeader.player);
        assertFalse(manager.invite(otherLeader.player, member1.player));
    }

    @Test
    void hasPendingInviteReturnsCorrectly() {
        manager.createParty(leader.player);
        assertFalse(manager.hasPendingInvite(member1.player));
        manager.invite(leader.player, member1.player);
        assertTrue(manager.hasPendingInvite(member1.player));
        assertFalse(manager.hasPendingInvite(outsider.player));
    }

    @Test
    void getInviterReturnsCorrectInviter() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        assertEquals(leader.uuid, manager.getInviter(member1.player));
    }

    @Test
    void getInviterReturnsNullForNoInvite() {
        assertNull(manager.getInviter(outsider.player));
    }

    // ---- onPlayerQuit ----

    @Test
    void onPlayerQuitCleansUpInvites() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        assertTrue(manager.hasPendingInvite(member1.player));
        manager.onPlayerQuit(member1.player);
        assertFalse(manager.hasPendingInvite(member1.player));
    }

    @Test
    void onPlayerQuitRemovesFromParty() {
        manager.createParty(leader.player);
        manager.invite(leader.player, member1.player);
        manager.acceptInvite(member1.player);
        assertNotNull(manager.partyOf(member1.player));
        manager.onPlayerQuit(member1.player);
        assertNull(manager.partyOf(member1.player));
    }
}