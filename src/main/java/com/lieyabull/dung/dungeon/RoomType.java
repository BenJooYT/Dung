package com.lieyabull.dung.dungeon;

/** Room kind used for pacing (Isaac). Kind index drives loot tables + AI difficulty. */
public enum RoomType {
    START(0, "Spawn"),
    COMBAT(1, "Combat"),
    TREASURE(2, "Treasure"),
    SHOP(3, "Shop"),
    SECRET(4, "Secret"),
    ELITE(5, "Elite"),
    BOSS(6, "Boss"),
    LOCKED(7, "Locked"),
    UPGRADE(8, "Upgrade");

    public final int kind;
    public final String label;

    RoomType(int kind, String label) {
        this.kind = kind;
        this.label = label;
    }
}