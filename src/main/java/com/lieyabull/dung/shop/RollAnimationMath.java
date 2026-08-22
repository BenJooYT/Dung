package com.lieyabull.dung.shop;

import java.util.ArrayList;
import java.util.List;

/** Pure math for the horizontal slot-machine animations. No Bukkit dependencies, so it is fully
 *  unit-testable. A scrolling "strip" of elements is rendered through a fixed-size window; the
 *  server-chosen result is placed in the strip so it lands in the CENTER of the window on the final
 *  frame. Tick delays ramp up toward the end to give a decelerating, slot-machine feel. */
public final class RollAnimationMath {
    /** Number of visible slots in the animation window. */
    public static final int WINDOW = 5;
    /** Window index that marks the selection (middle of the 5-slot window). */
    public static final int CENTER = 2;

    private RollAnimationMath() {}

    /**
     * Build a strip of {@code totalSteps + WINDOW - 1} elements where {@code result} sits at the
     * index that will be centered in the window after exactly {@code totalSteps} frames of
     * {@link #frames}. Decoy elements are cycled from {@code decoys}.
     */
    public static <T> List<T> buildStrip(List<T> decoys, T result, int totalSteps) {
        if (decoys == null || decoys.isEmpty()) throw new IllegalArgumentException("decoys must not be empty");
        int stripLen = totalSteps + WINDOW - 1;
        int resultIndex = stripLen - WINDOW + CENTER;
        List<T> strip = new ArrayList<>(stripLen);
        for (int i = 0; i < resultIndex; i++) {
            strip.add(decoys.get(i % decoys.size()));
        }
        strip.add(result);
        while (strip.size() < stripLen) {
            strip.add(decoys.get(strip.size() % decoys.size()));
        }
        return strip;
    }

    /** One window frame per scroll step: frame {@code i} shows strip[{@code i} .. {@code i+WINDOW-1}]. */
    public static <T> List<List<T>> frames(List<T> strip) {
        List<List<T>> frames = new ArrayList<>();
        for (int i = 0; i + WINDOW <= strip.size(); i++) {
            frames.add(new ArrayList<>(strip.subList(i, i + WINDOW)));
        }
        return frames;
    }

    /** Per-frame tick delays that start fast (1 tick) and decelerate to a slow stop. */
    public static int[] tickDelays(int totalSteps) {
        int n = Math.max(1, totalSteps);
        int[] d = new int[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / Math.max(1, n - 1);
            d[i] = Math.max(1, (int) Math.round(1 + t * t * 7));
        }
        return d;
    }

    /** The window slot index (0-based within the window) that holds the result in the last frame. */
    public static int resultWindowIndex(int totalSteps) {
        return CENTER;
    }
}