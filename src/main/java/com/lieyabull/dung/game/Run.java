package com.lieyabull.dung.game;

import com.lieyabull.dung.dungeon.Floor;

import java.util.Random;

/** Holds per-run mutable data (lost on death). Gear itself lives in the player's inventory. */
public final class Run {
    public final Random rng;
    public int floorIndex;
    public Floor floor;
    public long startNanos;
    public int runCoinsEarned; // run coins earned so far (from clears/boss), lost on death
    public int bankedCoins;    // how much of runCoinsEarned has already been banked to persistent
    public int kills;          // enemies defeated this run
    private PlayerState playerState;

    public Run(long seed) {
        this.rng = new Random(seed);
        this.startNanos = System.nanoTime();
    }

    public void setPlayerState(PlayerState ps) {
        this.playerState = ps;
    }

    public PlayerState playerState() {
        return playerState;
    }
}