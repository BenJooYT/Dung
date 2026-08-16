package com.lieyabull.dung.game;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.party.Party;
import com.lieyabull.dung.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of active dungeon instances. Each party gets its own DungeonInstance.
 * Routes events to the correct instance per player. Supports parallel dungeons.
 */
public final class GameManager {
    private final Dung plugin;
    private final PartyManager partyManager;
    private final Map<UUID, DungeonInstance> instances = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonInstance> playerInstance = new ConcurrentHashMap<>();
    private static GameManager instance;

    public static GameManager instance() {
        return instance;
    }

    public GameManager(Dung plugin) {
        this.plugin = plugin;
        this.partyManager = new PartyManager();
        instance = this;
        startTicker();
    }

    public PartyManager partyManager() { return partyManager; }

    /** Get the dungeon instance a player is currently in, or null. */
    public DungeonInstance instanceOf(Player p) {
        return playerInstance.get(p.getUniqueId());
    }

    /** Get a dungeon instance by its ID. */
    public DungeonInstance instanceById(UUID id) {
        return instances.get(id);
    }

    /** Get all active dungeon instances. */
    public Iterable<DungeonInstance> instances() {
        return instances.values();
    }

    /** Check if a player is in any active dungeon instance. */
    public boolean isInInstance(Player p) {
        return playerInstance.containsKey(p.getUniqueId());
    }

    /** Start a new dungeon run for a party. */
    public void startRun(Party party, long seed) {
        // Check no party member is already in a run
        for (UUID uid : party.members()) {
            if (playerInstance.containsKey(uid)) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) p.sendMessage("§cYou're already in a run.");
                return;
            }
        }
        DungeonInstance di = new DungeonInstance(plugin, party);
        instances.put(di.instanceId(), di);
        for (UUID uid : party.members()) {
            playerInstance.put(uid, di);
        }
        di.startRun(seed);
    }

    /** End a player's dungeon instance (leave/disband). */
    public void leaveInstance(Player p) {
        DungeonInstance di = playerInstance.get(p.getUniqueId());
        if (di == null) return;
        // Remove all party members from the instance map first, then end the run
        for (UUID uid : di.party().members()) {
            playerInstance.remove(uid);
        }
        instances.remove(di.instanceId());
        di.endRun();
    }

    /** Remove a single player from the instance map (used when a player dies mid-run, since they
     *  are removed from the party first, so neither leaveInstance nor removeInstance would find
     *  them anymore — leaving isInInstance() true forever and blocking /dung start). */
    public void removePlayerFromInstance(Player p) {
        playerInstance.remove(p.getUniqueId());
    }

    /** End a specific dungeon instance. */
    public void removeInstance(DungeonInstance di) {
        instances.remove(di.instanceId());
        for (UUID uid : di.party().members()) {
            playerInstance.remove(uid);
        }
    }

    /** Static helper so Enemy can reach the run without circular constructor params. */
    public static boolean playerHurt(Player p, double dmg) {
        if (instance == null) return false;
        DungeonInstance di = instance.playerInstance.get(p.getUniqueId());
        if (di == null) return false;
        return di.playerHurt(p, dmg);
    }

    // ---------- tick ----------

    public void tick() {
        for (DungeonInstance di : List.copyOf(instances.values())) {
            di.tick();
        }
    }

    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() { tick(); }
        }.runTaskTimer(plugin, 0, 1);
    }

    public void shutdown() {
        for (DungeonInstance di : List.copyOf(instances.values())) {
            di.endRun();
        }
        instances.clear();
        playerInstance.clear();
    }
}
