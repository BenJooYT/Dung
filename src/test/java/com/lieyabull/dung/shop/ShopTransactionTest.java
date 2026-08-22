package com.lieyabull.dung.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure state-machine tests for {@link ShopTransaction} — no Bukkit dependencies. */
public class ShopTransactionTest {

    @Test
    void startsIdle() {
        ShopTransaction t = new ShopTransaction(ShopType.RUN);
        assertEquals(ShopTransaction.State.IDLE, t.state());
        assertTrue(t.canRoll());
        assertFalse(t.canChoose());
    }

    @Test
    void selectCategoryOnlyWhileIdle() {
        ShopTransaction t = new ShopTransaction(ShopType.PERSISTENT);
        assertTrue(t.selectCategory(Category.ARMOR));
        assertEquals(Category.ARMOR, t.category());

        t.startRoll(Category.WEAPON);
        assertFalse(t.selectCategory(Category.WEAPON), "cannot switch tabs mid-roll");
    }

    @Test
    void cannotRollTwice() {
        ShopTransaction t = new ShopTransaction(ShopType.RUN);
        assertTrue(t.startRoll(Category.WEAPON));
        assertFalse(t.startRoll(Category.ARMOR), "only one roll may be active");
    }

    @Test
    void cannotRollWhileResultPending() {
        ShopTransaction t = new ShopTransaction(ShopType.RUN);
        t.startRoll(Category.WEAPON);
        t.itemAnimationFinished();
        t.rarityAnimationFinished();
        assertEquals(ShopTransaction.State.RESULT, t.state());
        assertFalse(t.canRoll(), "cannot roll again before KEEP/SALVAGE resolves");
        assertTrue(t.canChoose());
    }

    @Test
    void fullLifecycleReturnsToIdle() {
        ShopTransaction t = new ShopTransaction(ShopType.RUN);
        assertTrue(t.startRoll(Category.MANA_SHIELD));
        assertFalse(t.canChoose());
        t.attachResult(new ServerSideRollResult(null, null, Category.MANA_SHIELD, 3));
        assertEquals(ShopTransaction.State.ROLLING_ITEM, t.state());
        t.itemAnimationFinished();
        assertEquals(ShopTransaction.State.ROLLING_RARITY, t.state());
        t.rarityAnimationFinished();
        assertEquals(ShopTransaction.State.RESULT, t.state());
        assertTrue(t.canChoose());
        assertNotNull(t.pending());
        t.reset();
        assertEquals(ShopTransaction.State.IDLE, t.state());
        assertNull(t.pending());
        assertTrue(t.canRoll());
        assertFalse(t.canChoose());
    }

    @Test
    void fullInventoryKeepsResultPending() {
        ShopTransaction t = new ShopTransaction(ShopType.RUN);
        t.startRoll(Category.ARMOR);
        t.itemAnimationFinished();
        t.rarityAnimationFinished();
        t.markKeepPending();
        assertEquals(ShopTransaction.State.KEEP_PENDING, t.state());
        assertTrue(t.canChoose(), "player may retry KEEP or choose SALVAGE while pending");
        assertFalse(t.canRoll(), "cannot start a new roll while a result is pending");
        assertFalse(t.selectCategory(Category.ARMOR), "cannot switch tabs while a result is pending");
        t.reset();
        assertTrue(t.canRoll());
    }

    @Test
    void attachResultWithoutRollKeepsStateIdle() {
        // attachResult is only meaningful after startRoll succeeds; it must not disturb the guard.
        ShopTransaction t = new ShopTransaction(ShopType.RUN);
        t.attachResult(new ServerSideRollResult(null, null, Category.WEAPON, 1));
        assertEquals(ShopTransaction.State.IDLE, t.state());
        assertTrue(t.canRoll());
    }

    @Test
    void itemAnimationFinishedIsIdempotentOutOfState() {
        ShopTransaction t = new ShopTransaction(ShopType.RUN);
        t.itemAnimationFinished(); // no-op while IDLE
        assertEquals(ShopTransaction.State.IDLE, t.state());
        t.startRoll(Category.WEAPON);
        t.itemAnimationFinished();
        t.itemAnimationFinished(); // no-op now that we are ROLLING_RARITY
        assertEquals(ShopTransaction.State.ROLLING_RARITY, t.state());
    }

    @Test
    void rarityAnimationFinishedIsIdempotentOutOfState() {
        ShopTransaction t = new ShopTransaction(ShopType.RUN);
        t.rarityAnimationFinished(); // no-op while IDLE
        assertEquals(ShopTransaction.State.IDLE, t.state());
        t.startRoll(Category.WEAPON);
        t.rarityAnimationFinished(); // no-op while ROLLING_ITEM
        assertEquals(ShopTransaction.State.ROLLING_ITEM, t.state());
    }
}