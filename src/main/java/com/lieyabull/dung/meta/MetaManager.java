package com.lieyabull.dung.meta;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Permanent progression that survives runs: persistent coins, unlock flags, and the
 * player's class choice. Run gear/currency is intentionally NOT here (lost on death).
 */
public final class MetaManager {
    private final File file;
    private final YamlConfiguration data = new YamlConfiguration();
    private final Map<UUID, MetaProfile> profiles = new LinkedHashMap<>();

    public MetaManager(File file) {
        this.file = file;
    }

    public void load() {
        if (!file.exists()) return;
        try {
            data.load(file);
        } catch (Exception e) {
            // Never silently wipe player data: back the corrupt file up for recovery instead of
            // leaving an empty map that the next save() would write over the original.
            e.printStackTrace();
            try {
                File corrupt = new File(file.getParentFile(),
                        file.getName() + ".corrupt-" + System.currentTimeMillis());
                if (file.renameTo(corrupt)) {
                    System.out.println("[Dung] Corrupt save backed up to " + corrupt.getName());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void save() {
        try {
            for (Map.Entry<UUID, MetaProfile> e : profiles.entrySet()) {
                String key = e.getKey().toString();
                MetaProfile prof = e.getValue();
                data.set(key + ".coins", prof.persistentCoins);
                data.set(key + ".deaths", prof.deaths);
                data.set(key + ".clears", prof.clears);
                data.set(key + ".class", prof.classId);
                data.set(key + ".kills", prof.kills);
                data.set(key + ".bestsFloor", prof.bestFloor);
                data.set(key + ".shards", prof.shards);
                data.set(key + ".tutorial", prof.hasSeenTutorial);
                if (prof.lastWorld != null) {
                    data.set(key + ".lastWorld", prof.lastWorld);
                    data.set(key + ".lastX", prof.lastX);
                    data.set(key + ".lastY", prof.lastY);
                    data.set(key + ".lastZ", prof.lastZ);
                    data.set(key + ".lastYaw", (double) prof.lastYaw);
                    data.set(key + ".lastPitch", (double) prof.lastPitch);
                }
                for (java.util.Map.Entry<String, Integer> u : prof.upgrades.entrySet()) {
                    data.set(key + ".upgrades." + u.getKey(), u.getValue());
                }
            }
            file.getParentFile().mkdirs();
            // Atomic write: dump to a temp file, then move it over the target. A crash mid-write
            // can no longer corrupt the live save (the old file stays intact until the swap).
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

    public MetaProfile profile(UUID id) {
        return profiles.computeIfAbsent(id, k -> {
            String key = id.toString();
            MetaProfile p = new MetaProfile();
            p.persistentCoins = data.getInt(key + ".coins", 0);
            p.deaths = data.getInt(key + ".deaths", 0);
            p.clears = data.getInt(key + ".clears", 0);
            p.classId = data.getString(key + ".class", "warrior");
            p.kills = data.getInt(key + ".kills", 0);
            p.bestFloor = data.getInt(key + ".bestsFloor", 0);
            p.shards = data.getInt(key + ".shards", 0);
            p.hasSeenTutorial = data.getBoolean(key + ".tutorial", false);
            if (data.contains(key + ".lastWorld")) {
                p.lastWorld = data.getString(key + ".lastWorld");
                p.lastX = data.getDouble(key + ".lastX", 0);
                p.lastY = data.getDouble(key + ".lastY", 0);
                p.lastZ = data.getDouble(key + ".lastZ", 0);
                p.lastYaw = (float) data.getDouble(key + ".lastYaw", 0);
                p.lastPitch = (float) data.getDouble(key + ".lastPitch", 0);
            }
            if (data.contains(key + ".upgrades")) {
                for (String u : data.getConfigurationSection(key + ".upgrades").getKeys(false)) {
                    p.upgrades.put(u, data.getInt(key + ".upgrades." + u, 0));
                }
            }
            return p;
        });
    }

    /** Wipe all player data from memory and disk. */
    public void clearAll() {
        profiles.clear();
        for (String key : data.getKeys(false)) {
            data.set(key, null);
        }
        save();
    }

    /** Add persistent coins (survive death); separate from run coins. */
    public void addPersistentCoins(UUID id, int amount) {
        profile(id).persistentCoins += amount;
    }

    public static final class MetaProfile {
        public int persistentCoins;
        public int shards;
        public final Map<String, Integer> upgrades = new LinkedHashMap<>();
        public boolean hasSeenTutorial;
        public int deaths;
        public int clears;
        public String classId = "warrior";
        public int kills;
        public int bestFloor;
        // Last known location for rejoin — null means use default spawn
        public String lastWorld;
        public double lastX, lastY, lastZ;
        public float lastYaw, lastPitch;
    }
}