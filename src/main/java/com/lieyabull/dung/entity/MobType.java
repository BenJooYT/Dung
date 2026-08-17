package com.lieyabull.dung.entity;

/** Enemy catalog with AI kinds and floor-scaled stats. ai drives behavior selection:
 *  1=GAPER (shamble + spit), 2=FLY (swarm + flee), 3=SPIDER (climb + leap),
 *  4=MULLIBOOM (walk + explode on death), 5=CHARGER (dash), 6=MAW (stationary ranged). */
public enum MobType {
    GAPER(1, 10, 2, 1.5, 1, "§aGaper"),
    FLY(2, 6, 1, 5.0, 2, "§eFly"),
    SPIDER(3, 8, 2, 3.5, 3, "§cSpider"),
    MULLIBOOM(4, 14, 3, 1.2, 4, "§2Mulliboom"),
    CHARGER(5, 16, 4, 7.0, 5, "§cCharger"),
    MAW(6, 12, 3, 0, 6, "§cMaw"),
    // elite variants (id >= 100 gate elite AI + guaranteed loot)
    ELITE_GAPER(101, 40, 6, 1.8, 1, "§6Elite Gaper"),
    ELITE_CHARGER(105, 70, 9, 7.0, 5, "§6Elite Charger");

    public final int id;
    public final double baseHp;
    public final double baseDamage;
    public final double baseSpeed;   // blocks/sec
    public final int ai;             // behavior selector (see above)
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
        // x5 to match the 100HP player pool (was x10); combined with 500ms i-frames this reads as
        // chunky-but-fair hits instead of binary spikes.
        return baseDamage * (1 + floor * 0.15) * 5;
    }
}