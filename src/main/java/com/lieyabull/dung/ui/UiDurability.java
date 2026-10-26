package com.lieyabull.dung.ui;

import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.lang.Lang;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Single shared durability helper for HUD + Tab.
 *
 * <p>Both UIs scan the same source (mainhand + offhand + 4 armor + hotbar shield slot 9 when
 * it is not the held slot) via {@link #collect}, then render from the same {@link Piece} list:
 * HUD shows the worst piece + count (one live row), Tab shows one row per piece (detail).
 * Thresholds (0.67/0.34), bar length (10), and glyphs live here only so the two UIs cannot drift.
 */
public final class UiDurability {
    /** One damageable gear piece: display label + current/max durability. */
    public record Piece(String label, int cur, int max) {
        public double pct() {
            return pctOf(cur, max);
        }
    }

    private UiDurability() {}

    public static double pctOf(int cur, int max) {
        if (max <= 0) return 1.0;
        return (double) cur / max;
    }

    /** Bar color by threshold (green >= 67%, yellow >= 34%, red below). */
    public static String colorFor(double pct) {
        if (pct >= 0.67) return "§a";
        if (pct >= 0.34) return "§e";
        return "§c";
    }

    /** 10-segment colored bar for a 0..1 fraction. */
    public static String bar(double pct) {
        return bar(pct, 10);
    }

    /** Colored bar with explicit length (pure, headless-testable). */
    public static String bar(double pct, int len) {
        double clamped = Math.max(0.0, Math.min(1.0, pct));
        int filled = (int) Math.round(clamped * len);
        int empty = len - filled;
        StringBuilder sb = new StringBuilder(colorFor(pct));
        sb.append("█".repeat(Math.max(0, filled)));
        if (empty > 0) sb.append("§8").append("░".repeat(empty));
        return sb.toString();
    }

    /** Lowest-pct piece, empty when the list is empty. */
    public static Optional<Piece> worst(List<Piece> pieces) {
        if (pieces == null || pieces.isEmpty()) return Optional.empty();
        Piece w = null;
        double wp = Double.MAX_VALUE;
        for (Piece pc : pieces) {
            double p = pc.pct();
            if (p < wp) {
                wp = p;
                w = pc;
            }
        }
        return Optional.ofNullable(w);
    }

    /**
     * HUD live row: worst piece + count, e.g. {@code §7Gear: §c███... §7Chest 12/85 §8(6)}.
     * Returns "" when there is no damageable gear so the caller clears the row.
     */
    public static String hudLine(String gearPrefix, List<Piece> pieces) {
        Optional<Piece> w = worst(pieces);
        if (w.isEmpty()) return "";
        Piece p = w.get();
        return gearPrefix + bar(p.pct()) + " §7" + p.label() + " " + p.cur() + "/" + p.max()
                + " §8(" + pieces.size() + ")";
    }

    /**
     * Tab detail rows: one per piece, e.g. {@code §7Durability: §a███... §7Boots 85/85}.
     * The caller prepends its own indent; the durability prefix must come from the
     * {@code tab.durability} Lang key (never hardcoded English).
     */
    public static List<String> tabLines(String durabilityPrefix, List<Piece> pieces) {
        if (pieces == null || pieces.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(pieces.size());
        for (Piece pc : pieces) {
            out.add(durabilityPrefix + bar(pc.pct()) + " §7" + pc.label() + " " + pc.cur() + "/" + pc.max());
        }
        return out;
    }

    /** 5-segment per-slot durability bar: green while healthy, orange as it wears, then the
     *  remaining squares disappear (no empty placeholders, no numbers). */
    public static String slotBar(double pct) {
        double clamped = Math.max(0.0, Math.min(1.0, pct));
        int filled = (int) Math.ceil(clamped * 5.0);
        if (filled <= 0) return "";
        String color = clamped >= 0.6 ? "§a" : "§6";
        return color + "█".repeat(filled);
    }

    /**
     * Fixed HUD durability rows in display order: Hand, Helmet, Chest, Legs, Boots.
     * A missing or non-damageable slot still yields a Piece (max 0) so the row set never
     * shifts around as gear changes.
     */
    public static List<Piece> hudSlots(org.bukkit.inventory.PlayerInventory inv, org.bukkit.entity.Player p) {
        List<Piece> out = new ArrayList<>(5);
        if (inv == null) return out;
        String hand = p == null ? "Hand" : Lang.forPlayer(p, "hud.hand");
        String helmet = p == null ? "Helmet" : Lang.forPlayer(p, "tab.armor.helmet");
        String chest = p == null ? "Chest" : Lang.forPlayer(p, "tab.armor.chest");
        String legs = p == null ? "Legs" : Lang.forPlayer(p, "tab.armor.legs");
        String boots = p == null ? "Boots" : Lang.forPlayer(p, "tab.armor.boots");
        out.add(slotPiece(inv.getItemInMainHand(), hand));
        ItemStack[] armor = inv.getArmorContents();
        // Bukkit armor order is [boots, leggings, chestplate, helmet]; display top-to-bottom.
        String[] labels = {helmet, chest, legs, boots};
        for (int i = 0; i < labels.length; i++) {
            ItemStack s = armor != null && armor.length > (3 - i) ? armor[3 - i] : null;
            out.add(slotPiece(s, labels[i]));
        }
        return out;
    }

    /** One HUD slot row: bare label when empty, otherwise label + 5-segment bar (no numbers). */
    public static String hudSlotLine(Piece pc) {
        if (pc == null) return "";
        String base = "§7" + pc.label();
        return pc.max() > 0 ? base + " " + slotBar(pc.pct()) : base;
    }

    private static Piece slotPiece(ItemStack s, String label) {
        if (s == null || s.getType().isAir()) return new Piece(label, 0, 0);
        int d = GearFactory.getDurability(s);
        int m = GearFactory.getMaxDurability(s);
        if (d < 0 || m <= 0) return new Piece(label, 0, 0);
        return new Piece(label, d, m);
    }

    /**
     * Scan hands + armor + shield slot 9 (slot index 8) when it is not the held slot.
     * Labels: armor/shield are localized via Lang so HUD worst labels match Tab per-piece
     * labels; hands use plain English slot ids (no Lang key exists yet — keep as stable ids).
     */
    public static List<Piece> collect(org.bukkit.inventory.PlayerInventory inv, org.bukkit.entity.Player p) {
        List<Piece> out = new ArrayList<>(7);
        if (inv == null) return out;
        String shieldLabel = p == null ? "Shield" : Lang.forPlayer(p, "item.mana_shield");
        String boots = p == null ? "Boots" : Lang.forPlayer(p, "tab.armor.boots");
        String legs = p == null ? "Legs" : Lang.forPlayer(p, "tab.armor.legs");
        String chest = p == null ? "Chest" : Lang.forPlayer(p, "tab.armor.chest");
        String helmet = p == null ? "Helmet" : Lang.forPlayer(p, "tab.armor.helmet");
        addIfDurable(out, inv.getItemInMainHand(), "Mainhand");
        addIfDurable(out, inv.getItemInOffHand(), "Offhand");
        ItemStack[] armor = inv.getArmorContents();
        // Bukkit armor order: [boots, leggings, chestplate, helmet] — matches TabUI's armorSlot index.
        String[] armorLabels = {boots, legs, chest, helmet};
        for (int i = 0; i < armorLabels.length && i < armor.length; i++) {
            addIfDurable(out, armor[i], armorLabels[i]);
        }
        // Shield parked in hotbar slot 9 still takes durability damage while stored, so include
        // it when it is not already counted as the held mainhand item.
        try {
            if (inv.getHeldItemSlot() != 8) {
                addIfDurable(out, inv.getItem(8), shieldLabel);
            }
        } catch (Exception ignored) {
            // Mocked inventories in headless tests may not support slot indexing — hands+armor already covered.
        }
        return out;
    }

    private static void addIfDurable(List<Piece> out, ItemStack s, String label) {
        if (s == null || s.getType().isAir()) return;
        int d = GearFactory.getDurability(s);
        int m = GearFactory.getMaxDurability(s);
        if (d >= 0 && m > 0) out.add(new Piece(label, d, m));
    }
}
