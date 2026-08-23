package com.lieyabull.dung.world;

import com.lieyabull.dung.Dung;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Owns the two special world types the server manages: the persistent LOBBY world
 * (players spawn/join/return here) and the DEDICATED per-run dungeon worlds that are
 * created when a run starts and deleted from disk when it ends. The plots world
 * ({@code dung_plots}) is managed separately by PlotManager.
 */
public final class WorldManager {
    public static final String LOBBY_WORLD_NAME = "dung_lobby";
    public static final String RUN_WORLD_PREFIX = "dung_run_";

    private final Dung plugin;
    private World lobby;
    /** Custom lobby spawn set via /setlobby; null = default (0, 64, 0). Persisted to lobby.yml. */
    private Location customLobbySpawn;

    public WorldManager(Dung plugin) {
        this.plugin = plugin;
        loadCustomSpawn();
    }

    /** The file persisting the /setlobby spawn across restarts. */
    private java.io.File spawnFile() {
        return new File(plugin.getDataFolder(), "lobby.yml");
    }

    private void loadCustomSpawn() {
        try {
            org.bukkit.configuration.file.YamlConfiguration yml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(spawnFile());
            String worldName = yml.getString("world");
            if (worldName == null) return;
            org.bukkit.World w = Bukkit.getWorld(worldName);
            if (w == null) w = Bukkit.getWorlds().stream()
                    .filter(x -> x.getName().equals(worldName)).findFirst().orElse(null);
            if (w == null) return;
            customLobbySpawn = new Location(w,
                    yml.getDouble("x"), yml.getDouble("y"), yml.getDouble("z"),
                    (float) yml.getDouble("yaw"), (float) yml.getDouble("pitch"));
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to read lobby.yml: " + ex.getMessage());
        }
    }

    private void saveCustomSpawn() {
        try {
            org.bukkit.configuration.file.YamlConfiguration yml =
                    new org.bukkit.configuration.file.YamlConfiguration();
            if (customLobbySpawn != null) {
                yml.set("world", customLobbySpawn.getWorld().getName());
                yml.set("x", customLobbySpawn.getX());
                yml.set("y", customLobbySpawn.getY());
                yml.set("z", customLobbySpawn.getZ());
                yml.set("yaw", (double) customLobbySpawn.getYaw());
                yml.set("pitch", (double) customLobbySpawn.getPitch());
            }
            File tmp = new File(spawnFile().getParentFile(), spawnFile().getName() + ".tmp");
            java.nio.file.Files.write(tmp.toPath(), yml.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try {
                java.nio.file.Files.move(tmp.toPath(), spawnFile().toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                java.nio.file.Files.move(tmp.toPath(), spawnFile().toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to save lobby.yml: " + ex.getMessage());
        }
    }

    /** /setlobby: set the lobby spawn (and world spawn) to the player's current location. */
    public boolean setLobbySpawn(Player p) {
        World lobbyWorld = getLobby();
        if (!p.getWorld().equals(lobbyWorld)) return false;
        Location at = p.getLocation();
        customLobbySpawn = at.clone();
        lobbyWorld.setSpawnLocation(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        saveCustomSpawn();
        return true;
    }

    /** Get or create the persistent lobby world players spawn/join in. */
    public World getLobby() {
        if (lobby != null) return lobby;
        World w = Bukkit.getWorld(LOBBY_WORLD_NAME);
        boolean fresh = w == null;
        if (fresh) {
            WorldCreator wc = new WorldCreator(LOBBY_WORLD_NAME);
            wc.generator(new VoidChunkGenerator());
            wc.generateStructures(false);
            w = wc.createWorld();
        }
        if (w != null) {
            // Lobby gamerules — re-applied on every load so they always hold, even after restarts
            w.setGameRule(GameRules.SPAWN_MOBS, false);            // no mobs of any kind
            w.setGameRule(GameRules.SPAWN_PHANTOMS, false);        // no phantoms for idle players
            w.setGameRule(GameRules.RAIDS, false);                 // no raids triggered here
            w.setGameRule(GameRules.KEEP_INVENTORY, true);         // falling into the void loses nothing
            w.setGameRule(GameRules.ADVANCE_TIME, false);          // permanently noon
            w.setTime(6000);
            w.setGameRule(GameRules.ADVANCE_WEATHER, false);       // permanently clear
            w.setStorm(false);
            w.setThundering(false);
            w.setWeatherDuration(0);
            // Vanilla split doFireTick into these two rules; the typed GameRule constant for
            // allowFireTicksAwayFromPlayer is deprecated in this API version, so set it by command.
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "gamerule allowFireTicksAwayFromPlayer false");            // fire can't burn builds
            w.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);      // and can't spread either
            w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);       // no advancement spam in lobby chat
            w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);                   // no grass spread / leaf decay / random ticks
            w.setPVP(false);                                                 // no PvP at spawn
            // Default spawn is only applied when no /setlobby spawn has been saved
            if (customLobbySpawn == null) w.setSpawnLocation(0, 64, 0);
            if (fresh) buildSpawnPlatform(w);
        }
        lobby = w;
        return w;
    }

    /** Create a fresh void run world dedicated to a single dungeon instance. */
    public World createRunWorld(String id) {
        String safe = id == null ? "" : id.toLowerCase().replaceAll("[^a-z0-9]", "");
        WorldCreator wc = new WorldCreator(RUN_WORLD_PREFIX + safe);
        wc.generator(new VoidChunkGenerator());
        wc.generateStructures(false);
        World w = wc.createWorld();
        if (w != null) {
            w.setGameRule(GameRules.SPAWN_MOBS, false);
            w.setGameRule(GameRules.KEEP_INVENTORY, false);
        }
        return w;
    }

    /** Kick any remaining players out to the lobby spawn, unload the run world, then delete its
     *  folder from disk. Never throws. */
    public void deleteRunWorld(World w) {
        if (w == null) return;
        try {
            Location out = lobbySpawn();
            for (Player p : w.getPlayers()) p.teleport(out);
            Bukkit.unloadWorld(w, false);
            File dir = new File(Bukkit.getWorldContainer(), w.getName());
            if (dir.exists()) deleteRecursively(dir);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to delete run world " + w.getName() + ": " + ex.getMessage());
        }
    }

    /** The lobby spawn location (players always come back here after a run). Uses the /setlobby
     *  spawn when one has been saved, otherwise the lobby world's default spawn. */
    public Location lobbySpawn() {
        if (customLobbySpawn != null && customLobbySpawn.getWorld() != null
                && customLobbySpawn.getWorld().equals(getLobby())) {
            return customLobbySpawn.clone();
        }
        return getLobby().getSpawnLocation();
    }

    /** Build a small obsidian platform under the lobby spawn so players don't fall into the void. */
    private void buildSpawnPlatform(World w) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    w.getBlockAt(x, 63, z).setType(Material.OBSIDIAN);
                }
            }
        });
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursively(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Minimal void generator: no noise, surface, carvers, structures, decorations or mobs —
     *  every chunk stays empty. */
    private static final class VoidChunkGenerator extends org.bukkit.generator.ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise(org.bukkit.generator.WorldInfo worldInfo,
                                           java.util.Random random,
                                           int chunkX, int chunkZ) {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface(org.bukkit.generator.WorldInfo worldInfo,
                                             java.util.Random random,
                                             int chunkX, int chunkZ) {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves(org.bukkit.generator.WorldInfo worldInfo,
                                             java.util.Random random,
                                             int chunkX, int chunkZ) {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures(org.bukkit.generator.WorldInfo worldInfo,
                                                java.util.Random random,
                                                int chunkX, int chunkZ) {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations(org.bukkit.generator.WorldInfo worldInfo,
                                                 java.util.Random random,
                                                 int chunkX, int chunkZ) {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs(org.bukkit.generator.WorldInfo worldInfo,
                                          java.util.Random random,
                                          int chunkX, int chunkZ) {
            return false;
        }
    }
}
