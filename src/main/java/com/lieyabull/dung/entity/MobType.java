package com.lieyabull.dung.entity;

/** Enemy catalog with AI kinds and floor-scaled stats. kind drives behavior selection. */
public enum MobType {
    GAPER(1, 10, 2, 2, 1, "§aGaper"),
    FLY(2, 6, 1, 4, 1, "§eFly"),
    SPIDER(3, 8, 2, 5, 2, "§cSpider"),
    MULLIBOOM(4, 14, 3, 1, 1, "§2Mulliboom"),
    CHARGER(5, 16, 4, 7, 2, "§cCharger"),
    MAW(6, 12, 3, 3, 3, "§cMaw"),
    // elite variants (kind >= 100 gate elite AI + guaranteed loot)
    ELITE_GAPER(101, 40, 6, 2, 2, "§6Elite Gaper"),
    ELITE_CHARGER(105, 70, 9, 7, 3, "§6Elite Charger");

    public final int id;
    public final double baseHp;
    public final double baseDamage;
    public final double baseSpeed;   // blocks/sec
    public final int ai;             // 1 contact,2 fly-fast (faster),3 same-as-contact,4 burst-range (2.2),5 charger-dash,6 long-range (3.2)
    public final String name;

    MobType(int id, double hp, double dmg, double speed, int ai, String name) {
        this.id = id;
        this.baseHp = hp;
        this.baseDamage = dmg;
        this.baseSpeed = speed;
        this.ai = ai;
        this.name = name;
    }

    public boolean isElite() {
        return id >= 100;
    }

    /** floor-scaled hp (per-run scaling). */
    public double hpAt(int floor) {
        return baseHp * (1 + floor * 0.5);
    }

    public double damageAt(int floor) {
        // x10 to match the 100HP player pool (was balanced around 10 HP)
        return baseDamage * (1 + floor * 0.15) * 10;
    }
}