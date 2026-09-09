package com.lieyabull.dung.game;

import com.lieyabull.dung.dungeon.Floor;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Holds per-run mutable data (lost on death). Gear itself lives in the player's inventory.
 *  Supports multiple players (party mode) via per-player PlayerState. */
public final class Run {
    public final Random rng;
    public int floorIndex;
    public Floor floor;
    public long startNanos;
    public int runCoinsEarned; // run coins earned so far (from clears/boss), lost on death
    public int bankedCoins;    // how much of runCoinsEarned has already been banked to persistent
    /** Enemies defeated this run, tracked per player (the player who landed the killing blow).
     *  Kills are settled into the persistent profile when the player's run involvement ends. */
    public final Map<UUID, Integer> kills = new HashMap<>();
    /** Per-player salvage shards earned this floor (added to persistent shards on boss defeat,
     *  reset on floor entry, lost on death before boss defeat). */
    public final Map<UUID, Integer> salvageShards = new HashMap<>();
    private final Map<UUID, PlayerState> playerStates = new HashMap<>();

    public Run(long seed) {
        this.rng = new Random(seed);
        this.startNanos = System.nanoTime();
    }

    public void addPlayerState(UUID uuid, PlayerState ps) {
        playerStates.put(uuid, ps);
    }

    public PlayerState playerStateOf(UUID uuid) {
        return playerStates.get(uuid);
    }

    /** The player's own kill count this run (0 if none). */
    public int killsOf(UUID uuid) {
        Integer v = kills.get(uuid);
        return v != null ? v : 0;
    }

    public void addKill(UUID uuid) {
        kills.merge(uuid, 1, Integer::sum);
    }

    /** Returns this player's unsettled kills and clears them — each kill is settled into the
     *  persistent profile exactly once per run. */
    public int takeKills(UUID uuid) {
        Integer v = kills.remove(uuid);
        return v != null ? v : 0;
    }

    /** Returns the first player state (for legacy single-player access). */
    public PlayerState playerState() {
        return playerStates.isEmpty() ? null : playerStates.values().iterator().next();
    }

    // ---------- Combat Power difficulty lock (design section 15) ----------
    // Computed once at run start from the weighted party CP and never recalculated mid-run.
    /** Weighted party CP at run start (100%/90%/80%/64% weakest-first). */
    public double cpWeighted = 0;
    /** Locked difficulty modifier, clamped to +-0.10 early / +-0.20 late. */
    public double cpModifier = 0;
    /** Encounter-complexity share (tight leash, elite chance hard-capped at 25%/room). */
    public double cpComplexity = 0;
    /** Enemy damage share (middle leash). */
    public double cpDmg = 0;
    /** Enemy health share (widest leash, allowed outside the elite cap). */
    public double cpHp = 0;
    /** True once the modifier has been locked at run start. */
    public boolean cpLocked = false;
}
