package com.lieyabull.dung.game;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.party.Party;
import com.lieyabull.dung.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
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

    /** Find a dungeon instance by its run world. */
    public DungeonInstance instanceByWorld(World w) {
        for (DungeonInstance di : instances.values()) {
            if (di.world() != null && di.world().equals(w)) return di;
        }
        return null;
    }

    /** Get all active dungeon instances. */
    public Iterable<DungeonInstance> instances() {
        return instances.values();
    }

    /** Check if a player is in any active dungeon instance. */
    public boolean isInInstance(Player p) {
        return playerInstance.containsKey(p.getUniqueId());
    }

    /** Start a new dungeon run for a party. Each instance is offset ~1000 blocks from the
     *  previous one so multiple parties can run parallel dungeons without overlapping.
     *  Returns false (and starts nothing) if any member is genuinely still in an ACTIVE run. */
    public boolean startRun(Party party, long seed) {
        // Check no party member is already in an ACTIVE run. Stale mappings from an already-ended
        // instance (e.g. a member who died and left the party before endRun's cleanup ran) are
        // purged here instead of blocking every future start for that player.
        for (UUID uid : party.members()) {
            DungeonInstance existing = playerInstance.get(uid);
            if (existing == null) continue;
            if (instances.containsKey(existing.instanceId()) && existing.isRunning()) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "run.alreadyIn"));
                return false;
            }
            playerInstance.remove(uid);
        }
        // Each run gets its own dedicated void world, created here and deleted from disk when
        // the run ends (DungeonInstance.endRun). Offsets stay 0 — the private world needs no
        // region math to keep parallel parties apart.
        String runId = "r" + System.nanoTime();
        org.bukkit.World runWorld = plugin.worldManager().createRunWorld(runId);
        DungeonInstance di = new DungeonInstance(plugin, party, 0, 0, runWorld);
        instances.put(di.instanceId(), di);
        for (UUID uid : party.members()) {
            playerInstance.put(uid, di);
        }
        di.startRun(seed);
        return true;
    }

    /** End a player's participation in a dungeon run (/dung leave). Removes just that player from
     *  their instance and party; the shared run continues for the rest of the party. */
    public void leaveInstance(Player p) {
        DungeonInstance di = playerInstance.get(p.getUniqueId());
        if (di == null) return;
        // Remove the player from the party first so removePlayer can detect an empty party.
        di.party().removeMember(p.getUniqueId());
        di.removePlayer(p);
        // Clean up the PartyManager's playerParties entry so the player can start a new run.
        // If the party is now empty, also remove it from partyById so it won't be reused.
        partyManager.cleanupAfterLeave(p);
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

    /** True once the player is flagged AFK; AFK players are not hurt by run damage at all. */
    private static boolean isAfkProtected(Player p) {
        Dung dung = Dung.instance();
        if (dung == null) return false;
        com.lieyabull.dung.listener.AfkListener afk = dung.afkListener();
        return afk != null && afk.isAfk(p);
    }

    /** Static helper so Enemy can reach the run without circular constructor params. */
    public static boolean playerHurt(Player p, double dmg) {
        if (instance == null) return false;
        if (isAfkProtected(p)) return false;
        DungeonInstance di = instance.playerInstance.get(p.getUniqueId());
        if (di == null) return false;
        return di.playerHurt(p, dmg);
    }

    /** Static helper for Mulliboom explosions that bypass invulnerability frames. */
    public static boolean playerHurtBypassInvuln(Player p, double dmg) {
        if (instance == null) return false;
        if (isAfkProtected(p)) return false;
        DungeonInstance di = instance.playerInstance.get(p.getUniqueId());
        if (di == null) return false;
        return di.playerHurtBypassInvuln(p, dmg);
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
