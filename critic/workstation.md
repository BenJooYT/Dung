# Workstation — Chest GUI (UPGRADE / REFORGE / PRESERVE / SALVAGE / STORAGE)

## 1. Purpose
A unified 45-slot (5-row) chest GUI for the five physical workstation blocks in an UPGRADE room. It lists the player's eligible gear, lets them select an item, shows an exact cost/result preview panel, and applies the operation server-side. Destructive operations (SALVAGE, PRESERVE) require a 2-click confirm. STORAGE is read-only. Opened by right-clicking a workstation block (GameListener line 462 → `openWorkstation` line 1979).

## 2. Files / locations
- `src/main/java/com/lieyabull/dung/ui/WorkstationUI.java` — all of it.
  - `openWorkstation` line 68; `makeInfo` line 124; `onClick` line 177; `renderDetail` line 243; `currentItem` line 348; `execute` line 363; `fingerprint` line 385; `onClose` line 420; `onDrag` line 427; `reopen` line 434.
- `DungeonInstance` — `workstationSlots` line 1991; `persistentSlots` line 2036; `tryUpgrade` line 2063; `tryReforge` line 2113; `tryPreserve` line 2167; `trySalvage` line 2230.
- `WorkstationType.java` for the five types + colors/labels; `WorkstationRules` for costs.

## 3. Structure
- `SIZE = 45` (line 40). Top gear slots 0–26 hold up to 27 eligible items (cloned + tagged `ACTION_SELECT` + a `_slot` index, lines 90–98). `state.guiToPlayer` maps GUI slot → player inventory slot (line 98).
- Info panel at slot 31 (or 32 for STORAGE), line 102.
- `renderDetail` (line 243) re-renders: confirm button at slot 31, back at slot 41, info at slot 32 (lines 343–345), plus the selected item's cost/result lines.
- Selection sources: (a) clicking a listed top-slot gear item (`ACTION_SELECT`, line 220), or (b) clicking a workstation-eligible item in the player's own bottom inventory (raw-slot path, lines 198–212).
- Confirm: 2-click for PRESERVE/SALVAGE (`state.confirmed`, lines 230–236); the button label flips to `§cCONFIRM AGAIN`.
- `execute` (line 363) re-validates the item is still in the slot via `fingerprint` before applying, and sets `state.busy` to block re-entry.
- Drags cancelled (line 428); all top clicks cancelled (line 182).

## 4. Strengths
- **Fingerprint re-validation before apply** (line 370–373) is excellent: it prevents a rearranged inventory from upgrading/salvaging a different item than the one shown. This is the single most important safety feature here.
- **2-click confirm for destructive ops** with a flipped button label ("CONFIRM AGAIN") is the right pattern — and it is notably absent from ShopUI's persistent spends.
- Cost/result preview is detailed and typed (upgrade current/next, reforge new affixes, preserve chance + pity, salvage value) — genuinely useful.
- Clear read-only STORAGE messaging (info panel says "read-only inside a run", line 161).
- `busy` guard prevents double-apply from double-click (line 183, 365).

## 5. Weaknesses / UX issues
- **Bottom-inventory slot mapping is fragile and likely wrong for storage vs hotbar.** Lines 198–211:
  ```java
  int rawSlot = e.getRawSlot();
  int invSize = e.getInventory().getSize();
  if (rawSlot >= invSize) {
      int playerSlot = rawSlot - invSize;
      if (playerSlot >= 0 && playerSlot < 41) { ... p.getInventory().getItem(playerSlot) ... }
  }
  ```
  This assumes the bottom (player) inventory's raw slots map **linearly** to player inventory slots 0–40. In Bukkit, the raw slots for the bottom of a chest view are in **visual order** (three storage rows shown above the hotbar row), not logical player-slot order, and `rawSlot - topSize` does not equal the player's slot index for the storage region. A player clicking an item shown in the middle "storage" rows is likely to select a **different logical slot** than the one they clicked. Because the fingerprint check only re-validates whatever is in the *computed* slot, a mis-mapped click could select — and then, after confirm, **salvage/preserve a different item** than the one clicked. The correct approach is `e.getClickedInventory() == p.getInventory()` and use `e.getSlot()` (which is already the player-inventory slot for bottom clicks), or `e.getView().convertSlot(rawSlot)`.
  - Note the `playerSlot < 41` guard means armor/offhand (36–40) are nominally reachable via the raw path even though those slots aren't shown in the visible bottom — a sign the author assumed a linear 0–40 mapping that the view doesn't guarantee.
- **Info/confirm/back slot juggling.** On first open, STORAGE puts info at 32, others at 31 (line 102); after a selection, `renderDetail` always writes confirm at 31, info at 32, back at 41 (lines 343–345). So for non-STORAGE types the info panel physically **moves** from slot 31 → 32 the first time you select an item, and for STORAGE it never moves. A panel that shifts position mid-interaction is disorienting.
- **Gear list slots 0–26 but `guiToPlayer` is sized `SIZE` (45)** (line 79) — harmless but oversized; the loop caps at `i < 27` (line 85) so slots 27+ are never populated (they get gray panes). No "this is your Xth of Y items" or scrolling indicator if a player has >27 eligible items — excess gear is silently invisible with no hint that the list is truncated.
- **Armor-first, hotbar-last ordering** (DungeonInstance lines 1991–2011) is intentional and good, but there is no visual separator or label between the "armor / bag / hotbar" groups in the GUI rows, so a player can't tell why items appear in that order.

## 6. Bugs / risks
- **The bottom-slot mapping bug above is the highest-risk finding in this file.** It is exactly the kind of off-by-one/ordering slot bug the codebase otherwise defends against (via fingerprint) but only on the *selected* slot — the wrong slot can still be selected and then acted on. **This must be verified and fixed with `e.getSlot()`/`convertSlot`.**
- **`NamespacedKey.minecraft("dung_ws")`** and `"dung_ws_slot"` (lines 91–96, 451) use the reserved `minecraft:` namespace — same anti-pattern as ShopUI; should be a plugin-namespaced key.
- **`state.busy` is never reset on a reused State.** After `execute` sets `busy=true` (line 366), the flow re-opens a *new* State next tick, so the old State is discarded — fine. But if a future path reuses the same State (e.g. a non-reopen operation) the GUI would be permanently stuck ignoring clicks.
- **`fingerprint` reads every PDC key** (lines 391–405) but only STRING/INTEGER/DOUBLE; a tag stored as another type is silently skipped — so an item differing only in an unread tag type could pass the fingerprint. Minor given gear tags are STRING/INTEGER/DOUBLE.
- **`renderDetail` sends a chat error and re-opens when the selected item vanished** (lines 246–249) — correct, but it happens with no chat context if the player closed the GUI (re-open will re-open a GUI the player just closed). Edge case: closing mid-operation triggers an unwanted re-open.
- **Gear item clones carry the `_slot` index but not the player slot** — selection relies on `state.guiToPlayer[guiIndex]`; if a player swaps inventory while the GUI is open (blocked for top, but bottom clicks are also cancelled so swaps are blocked), `guiToPlayer` stays valid. OK in practice.

## 7. Concrete suggestions (priority order)
1. **Replace the manual `rawSlot - invSize` mapping with `e.getSlot()` (or `convertSlot`) on the player inventory** for bottom selections — highest priority correctness fix (small).
2. **Anchor the info panel in one fixed slot** for all five types and stop moving it on selection (small, UX).
3. **Use plugin-namespaced keys** for `dung_ws` / `dung_ws_slot` (small, robustness).
4. **Indicate list truncation** when eligible gear exceeds 27 items (e.g. "… and N more" info line) (medium).
5. **Add group separators or small labels** between armor / bag / hotbar groups in the top list (medium, UX).
6. Reuse the 2-click-confirm pattern here as a template for ShopUI's persistent spends (cross-file, medium).

## 8. Shared-flaw note
- `minecraft:` PDC keys — same as ShopUI.
- Cost annotation in lore/name tags (workstation name tags, DungeonInstance lines 988–1030) duplicates the "cost in lore" convention used by ShopUI; both are hand-built `§` strings with no shared formatter.
- The 2-click-confirm design here is a strength that ShopUI would benefit from reusing.
