package com.lieyabull.dung.shop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure math tests for {@link RollAnimationMath} — no Bukkit dependencies. */
public class RollAnimationMathTest {

    @Test
    void resultLandsInWindowCenterOnFinalFrame() {
        List<String> decoys = List.of("A", "B", "C");
        String result = "★";
        int steps = 14;
        List<String> strip = RollAnimationMath.buildStrip(decoys, result, steps);
        List<List<String>> frames = RollAnimationMath.frames(strip);

        assertEquals(steps, frames.size(), "one frame per scroll step");
        assertEquals(RollAnimationMath.WINDOW, frames.get(frames.size() - 1).size(), "window width constant");
        assertEquals(result, frames.get(frames.size() - 1).get(RollAnimationMath.CENTER),
                "the result must be centered in the final frame");
        assertEquals(RollAnimationMath.CENTER, RollAnimationMath.resultWindowIndex(steps));
    }

    @Test
    void stripLengthIsStepsPlusWindowMinusOne() {
        List<String> strip = RollAnimationMath.buildStrip(List.of("A"), "B", 10);
        assertEquals(10 + RollAnimationMath.WINDOW - 1, strip.size());
    }

    @Test
    void framesSlideOneElementAtATime() {
        List<String> strip = List.of("0", "1", "2", "3", "4", "5", "6", "7");
        List<List<String>> frames = RollAnimationMath.frames(strip);
        // 8 elements, window 5 -> 4 frames
        assertEquals(4, frames.size());
        assertEquals(List.of("0", "1", "2", "3", "4"), frames.get(0));
        assertEquals(List.of("1", "2", "3", "4", "5"), frames.get(1));
        assertEquals(List.of("3", "4", "5", "6", "7"), frames.get(3));
    }

    @Test
    void tickDelaysStartFastAndDecelerate() {
        int[] d = RollAnimationMath.tickDelays(10);
        assertEquals(10, d.length);
        assertEquals(1, d[0], "first frame should be fast");
        assertEquals(8, d[d.length - 1], "last frame should be the slowest");
        for (int i = 1; i < d.length; i++) {
            assertTrue(d[i] >= d[i - 1], "delays must never decrease (slot machine decelerates)");
        }
    }

    @Test
    void tickDelaysClampToAtLeastOneStep() {
        int[] d = RollAnimationMath.tickDelays(1);
        assertEquals(1, d.length);
        assertEquals(1, d[0]);
    }

    @Test
    void buildStripRejectsEmptyDecoys() {
        assertThrows(IllegalArgumentException.class,
                () -> RollAnimationMath.buildStrip(List.of(), "B", 5));
    }
}