package com.lieyabull.dung.dummy;

import com.lieyabull.dung.Dung;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages stationary player-like "dummy" NPCs. Each dummy is a composition of an invisible
 * armor stand (base), a riding {@link TextDisplay} (the multi-line name) and an
 * {@link Interaction} hitbox that receives left/right clicks. Clicks execute the dummy's
 * configured command AS THE CLICKING PLAYER via {@link Bukkit#dispatchCommand}.
 * Names support multiple lines via the literal "/r" separator in input.
 */
public final class DummyManager implements Listener {

    /** Max distance (blocks) for "nearest dummy" operations like remove/name/tp. */
    private static final double NEAREST_RANGE = 5.0;
    /** Debounce window so a single click doesn't fire twice (both interact events / both hands). */
    private static final long CLICK_DEBOUNCE_MS = 150L;
    /** Extra upward render offset (blocks) for the name TextDisplay riding the stand. */
    private static final float NAME_TAG_LIFT = 0.15f;
    /** Tag offset while a packet-based avatar model is showing — the model is ~1.9 blocks tall,
     *  so the tag must float well above the invisible stand's mount point. */
    private static final float AVATAR_TAG_LIFT = 1.45f;

    private final Dung plugin;
    private final File file;
    private final YamlConfiguration data = new YamlConfiguration();
    private final List<Dummy> dummies = new ArrayList<>();
    /** Live entity composition per dummy, only present while its world is loaded. */
    private final Map<Dummy, Ref> refs = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    /** Dummies with an avatar resolve/apply currently in flight (prevents overlapping applies). */
    private final java.util.HashSet<Dummy> resolving = new java.util.HashSet<>();
    /** Resolved avatar profiles (name → profile with textures), so boot-time failures can be
     *  retried on join without re-hitting Mojang for every viewer. */
    private final Map<String, com.destroystokyo.paper.profile.PlayerProfile> avatarProfiles = new HashMap<>();
    /** Packet-based true player models (only functional when ProtocolLib is installed). */
    private final FakePlayerRenderer renderer;

    private record Ref(ArmorStand stand, Interaction interaction, TextDisplay display) {
    }

    public DummyManager(Dung plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dummies.yml");
        this.renderer = new FakePlayerRenderer(plugin);
        // Fake players must re-appear for late joiners — renderer self-registers its join listener.
        if (renderer.available()) {
            Bukkit.getPluginManager().registerEvents(renderer, plugin);
        }
        // loadAll() is deferred until worlds exist — called from onEnable 1 tick later.
    }

    // ==================== CREATION / REMOVAL ====================

    /** Create a dummy at the player's location from a raw name ("line1/r line2" → two lines). */
    public Dummy create(Player p, String rawName) {
        List<String> lines = splitLines(rawName);
        Location loc = p.getLocation();
        Dummy d = new Dummy(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), lines, null, null);
        dummies.add(d);
        spawn(d);
        save();
        return d;
    }

    /** Nearest dummy within {@link #NEAREST_RANGE} blocks of the player, or null. */
    public Dummy nearest(Player p) {
        return nearest(p, NEAREST_RANGE);
    }

    public Dummy nearest(Player p, double range) {
        World w = p.getWorld();
        Dummy best = null;
        double bestDist = range * range;
        for (Dummy d : dummies) {
            if (!d.worldName.equals(w.getName())) continue;
            double dx = d.x - p.getLocation().getX();
            double dy = d.y - p.getLocation().getY();
            double dz = d.z - p.getLocation().getZ();
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist <= bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    /** Remove the nearest dummy within 5 blocks; @return true if one was removed. */
    public boolean removeNearest(Player p) {
        Dummy d = nearest(p);
        if (d == null) return false;
        despawn(d);
        dummies.remove(d);
        lastClick.remove(p.getUniqueId());
        save();
        return true;
    }

    // ==================== MUTATION ====================

    public void setName(Dummy d, String rawName) {
        d.nameLines = splitLines(rawName);
        Ref ref = refs.get(d);
        if (ref != null) applyName(ref.display(), d);
        save();
    }

    public void setLeft(Dummy d, String cmd) {
        d.leftCommand = cmd;
        save();
    }

    public void setRight(Dummy d, String cmd) {
        d.rightCommand = cmd;
        save();
    }

    public void clearLeft(Dummy d) {
        d.leftCommand = null;
        save();
    }

    public void clearRight(Dummy d) {
        d.rightCommand = null;
        save();
    }

    /**
     * Turn the dummy into a player-looking figure wearing the named player's skin.
     * The skin is resolved via Mojang (async — this is a network lookup) and applied on the
     * main thread: a visible, armed armor stand wearing the skinned player head as its helmet.
     */
    public void setAvatar(Dummy d, String playerName) {
        d.avatar = playerName;
        save();
        resolveAndApplyAvatar(d);
    }

    /** Resolve the avatar profile off-main, then apply the look on the main thread.
     *  Profiles are cached so repeated attempts (e.g. after joins) are free.
     *  Guarded against concurrent double-invocation — overlapping applies would send duplicate
     *  packet bursts for the same dummy. */
    private void resolveAndApplyAvatar(Dummy d) {
        String name = d.avatar;
        if (name == null || name.isEmpty()) return;
        if (!resolving.add(d)) return; // already an apply in flight for this dummy
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String key = name.toLowerCase();
            com.destroystokyo.paper.profile.PlayerProfile prof = avatarProfiles.get(key);
            if (prof == null) {
                Player online = Bukkit.getPlayerExact(name);
                if (online != null) {
                    prof = online.getPlayerProfile(); // already has skin data
                } else {
                    try {
                        prof = Bukkit.createProfile(name); // triggers Mojang lookup
                        prof.complete(true);               // blocking fetch of textures
                    } catch (Exception ex) {
                        resolving.remove(d);
                        plugin.getLogger().warning("Dummy avatar lookup failed for '" + name + "': " + ex.getMessage());
                        return;
                    }
                }
                if (prof != null && prof.hasTextures()) avatarProfiles.put(key, prof);
            }
            final com.destroystokyo.paper.profile.PlayerProfile resolved = prof;
            final boolean known = resolved != null && resolved.hasTextures();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!dummies.contains(d) || !name.equals(d.avatar)) return; // stale resolve
                Ref ref = refs.get(d);
                if (ref == null) return;
                if (known) {
                    // TRUE player model via ProtocolLib; the stand goes invisible so only the
                    // player model shows. Falls back to a visible skinned-head stand when
                    // ProtocolLib is absent or its packets are incompatible with this server.
                    Location loc = new Location(Bukkit.getWorld(d.worldName), d.x, d.y, d.z, d.yaw, d.pitch);
                    if (renderer.show(d, loc, name, resolved)) {
                        ref.stand().setVisible(false);
                        ref.stand().setHelmet(null);
                        setDisplayLift(ref.display(), AVATAR_TAG_LIFT); // float above the model
                    } else {
                        renderer.hide(d); // drop any half-sent fake-player state
                        applyAvatar(ref.stand(), resolved, true);
                        setDisplayLift(ref.display(), NAME_TAG_LIFT);
                    }
                } else {
                    // Unknown player: visible unarmed stand so it's still obviously something.
                    applyAvatar(ref.stand(), null, false);
                    setDisplayLift(ref.display(), NAME_TAG_LIFT);
                }
                save();
            } finally {
                    resolving.remove(d);
                }
            });
        });
    }

    /** Re-apply the name tag's vertical render offset (used to float the tag above an avatar model). */
    private void setDisplayLift(TextDisplay display, float lift) {
        if (display == null || !display.isValid()) return;
        display.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0f, lift, 0f),          // translation
                new org.joml.Quaternionf(),                   // left rotation (none)
                new org.joml.Vector3f(1f, 1f, 1f),            // scale
                new org.joml.Quaternionf()));                 // right rotation (none)
    }

    /** Apply the player look to a dummy's armor stand (visible, arms, skinned player head). */
    private void applyAvatar(ArmorStand stand, com.destroystokyo.paper.profile.PlayerProfile prof, boolean known) {        if (!stand.isValid()) return;
        stand.setVisible(true);
        stand.setSmall(false);
        stand.setArms(true);
        if (!known) return; // unknown player: show an unarmed visible stand rather than nothing
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
        var meta = head.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            skull.setPlayerProfile(prof);
            head.setItemMeta(skull);
        }
        stand.getEquipment().setHelmet(head);
    }

    public List<Dummy> all() {
        return List.copyOf(dummies);
    }

    /** Move the dummy to the given position + facing and rebuild its live composition. */
    public void relocate(Dummy d, Location loc) {
        d.worldName = loc.getWorld().getName();
        d.x = loc.getX();
        d.y = loc.getY();
        d.z = loc.getZ();
        d.yaw = loc.getYaw();
        d.pitch = loc.getPitch();
        despawn(d);
        spawn(d);
        save();
    }

    // ==================== ENTITY COMPOSITION ====================

    private void spawn(Dummy d) {
        World w = Bukkit.getWorld(d.worldName);
        if (w == null) return; // world not loaded — will respawn when loadAll() runs again
        Location loc = new Location(w, d.x, d.y, d.z, d.yaw, d.pitch);
        sweepOrphans(w, loc);

        ArmorStand stand = w.spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setSmall(true);
            as.setArms(true); // dummies have hands so admins can give them held items
            as.setMarker(false);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setCustomNameVisible(false);
            as.setPersistent(false); // manager owns lifecycle; avoids duplicates across restarts
            as.setCollidable(false);
            if (d.handItem != null) as.getEquipment().setItemInMainHand(d.handItem);
        });

        Interaction interaction = w.spawn(loc, Interaction.class, i -> {
            i.setInteractionWidth(0.8f);
            i.setInteractionHeight(1.9f);
            i.setResponsive(true);
            i.setInvulnerable(true);
            i.setPersistent(false);
        });

        TextDisplay display = w.spawn(loc.clone().add(0, 0.3, 0), TextDisplay.class, td -> {
            td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setPersistent(false);
            td.setInvulnerable(true);
            // Render offset relative to the riding position on the stand.
            td.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0f, NAME_TAG_LIFT, 0f), // translation
                    new org.joml.Quaternionf(),                   // left rotation (none)
                    new org.joml.Vector3f(1f, 1f, 1f),            // scale
                    new org.joml.Quaternionf()));                 // right rotation (none)
            applyName(td, d);
        });
        stand.addPassenger(display);

        refs.put(d, new Ref(stand, interaction, display));
        if (d.avatar != null && !d.avatar.isEmpty()) resolveAndApplyAvatar(d);
    }

    /**
     * Remove orphaned manager-style entities near a dummy's spawn point (leftovers from earlier
     * builds/crashes that would render a second name tag inside the model). Only touches
     * invulnerable, non-persistent ArmorStands/TextDisplays/Interactions that belong to no live
     * ref — vanilla player-placed stands are persistent and are never touched.
     */
    private void sweepOrphans(World w, Location loc) {
        for (Entity e : w.getNearbyEntities(loc, 1.0, 2.5, 1.0)) {
            boolean ours = e instanceof ArmorStand || e instanceof TextDisplay || e instanceof Interaction;
            if (!ours) continue;
            if (e.isPersistent()) continue;      // player-placed / vanilla entities
            if (!e.isInvulnerable()) continue;   // ours are always invulnerable
            if (owned(e)) continue;              // still tracked by a live composition
            e.remove();
        }
    }

    /** Render the multi-line name onto a TextDisplay (each line deserialized with § codes). */    private void applyName(TextDisplay display, Dummy d) {
        List<Component> comps = new ArrayList<>();
        for (String line : d.nameLines) {
            comps.add(LegacyComponentSerializer.legacySection().deserialize(line));
        }
        display.text(Component.join(JoinConfiguration.separator(Component.newline()), comps));
    }

    private void despawn(Dummy d) {
        renderer.hide(d); // remove any client-side fake player model first
        Ref ref = refs.remove(d);
        if (ref == null) return;
        for (Entity e : new Entity[]{ref.display(), ref.interaction(), ref.stand()}) {
            if (e != null && e.isValid()) e.remove();
        }
    }

    /** Despawn every live dummy entity (used on disable). */
    public void shutdown() {
        for (Dummy d : List.copyOf(dummies)) despawn(d);
    }

    // ==================== PERSISTENCE / SELF-HEAL ====================

    /**
     * Respawn any dummy whose live composition is missing or invalid (e.g. its chunk was
     * unloaded — the entities are deliberately non-persistent so the manager owns their
     * lifecycle). Only spawns when the dummy's world AND chunk are loaded, so we never
     * force-load terrain. Also re-applies avatars via spawn().
     */
    private void ensureSpawned() {
        for (Dummy d : List.copyOf(dummies)) {
            Ref ref = refs.get(d);
            boolean alive = ref != null
                    && ref.stand().isValid()
                    && ref.interaction().isValid()
                    && ref.display().isValid();
            if (alive) continue;
            if (ref != null) despawn(d); // clean up any partial composition
            World w = Bukkit.getWorld(d.worldName);
            if (w == null) continue; // world not loaded yet — WorldLoadEvent will retry
            int cx = ((int) Math.floor(d.x)) >> 4;
            int cz = ((int) Math.floor(d.z)) >> 4;
            if (!w.isChunkLoaded(cx, cz)) continue; // ChunkLoadEvent will retry
            spawn(d);
        }
    }

    /**
     * Re-attempt packet-model rendering for avatars currently showing the fallback look.
     * At boot no one is online, so {@code show()} has no viewers and avatars settle into
     * skinned-head stands; once a viewer arrives (join/world switch) this upgrades them to
     * true player models. Profile caching makes repeated attempts cheap.
     */
    public void refreshAvatars(World w) {
        if (!renderer.available() || w == null) return;
        boolean anyFallback = false;
        for (Dummy d : dummies) {
            if (d.avatar == null || d.avatar.isEmpty()) continue;
            if (!d.worldName.equals(w.getName())) continue;
            Ref ref = refs.get(d);
            if (ref == null || !ref.stand().isValid()) continue;
            // A visible stand means the avatar is in fallback look (model couldn't be shown yet).
            if (ref.stand().isVisible()) { anyFallback = true; break; }
        }
        if (!anyFallback) return;
        for (Dummy d : List.copyOf(dummies)) {
            if (d.avatar == null || d.avatar.isEmpty()) continue;
            if (!d.worldName.equals(w.getName())) continue;
            Ref ref = refs.get(d);
            if (ref != null && ref.stand().isValid() && ref.stand().isVisible()) {
                resolveAndApplyAvatar(d);
            }
        }
    }

    /** After joining, upgrade any fallback-look avatars in the destination world. */
    @EventHandler
    public void onJoinRefresh(org.bukkit.event.player.PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> { if (joiner.isOnline()) refreshAvatars(joiner.getWorld()); }, 10L);
    }

    /** Chunks loading back in restore any dummies that lived there. */
    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        World w = event.getWorld();
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        for (Dummy d : dummies) {
            if (!d.worldName.equals(w.getName())) continue;
            if ((((int) Math.floor(d.x)) >> 4) == cx && (((int) Math.floor(d.z)) >> 4) == cz) {
                ensureSpawned();
                return;
            }
        }
    }

    /** Worlds finishing load (e.g. late-loaded worlds) get their dummies spawned. */
    @EventHandler(ignoreCancelled = true)
    public void onWorldLoad(org.bukkit.event.world.WorldLoadEvent event) {
        ensureSpawned();
    }

    /**
     * Switching worlds clears all client-side entities for the switching player: respawn any
     * lost dummy compositions in the destination world and re-send this player's avatar
     * packets there (deferred 1 tick so the client is ready).
     */
    @EventHandler(ignoreCancelled = true)
    public void onChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            ensureSpawned();
            renderer.resendTo(p);
            refreshAvatars(p.getWorld());
        });
    }

    /**
     * Load dummies.yml and respawn all dummies whose world exists/loaded.
     * Must be called AFTER worlds are available (deferred 1 tick from onEnable).
     */
    public void loadAll() {
        for (Dummy d : List.copyOf(dummies)) {
            despawn(d);
            dummies.remove(d);
        }
        if (!file.exists()) return;
        try {
            data.load(file);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        for (Map<?, ?> m : data.getMapList("dummies")) {
            Object worldObj = m.get("world");
            if (worldObj == null) continue;
            String worldName = worldObj.toString();
            if (Bukkit.getWorld(worldName) == null) continue; // skip unloaded worlds
            double x = num(m.get("x"));
            double y = num(m.get("y"));
            double z = num(m.get("z"));
            float yaw = (float) num(m.get("yaw"));
            float pitch = (float) num(m.get("pitch"));
            List<String> lines = new ArrayList<>();
            Object rawLines = m.get("name-lines");
            if (rawLines instanceof List<?> l) {
                for (Object o : l) lines.add(o.toString());
            } else if (rawLines != null) {
                lines.add(rawLines.toString());
            }
            if (lines.isEmpty()) lines.add("§7Dummy");
            Dummy d = new Dummy(worldName, x, y, z, yaw, pitch, lines,
                    strOrNull(m.get("left-command")), strOrNull(m.get("right-command")));
            d.avatar = strOrNull(m.get("avatar"));
            Object hand = m.get("hand-item");
            d.handItem = hand instanceof org.bukkit.inventory.ItemStack is ? is : null;
            dummies.add(d);
            spawn(d);
        }
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static String strOrNull(Object o) {
        String s = o == null ? null : o.toString();
        return s == null || s.isEmpty() ? null : s;
    }

    /** Max characters per display line — anything longer fills the player's screen. */
    public static final int MAX_LINE_LENGTH = 48;
    /** Max number of lines a dummy name can have. */
    public static final int MAX_LINES = 4;

    /**
     * Split a raw name on the literal "/r" separator into trimmed display lines.
     * Each line is truncated to {@link #MAX_LINE_LENGTH} characters and the total
     * is capped at {@link #MAX_LINES} lines so the TextDisplay can't screen-bomb players.
     */
    public static List<String> splitLines(String rawName) {
        List<String> lines = new ArrayList<>();
        for (String part : rawName.split("/r")) {
            String trimmed = part.trim();
            if (trimmed.length() > MAX_LINE_LENGTH) {
                trimmed = trimmed.substring(0, MAX_LINE_LENGTH) + "…";
            }
            if (!trimmed.isEmpty()) lines.add(trimmed);
            if (lines.size() >= MAX_LINES) break;
        }
        if (lines.isEmpty() || lines.stream().allMatch(String::isEmpty)) lines.add("§7Dummy");
        return lines;
    }

    public void save() {
        try {
            data.set("dummies", null);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Dummy d : dummies) {
                Map<String, Object> m = new HashMap<>();
                m.put("world", d.worldName);
                m.put("x", d.x);
                m.put("y", d.y);
                m.put("z", d.z);
                m.put("yaw", d.yaw);
                m.put("pitch", d.pitch);
                m.put("name-lines", d.nameLines);
                m.put("left-command", d.leftCommand == null ? "" : d.leftCommand);
                m.put("right-command", d.rightCommand == null ? "" : d.rightCommand);
                m.put("avatar", d.avatar == null ? "" : d.avatar);
                if (d.handItem != null) m.put("hand-item", d.handItem);
                list.add(m);
            }
            data.set("dummies", list);
            file.getParentFile().mkdirs();
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.write(tmp.toPath(), data.saveToString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== CLICK HANDLING ====================

    /** Right-click on a dummy's Interaction → run its right command as the clicking player. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        // PlayerInteractAtEntityEvent extends this type and has its own handler below — skip here.
        if (event instanceof PlayerInteractAtEntityEvent) return;
        handleRightClick(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        handleRightClick(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleRightClick(Player p, Entity clicked, PlayerInteractEntityEvent event) {
        Dummy d = byInteraction(clicked);
        if (d == null) return;
        event.setCancelled(true);
        // Admins can dress the dummy: sneak + right-click with an item in hand swaps it into the
        // stand's main hand (any item already there is handed back). Skipped while an avatar's
        // packet-based player model is showing — the invisible base stand isn't visible then.
        Ref ref = refs.get(d);
        if (p.isSneaking() && p.hasPermission("dung.admin")
                && ref != null && ref.stand().isValid() && ref.stand().isVisible()
                && !p.getInventory().getItemInMainHand().getType().isAir()) {
            org.bukkit.inventory.ItemStack held = p.getInventory().getItemInMainHand();
            org.bukkit.inventory.ItemStack give = held.clone();
            give.setAmount(1);
            org.bukkit.inventory.ItemStack prev = ref.stand().getEquipment().getItemInMainHand();
            ref.stand().getEquipment().setItemInMainHand(give);
            d.handItem = give;
            if (held.getAmount() > 1) held.setAmount(held.getAmount() - 1);
            else p.getInventory().setItemInMainHand(null);
            if (prev != null && !prev.getType().isAir()) {
                var leftover = p.getInventory().addItem(prev);
                leftover.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
            }
            save();
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.0f);
            return; // don't run the right-click command when dressing the dummy
        }
        if (!debounce(p)) return;
        playClick(p);
        execute(p, d.rightCommand);
    }

    /** Left-click (attack) on a dummy's Interaction → run its left command as the attacker. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Dummy d = byInteraction(event.getEntity());
        if (d == null) return;
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player p)) return;
        if (!debounce(p)) return;
        playClick(p);
        execute(p, d.leftCommand);
    }

    /** Dummies are invulnerable: cancel any damage to any of their entities. */
    @EventHandler(ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return; // handled above
        if (owned(event.getEntity())) event.setCancelled(true);
    }

    private boolean owned(Entity e) {
        for (Ref ref : refs.values()) {
            if (e.equals(ref.stand()) || e.equals(ref.interaction()) || e.equals(ref.display())) return true;
        }
        return false;
    }

    private Dummy byInteraction(Entity clicked) {
        if (!(clicked instanceof Interaction)) return null;
        for (Map.Entry<Dummy, Ref> e : refs.entrySet()) {
            if (clicked.equals(e.getValue().interaction())) return e.getKey();
        }
        return null;
    }

    /** True if enough time passed since this player's last dummy click. */
    private boolean debounce(Player p) {
        long now = System.currentTimeMillis();
        Long prev = lastClick.get(p.getUniqueId());
        lastClick.put(p.getUniqueId(), now);
        return prev == null || now - prev >= CLICK_DEBOUNCE_MS;
    }

    private void playClick(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    /** Run a dummy command as the clicking player; leading "/" is optional. */
    private void execute(Player p, String cmd) {
        if (cmd == null || cmd.isEmpty()) return;
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        Bukkit.dispatchCommand(p, c);
    }
}
