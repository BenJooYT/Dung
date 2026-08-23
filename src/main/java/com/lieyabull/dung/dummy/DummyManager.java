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

    private final Dung plugin;
    private final File file;
    private final YamlConfiguration data = new YamlConfiguration();
    private final List<Dummy> dummies = new ArrayList<>();
    /** Live entity composition per dummy, only present while its world is loaded. */
    private final Map<Dummy, Ref> refs = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    private record Ref(ArmorStand stand, Interaction interaction, TextDisplay display) {
    }

    public DummyManager(Dung plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dummies.yml");
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

    public List<Dummy> all() {
        return List.copyOf(dummies);
    }

    // ==================== ENTITY COMPOSITION ====================

    private void spawn(Dummy d) {
        World w = Bukkit.getWorld(d.worldName);
        if (w == null) return; // world not loaded — will respawn when loadAll() runs again
        Location loc = new Location(w, d.x, d.y, d.z, d.yaw, d.pitch);

        ArmorStand stand = w.spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setSmall(true);
            as.setMarker(false);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setCustomNameVisible(false);
            as.setPersistent(false); // manager owns lifecycle; avoids duplicates across restarts
            as.setCollidable(false);
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
            applyName(td, d);
        });
        stand.addPassenger(display);

        refs.put(d, new Ref(stand, interaction, display));
    }

    /** Render the multi-line name onto a TextDisplay (each line deserialized with § codes). */
    private void applyName(TextDisplay display, Dummy d) {
        List<Component> comps = new ArrayList<>();
        for (String line : d.nameLines) {
            comps.add(LegacyComponentSerializer.legacySection().deserialize(line));
        }
        display.text(Component.join(JoinConfiguration.separator(Component.newline()), comps));
    }

    private void despawn(Dummy d) {
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

    /** Split a raw name on the literal "/r" separator into trimmed display lines. */
    public static List<String> splitLines(String rawName) {
        List<String> lines = new ArrayList<>();
        for (String part : rawName.split("/r")) {
            lines.add(part.trim());
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
