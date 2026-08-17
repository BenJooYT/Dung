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
    public int kills;          // enemies defeated this run
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

    /** Returns the first player state (for legacy single-player access). */
    public PlayerState playerState() {
        return playerStates.isEmpty() ? null : playerStates.values().iterator().next();
    }
}