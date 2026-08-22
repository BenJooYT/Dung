package com.lieyabull.dung.shop;

/**
 * Server-authoritative state machine for a single shop transaction. It is pure (no Bukkit state) so
 * the transaction rules can be unit-tested: exactly one roll per purchase, exactly one KEEP/SALVAGE
 * decision per roll, no roll while an animation is active, and no switching tabs mid-roll.
 * <p>The UI charges currency only when {@link #startRoll} returns true, which guarantees the currency
 * is spent exactly once per purchase. The result item is attached via {@link #attachResult} and can
 * only be consumed once ({@link #reset} must be called before another roll begins).
 */
public final class ShopTransaction {
    public enum State {
        IDLE,           // showing the tab: cost + ROLL button
        ROLLING_ITEM,   // item slot-machine animation in progress
        ROLLING_RARITY, // rarity slot-machine animation in progress
        RESULT,         // final item shown with KEEP / SALVAGE
        KEEP_PENDING    // inventory was full; result still pending a retry
    }

    private final ShopType type;
    private Category category;
    private State state = State.IDLE;
    private ServerSideRollResult pending;

    public ShopTransaction(ShopType type) {
        this.type = type;
    }

    public ShopType type() {
        return type;
    }

    public Category category() {
        return category;
    }

    public State state() {
        return state;
    }

    public ServerSideRollResult pending() {
        return pending;
    }

    /** Switch tabs. Only allowed while idle (never mid-roll or mid-result). */
    public boolean selectCategory(Category cat) {
        if (state != State.IDLE) return false;
        category = cat;
        return true;
    }

    /** Begin a roll. Only one roll may be active at a time; returns false if already rolling. */
    public boolean startRoll(Category cat) {
        if (state != State.IDLE) return false;
        category = cat;
        state = State.ROLLING_ITEM;
        return true;
    }

    /** Attach the server-generated result (called right after {@link #startRoll} succeeds). */
    public void attachResult(ServerSideRollResult result) {
        this.pending = result;
    }

    public void itemAnimationFinished() {
        if (state == State.ROLLING_ITEM) state = State.ROLLING_RARITY;
    }

    public void rarityAnimationFinished() {
        if (state == State.ROLLING_RARITY) state = State.RESULT;
    }

    /** Player's inventory was full when trying to keep the item; keep the result pending for retry. */
    public void markKeepPending() {
        if (state == State.RESULT) state = State.KEEP_PENDING;
    }

    public boolean canChoose() {
        return state == State.RESULT || state == State.KEEP_PENDING;
    }

    public boolean canRoll() {
        return state == State.IDLE;
    }

    /** After a KEEP or SALVAGE succeeds, return to the idle tab view for the next purchase. */
    public void reset() {
        state = State.IDLE;
        pending = null;
    }
}