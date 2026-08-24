package com.lieyabull.dung.dummy;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.lieyabull.dung.Dung;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

/**
 * Renders dummy avatars as TRUE client-side player models via ProtocolLib packets.
 *
 * <p>Each avatar gets a deterministic fake UUID (and matching fake entity id); viewers receive
 * a PLAYER_INFO (ADD_PLAYER) packet carrying the resolved profile's skin textures, a
 * NAMED_ENTITY_SPAWN at the dummy location, a head rotation, and a metadata packet enabling
 * all skin layers. Removal is the inverse (PLAYER_INFO REMOVE + ENTITY_DESTROY).</p>
 *
 * <p>The fake player is CLIENT-SIDE ONLY — there is no server entity behind it. Clicks are
 * still handled by the dummy's real {@link org.bukkit.entity.Interaction} hitbox at the same
 * spot. The armor stand base stays invisible so only the player model shows.</p>
 *
 * <p>All ProtocolLib interaction is isolated to this class. Every packet send is guarded so a
 * version mismatch degrades to "no fake player rendered" instead of crashing the plugin.</p>
 */
public final class FakePlayerRenderer implements Listener {

    /** 1.20.2+ data-watcher index of the skin-overlay byte. */
    private static final int SKIN_LAYERS_INDEX = resolveSkinLayersIndex();

    /**
     * Resolve the real skin-overlay data-watcher index from the server instead of hardcoding it —
     * the player metadata layout shifts between Minecraft versions and sending a byte at the wrong
     * index (where the client expects another type) disconnects them with a protocol error.
     * Reads {@code Player.DATA_PLAYER_MODE_CUSTOMISATION}'s accessor id via reflection (Paper runs
     * Mojang-mapped); falls back to the historical index 17 if reflection fails.
     */
    private static int resolveSkinLayersIndex() {
        try {
            Class<?> player = Class.forName("net.minecraft.world.entity.player.Player");
            Object accessor = player.getField("DATA_PLAYER_MODE_CUSTOMISATION").get(null);
            java.lang.reflect.Field id = accessor.getClass().getDeclaredField("id");
            id.setAccessible(true);
            return id.getInt(accessor);
        } catch (Throwable t) {
            return 17;
        }
    }
    private static final byte SKIN_ALL = (byte) 0x7E; // cape+jacket+sleeves+pants+hat

    private final Dung plugin;
    private final ProtocolManager protocol;
    /**
     * Latched once a packet send reveals an incompatible ProtocolLib (e.g. the known
     * WrappedGameProfile.GET_PROPERTIES null-accessor breakage on 1.21.x authlib).
     * The renderer stops trying and callers fall back to skinned-head stands.
     */
    private volatile boolean broken;
    /** Fake uuid per dummy key ("world:x,y,z"). */
    private final Map<String, UUID> fakeIds = new HashMap<>();
    /** Client-side entity id source — starts far above the server's real ids so a fake player
     *  can never collide with (and overwrite) an actual entity on the client. */
    private static final java.util.concurrent.atomic.AtomicInteger FAKE_ENTITY_IDS =
            new java.util.concurrent.atomic.AtomicInteger(1_500_000_000);
    /** Fake (client-side) entity id per fake uuid — must fit in a signed int array slot. */
    private final Map<UUID, Integer> entityIds = new HashMap<>();
    /** Viewers currently seeing each fake player: fake uuid -> viewer uuids. */
    private final Map<UUID, Set<UUID>> viewers = new HashMap<>();
    /** Cached textures per fake uuid for re-sends to late joiners. */
    private final Map<UUID, List<com.destroystokyo.paper.profile.ProfileProperty>> props = new HashMap<>();

    public FakePlayerRenderer(Dung plugin) {
        this.plugin = plugin;
        ProtocolManager pm;
        try {
            pm = ProtocolLibrary.getProtocolManager();
        } catch (Throwable t) {
            pm = null;
            plugin.getLogger().warning("ProtocolLib not found — dummy avatars fall back to skinned-head stands.");
        }
        this.protocol = pm;
    }

    public boolean available() {
        return protocol != null && !broken;
    }

    private static String keyOf(Dummy d) {
        return d.worldName + ":" + d.x + "," + d.y + "," + d.z;
    }

    /** Show the avatar's fake player to every online viewer in its world.
     *  @return true if at least one viewer received the full packet set. Never throws. */
    public boolean show(Dummy d, Location loc, String playerName,
                     com.destroystokyo.paper.profile.PlayerProfile prof) {
        if (!available() || prof == null || loc.getWorld() == null) return false;
        String key = keyOf(d);
        UUID fakeId = fakeIds.computeIfAbsent(key,
                k -> UUID.nameUUIDFromBytes(("dung-dummy:" + k).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        entityIds.computeIfAbsent(fakeId, k -> FAKE_ENTITY_IDS.getAndDecrement());

        var textures = prof.getProperties().stream()
                .filter(p -> p.getName().equalsIgnoreCase("textures")).findFirst().orElse(null);
        if (textures != null) {
            props.put(fakeId, new ArrayList<>(List.of(new com.destroystokyo.paper.profile.ProfileProperty(
                    "textures", textures.getValue(), textures.getSignature()))));
        }

        boolean anyShown = false;
        for (Player viewer : loc.getWorld().getPlayers()) {
            if (sendShow(viewer, fakeId, playerName, loc)) {
                viewers.computeIfAbsent(fakeId, k -> new HashSet<>()).add(viewer.getUniqueId());
                anyShown = true;
            }
        }
        if (anyShown) {
            // The profile was registered listed so the spawn would validate; flip it to UNLISTED
            // shortly after via UPDATE_LISTED — removes the blank tab row while keeping the skin
            // registration (and thus the rendered model) intact.
            UUID id = fakeId;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Set<UUID> seen = new HashSet<>(viewers.getOrDefault(id, Set.of()));
                if (seen.isEmpty()) seen.addAll(loc.getWorld().getPlayers().stream()
                        .map(Player::getUniqueId).toList());
                for (UUID viewerUuid : seen) {
                    Player v = Bukkit.getPlayer(viewerUuid);
                    if (v == null || !v.isOnline()) continue;
                    try {
                        PacketContainer unlist = protocol.createPacket(PacketType.Play.Server.PLAYER_INFO);
                        unlist.getPlayerInfoActions().write(0,
                                java.util.EnumSet.of(EnumWrappers.PlayerInfoAction.UPDATE_LISTED));
                        PlayerInfoData data = new PlayerInfoData(id, 0, false,
                                EnumWrappers.NativeGameMode.SURVIVAL,
                                new WrappedGameProfile(id, " "),
                                WrappedChatComponent.fromLegacyText(" "));
                        unlist.getPlayerInfoDataLists().write(0, List.of(data));
                        protocol.sendServerPacket(v, unlist);
                    } catch (Throwable ignored) {
                    }
                }
            }, 30L);
        }
        return anyShown;
    }

    private boolean sendShow(Player viewer, UUID fakeId, String name, Location loc) {
        int entityId = entityIds.get(fakeId);
        String tag = "FakePlayer[" + name + "]";

        // 1) PLAYER_INFO ADD_PLAYER carrying the skin textures. On 1.20+ the entry must also be
        //    marked listed (UPDATE_LISTED) via the modern PlayerInfoData ctor, or clients reject
        //    the follow-up spawn of the player entity. A blank profile name keeps the model's own
        //    overhead nametag invisible — our TextDisplay shows the configured name instead.
        try {
            PacketContainer info = protocol.createPacket(PacketType.Play.Server.PLAYER_INFO);
            info.getPlayerInfoActions().write(0,
                    java.util.EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                            EnumWrappers.PlayerInfoAction.UPDATE_LISTED));
            WrappedGameProfile profile = new WrappedGameProfile(fakeId, " ");
            var texProps = props.get(fakeId);
            if (texProps != null) {
                for (var tp : texProps) {
                    profile.getProperties().put(tp.getName(),
                            com.comphenix.protocol.wrappers.WrappedSignedProperty.fromValues(
                                    tp.getName(), tp.getValue(), tp.getSignature()));
                }
            }
            PlayerInfoData data = new PlayerInfoData(fakeId, 0, true,
                    EnumWrappers.NativeGameMode.SURVIVAL, profile,
                    WrappedChatComponent.fromLegacyText(name));
            info.getPlayerInfoDataLists().write(0, List.of(data));
            protocol.sendServerPacket(viewer, info);
        } catch (Throwable t) {
            markBroken(tag, "PLAYER_INFO", t);
            return false;
        }

        // 2) Spawn the player model. NAMED_ENTITY_SPAWN was removed in 1.20.2 — players now
        //    spawn through the generic SPAWN_ENTITY packet carrying entity type PLAYER.
        //    writeDefaults() is REQUIRED: without it unset fields stay null and the malformed
        //    packet disconnects the client.
        try {
            PacketContainer spawn = protocol.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            spawn.getModifier().writeDefaults();
            spawn.getIntegers().write(0, entityId);
            spawn.getUUIDs().write(0, fakeId);
            spawn.getEntityTypeModifier().write(0, org.bukkit.entity.EntityType.PLAYER);
            spawn.getDoubles()
                    .write(0, loc.getX())
                    .write(1, loc.getY())
                    .write(2, loc.getZ());
            byte yawAngle = (byte) Math.floor(loc.getYaw() * 256f / 360f);
            byte pitchAngle = (byte) Math.floor(loc.getPitch() * 256f / 360f);
            // 1.21.x dropped the velocity bytes: [xRot, yRot, yHeadRot]. Getting xRot wrong
            // (e.g. putting YAW there) pitches the model into the ground/sky.
            spawn.getBytes().writeSafely(0, pitchAngle);
            spawn.getBytes().writeSafely(1, yawAngle);
            spawn.getBytes().writeSafely(2, yawAngle);
            protocol.sendServerPacket(viewer, spawn);
        } catch (Throwable t) {
            markBroken(tag, "SPAWN_ENTITY", t);
            return false;
        }

        // 3) Head rotation
        try {
            PacketContainer head = protocol.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            head.getIntegers().write(0, entityId);
            head.getBytes().write(0, (byte) Math.floor(loc.getYaw() * 256f / 360f));
            protocol.sendServerPacket(viewer, head);
        } catch (Throwable t) {
            plugin.getLogger().warning(tag + " HEAD_ROTATION failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        // 4) Skin layers metadata (all overlays on). The legacy WrappedDataWatcher API no longer
        //    serializes on 1.21.x (DataItem vs DataValue) — modern ProtocolLib expects a list of
        //    WrappedDataValue in the ENTITY_METADATA packet.
        try {
            PacketContainer meta = protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            meta.getIntegers().write(0, entityId);
            var serializer = WrappedDataWatcher.Registry.get(Byte.class);
            List<com.comphenix.protocol.wrappers.WrappedDataValue> values = List.of(
                    new com.comphenix.protocol.wrappers.WrappedDataValue(SKIN_LAYERS_INDEX, serializer, SKIN_ALL));
            meta.getDataValueCollectionModifier().write(0, values);
            protocol.sendServerPacket(viewer, meta);
        } catch (Throwable t) {
            plugin.getLogger().warning(tag + " ENTITY_METADATA failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        return true;
    }

    /** Latch off packet rendering on the first fatal send failure (warns exactly once). */
    private void markBroken(String tag, String packet, Throwable t) {
        if (broken) return;
        broken = true;
        plugin.getLogger().warning(tag + " " + packet + " failed: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        plugin.getLogger().warning("ProtocolLib appears incompatible with this server version — "
                + "dummy avatars fall back to skinned-head stands. Update ProtocolLib to fix.");
        viewers.clear();
        props.clear();
    }

    /** Remove the fake player from every viewer. Never throws. */
    public void hide(Dummy d) {
        if (!available()) return;
        UUID fakeId = fakeIds.remove(keyOf(d));
        if (fakeId == null) return;
        Set<UUID> seen = viewers.remove(fakeId);
        props.remove(fakeId);
        Integer entityId = entityIds.remove(fakeId);
        if (seen == null || entityId == null) return;
        for (UUID viewerUuid : seen) {
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer == null || !viewer.isOnline()) continue;
            // Destroy first and independently — a failure in one packet must not swallow the other,
            // or the client keeps rendering a ghost model.
            try {
                PacketContainer destroy = protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                destroy.getIntegerArrays().writeSafely(0, new int[]{entityId});
                protocol.sendServerPacket(viewer, destroy);
            } catch (Throwable ignored) {
            }
            try {
                PacketContainer remove = protocol.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                remove.getUUIDLists().write(0, List.of(fakeId));
                protocol.sendServerPacket(viewer, remove);
            } catch (Throwable t) {
                // Fallback: the multi-action PLAYER_INFO packet can express REMOVE_PLAYER too.
                try {
                    PacketContainer remove = protocol.createPacket(PacketType.Play.Server.PLAYER_INFO);
                    remove.getPlayerInfoActions().write(0,
                            java.util.EnumSet.of(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER));
                    PlayerInfoData data = new PlayerInfoData(new WrappedGameProfile(fakeId, ""), 0,
                            EnumWrappers.NativeGameMode.SURVIVAL, null);
                    remove.getPlayerInfoDataLists().write(0, List.of(data));
                    protocol.sendServerPacket(viewer, remove);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Re-send every same-world avatar to a joining player (call deferred from PlayerJoinEvent). */
    public void resendTo(Player joiner) {
        if (!available()) return;
        World world = joiner.getWorld();
        for (Dummy d : plugin.dummyManager().all()) {
            if (d.avatar == null || d.avatar.isEmpty()) continue;
            if (!d.worldName.equals(world.getName())) continue;
            UUID fakeId = fakeIds.get(keyOf(d));
            if (fakeId == null || !viewers.containsKey(fakeId)) continue; // never shown yet — skip
            Location loc = new Location(world, d.x, d.y, d.z, d.yaw, d.pitch);
            if (sendShow(joiner, fakeId, d.avatar, loc)) {
                viewers.computeIfAbsent(fakeId, k -> new HashSet<>()).add(joiner.getUniqueId());
            }
        }
    }

    /** Re-send every same-world avatar to a joining player. Deferred 2 ticks. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (joiner.isOnline()) resendTo(joiner);
        }, 2L);
    }
}
