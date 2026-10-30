package com.lieyabull.dung.dungeon;

import java.util.Set;

/**
 * Parameters for one floor's layout. {@code targetLoops} is the generator's goal;
 * {@code minLoops} is the validator's hard floor. The classic profile reproduces the
 * pre-profile generator: a 9x9 grid, 7 rooms, at most 2 branches, no loops, and an
 * UPGRADE room every 5th floor. {@code archetype} is null for classic/none.
 */
public record FloorProfile(
        String id,
        int width,
        int height,
        int roomCount,
        int maxBranches,
        int targetLoops,
        int minLoops,
        int minBossDistance,
        int upgradeEveryFloors,
        FloorLayout layout,
        Archetype archetype,
        Set<FloorModifier> modifiers
) {
    public static final FloorProfile CLASSIC =
            new FloorProfile("classic", 9, 9, 7, 2, 0, 0, 0, 5,
                    FloorLayout.CLASSIC, null, Set.of());

    public static FloorProfile classic(int width, int height, int roomCount) {
        return new FloorProfile("classic", width, height, roomCount,
                CLASSIC.maxBranches(), CLASSIC.targetLoops(), CLASSIC.minLoops(),
                CLASSIC.minBossDistance(), CLASSIC.upgradeEveryFloors(),
                FloorLayout.CLASSIC, null, Set.of());
    }

    public static FloorProfile hub(int width, int height, int roomCount, int maxBranches,
            int targetLoops, int minLoops, int minBossDistance, int upgradeEveryFloors) {
        return new FloorProfile("hub", width, height, roomCount, maxBranches, targetLoops,
                minLoops, minBossDistance, upgradeEveryFloors, FloorLayout.HUB, null, Set.of());
    }

    public static FloorProfile forArchetype(Archetype archetype, int width, int height,
            int roomCount, int upgradeEveryFloors) {
        return new FloorProfile(archetype.id(), width, height, roomCount,
                archetype.maxBranches(), archetype.targetLoops(), archetype.minLoops(),
                archetype.minBossDistance(), upgradeEveryFloors,
                FloorLayout.CLASSIC, archetype, Set.of());
    }

    public FloorProfile withModifiers(Set<FloorModifier> modifiers) {
        return new FloorProfile(id, width, height, roomCount, maxBranches, targetLoops,
                minLoops, minBossDistance, upgradeEveryFloors, layout, archetype,
                modifiers == null ? Set.of() : Set.copyOf(modifiers));
    }

    /** Minimum room-to-room spacing needed by this layout. */
    public int minSpacing() {
        return layout == FloorLayout.HUB
                ? RoomGen.HUB_INTERIOR + 2 * RoomGen.WALL + 1
                : 0;
    }
}
