package com.lieyabull.dung.game;

/**
 * The five physical workstations found in an UPGRADE room (every 5 floors). Each workstation is a
 * physical block in the world with a floating name tag; right-clicking it opens the corresponding
 * management GUI. The registered function ({@link #function}) drives behavior, NOT the vanilla block,
 * so the workstation block can be changed freely.
 */
public enum WorkstationType {
    UPGRADE("UPGRADE", "dung.ws.upgrade", org.bukkit.Material.SMITHING_TABLE,
            "Improve an item's core stat", "§a"),
    REFORGE("REFORGE", "dung.ws.reforge", org.bukkit.Material.GRINDSTONE,
            "Reroll an item's affixes", "§b"),
    PRESERVE("PRESERVE", "dung.ws.preserve", org.bukkit.Material.ANVIL,
            "Make an item persist past this run", "§d"),
    SALVAGE("SALVAGE", "dung.ws.salvage", org.bukkit.Material.BARREL,
            "Destroy an item for shards", "§c"),
    STORAGE("PERSISTENT STORAGE", "dung.ws.storage", org.bukkit.Material.ENDER_CHEST,
            "View persistent items (read-only in-run)", "§6");

    public final String label;
    /** Unique marker so block/callers can be identified independently of the block material. */
    public final String marker;
    public final org.bukkit.Material block;
    public final String description;
    public final String color;

    WorkstationType(String label, String marker, org.bukkit.Material block,
                    String description, String color) {
        this.label = label;
        this.marker = marker;
        this.block = block;
        this.description = description;
        this.color = color;
    }
}