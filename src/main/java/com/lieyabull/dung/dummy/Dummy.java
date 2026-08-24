package com.lieyabull.dung.dummy;

import java.util.List;

/**
 * Plain data holder for a stationary clickable dummy NPC. The live entities
 * (armor stand / interaction hitbox / text display) are managed by {@link DummyManager};
 * this class only carries the persisted state.
 */
public final class Dummy {
    public String worldName;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    /** Display name lines (legacy § codes allowed), rendered on the riding TextDisplay. */
    public List<String> nameLines;
    /** Command executed (as the clicking player) on left click; nullable/empty = nothing. */
    public String leftCommand;
    /** Command executed (as the clicking player) on right click; nullable/empty = nothing. */
    public String rightCommand;
    /** Player name whose skin the dummy wears (nullable = plain invisible-stand look). */
    public String avatar;
    /** Item shown in the dummy's main hand (nullable = empty hand); persisted. */
    public org.bukkit.inventory.ItemStack handItem;

    public Dummy(String worldName, double x, double y, double z, float yaw, float pitch,
                 List<String> nameLines, String leftCommand, String rightCommand) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.nameLines = nameLines;
        this.leftCommand = leftCommand;
        this.rightCommand = rightCommand;
    }

    /** Raw multi-line name joined back together with the "/r" separator used in commands. */
    public String rawName() {
        return String.join("/r", nameLines);
    }
}
