package com.lieyabull.dung.pickup;

import com.lieyabull.dung.game.PlayerState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Applies ground pickup effects. Uses Materials as the "item identity" (no literal copying). */
public final class Pickup {
    public enum Type {
        HEART, COIN, KEY, BOMB
    }

    private Pickup() {}

    public static boolean isPickup(Material m) {
        return m == Material.RED_DYE || m == Material.GOLD_NUGGET || m == Material.TRIPWIRE_HOOK || m == Material.TNT;
    }

    public static Type typeOf(Material m) {
        switch (m) {
            case GOLD_NUGGET: return Type.COIN;
            case TRIPWIRE_HOOK: return Type.KEY;
            case TNT: return Type.BOMB;
            default: return Type.HEART;
        }
    }

    /** Apply and return true if consumed. */
    public static boolean apply(Material m, PlayerState st) {
        switch (typeOf(m)) {
            case HEART:
                st.heal(8.0); // meaningful against the 100-HP pool (was a negligible 1 HP)
                return true;
            case COIN:
                st.coins++;
                return true;
            case KEY:
                st.keys++;
                return true;
            case BOMB:
                st.bombs++;
                return true;
        }
        return false;
    }

    public static ItemStack stack(Material m) {
        return new ItemStack(m);
    }
}