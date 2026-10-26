package com.lieyabull.dung.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for the shared durability helper — no Bukkit server needed.
 * Covers thresholds, bar shape, worst-piece selection, and both render paths.
 */
public class UiDurabilityTest {

    @Test
    void colorThresholds() {
        assertEquals("§a", UiDurability.colorFor(0.67));
        assertEquals("§a", UiDurability.colorFor(1.0));
        assertEquals("§e", UiDurability.colorFor(0.66));
        assertEquals("§e", UiDurability.colorFor(0.34));
        assertEquals("§c", UiDurability.colorFor(0.33));
        assertEquals("§c", UiDurability.colorFor(0.0));
    }

    @Test
    void barHasTenSegmentsWhenFull() {
        String full = UiDurability.bar(1.0);
        assertTrue(full.startsWith("§a"));
        assertEquals(10, full.replace("§a", "").replace("§8", "").length());
        assertTrue(full.contains("█".repeat(10)));
    }

    @Test
    void barIsEmptyAtZero() {
        String empty = UiDurability.bar(0.0);
        assertTrue(empty.startsWith("§c"));
        assertTrue(empty.contains("░".repeat(10)));
    }

    @Test
    void worstIsLowestPctNotLowestAbsolute() {
        // 10/100 (10%) is worse than 5/10 (50%) despite the smaller absolute number.
        var pieces = List.of(
                new UiDurability.Piece("A", 5, 10),
                new UiDurability.Piece("B", 10, 100));
        assertEquals("B", UiDurability.worst(pieces).orElseThrow().label());
    }

    @Test
    void hudLineShowsWorstAndCount() {
        var pieces = List.of(
                new UiDurability.Piece("Boots", 85, 85),
                new UiDurability.Piece("Chest", 12, 85));
        String line = UiDurability.hudLine("§7Gear: ", pieces);
        assertTrue(line.startsWith("§7Gear: "));
        // Worst piece identity + numbers + total count are all visible (not an averaged bar).
        assertTrue(line.contains("Chest"));
        assertTrue(line.contains("12/85"));
        assertTrue(line.contains("(2)"));
        assertTrue(line.contains("§c")); // 12/85 = 14% -> red
    }

    @Test
    void hudLineEmptyWhenNoGear() {
        assertEquals("", UiDurability.hudLine("§7Gear: ", List.of()));
    }

    @Test
    void tabLinesArePerPiece() {
        var pieces = List.of(
                new UiDurability.Piece("Boots", 85, 85),
                new UiDurability.Piece("Chest", 12, 85));
        List<String> lines = UiDurability.tabLines("§7Durability: ", pieces);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("Boots"));
        assertTrue(lines.get(0).contains("85/85"));
        assertTrue(lines.get(1).contains("Chest"));
        assertTrue(lines.get(1).contains("12/85"));
        // Same bar glyphs/thresholds as the HUD path (shared helper, cannot drift).
        assertTrue(lines.get(0).startsWith("§7Durability: §a"));
        assertTrue(lines.get(1).startsWith("§7Durability: §c"));
    }

    @Test
    void tabLinesEmptyWhenNoGear() {
        assertTrue(UiDurability.tabLines("§7Durability: ", List.of()).isEmpty());
    }

    @Test
    void slotBarIsFiveSegmentsAndVanishesAtZero() {
        String full = UiDurability.slotBar(1.0);
        assertTrue(full.startsWith("§a"));
        assertEquals(5, full.replace("§a", "").length());
        assertEquals("§a" + "█".repeat(5), full);

        String worn = UiDurability.slotBar(0.4);
        assertTrue(worn.startsWith("§6"));
        assertEquals(2, worn.replace("§6", "").length());
        assertFalse(worn.contains("░"));

        assertEquals("", UiDurability.slotBar(0.0));
    }

    @Test
    void slotRowHasLabelAndNoNumbers() {
        String line = UiDurability.hudSlotLine(new UiDurability.Piece("Chest", 12, 85));
        assertEquals("§7Chest §6█", line);
        assertFalse(line.contains("12/85"));
        assertFalse(line.contains("░"));
    }

    @Test
    void slotRowIsBareLabelWhenMissing() {
        assertEquals("§7Helmet", UiDurability.hudSlotLine(new UiDurability.Piece("Helmet", 0, 0)));
    }
}
