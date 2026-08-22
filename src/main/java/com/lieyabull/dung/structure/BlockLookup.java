package com.lieyabull.dung.structure;

/**
 * Abstract read of a room's solid/air occupancy in <em>structure-relative</em> coordinates, so block
 * validation can run identically against a WorldEdit clipboard (at runtime) or a plain map (headless
 * tests) without coupling to either.
 */
@FunctionalInterface
public interface BlockLookup {
    /** True if the cell at structure-relative (x, y, z) is solid/occluding. */
    boolean isSolid(int x, int y, int z);
}