# HUD — In-Run Sidebar Scoreboard + Action Bar

## 1. Purpose
Always-on, per-player in-game heads-up display shown while inside a dungeon run. Two channels:
- **Sidebar scoreboard** (`HUD.update`, objective `dung` → `DisplaySlot.SIDEBAR`): room type, combat stats, consumable counts, gear durability, boss/locked hints, class + ability cooldown.
- **Action bar** (`HUD.sendBar`): hearts/mana/shield pips plus a transient status `hint` folded in by `DungeonInstance.refreshUI`.

It is the primary combat/status readout and is refreshed every game tick.

## 2. Files / locations
- `src/main/java/com/lieyabull/dung/ui/HUD.java` — all of it.
  - `update(...)` line 43; `sendBar(...)` line 169; `gearCondition(...)` line 133; `registerRows(...)` line 185; `setLine(...)` line 198; `truncateVisible(...)` line 207.
- `src/main/java/com/lieyabull/dung/game/DungeonInstance.java` — `refreshUI()` line 3578 (drives HUD + Tab, throttles the action bar, collects the hint suffix); `setStatus(...)` line 3446.

## 3. Structure
- `ROWS = 13` fixed rows (line 26); rows registered exactly once per board (line 53, `registerRows`), then re-painted via per-row `Team.prefix` only when the text changed (`setLine`, line 198). This is a genuinely good pattern — it avoids the classic sidebar flicker caused by tearing down/re-adding the objective every tick.
- Rows 0–12: separator, DMG/DEF, crit, reach/speed, blank, coins/keys, bombs/kills, blank, room, gear durability bar, boss/locked hint, class, ability cooldown/ready.
- Action bar: `§c♥ X/max (pct)   §b✦ mana/max   🛡 shield` + optional hint (line 169–181). Single writer — all transient status text (room locked, secret hint, etc.) is appended as a suffix in `refreshUI`, never sent from a competing `sendActionBar` call. Good design.

## 4. Strengths
- Stable row set + prefix-only re-paint eliminates flicker and needless packet spam (`setLine` early-returns when unchanged).
- Single action-bar writer avoids the classic flicker/overwrite fight between transient status messages.
- `truncateVisible` correctly keeps `§` codes that started before the cut so a 40-visible-char truncation doesn't split a color mid-line.
- Good color semantics for the gear bar (green ≥67%, yellow ≥34%, red below).
- Shows whether the player is in a corridor vs a room (`di.roomAt`) rather than trusting a stale room label.

## 5. Weaknesses / UX issues
- **Duplicated durability logic.** `gearCondition` (line 133) is a near-verbatim copy of `TabUI.durabilitySummary` (TabUI.java line 94). Any change to thresholds (0.67/0.34), bar length (10), or icons must be edited in two files and can silently diverge.
- **Raw cooldown keys leak.** In `update`, line 126: `setLine(o, 12, "§7" + cdName + " §f" + ...)` — when no class ability is on cooldown but some other ability is, it prints the **internal** cooldown key (e.g. `fireball`, `blade_storm`) instead of a friendly name. The class-ability path (lines 114–119) maps to friendly labels, but the fallback path does not — inconsistent naming in the same line.
- **Hardcoded magic numbers.** Line 60 uses `" ".repeat` + `ROWS` coupling; the separator is a hand-padded `§m` string (`"§8§m                   "`). Line 62 `TextUtil.fmt(st.damage)`; the `§f/§b` color juxtaposition for melee/magic is easy to misread as a fraction rather than "melee/magic".
- **DMG line reads ambiguously.** `§7DMG §c<melee>§f/§b<magic>` (line 62) could be read as a ratio or a range; the same melee/magic convention is repeated in the tab (TabUI line 37). No label distinguishes the two numbers.
- **Consumable slot hints are hard-coded text** ("[slot 7]", "[slot 8]", line 68–69) — fine, but the shield slot guidance ("slot 9") only exists in the tutorial banner, not in the HUD, so a player who skipped/forgot the tutorial has no on-screen clue that slot 9 is the mana-shield slot.
- **Accessibility / colorblind reliance:** the gear durability bar and DMG numbers rely purely on color/`§c`/`§e`/`§a`; no text descriptor ("Low") accompanies the colored bar, so red–green–yellow colorblind players get no secondary cue. The hearts/mana also use two colored glyphs but are additionally labeled with numeric `current/max`, which is good.
- **`sendBar` percentage integer truncation.** Line 170 `String.format("%.0f%%", ...)` fine, but line 176 `int shieldPct = (int)(st.shield / st.shieldMax * 100)` truncates (not rounds) and does the div-by-zero guard redundantly; harmless but inconsistent with the `%.0f` used for hearts.

## 6. Bugs / risks
- **Global `curRoom` used for the room/gear-adjacency hints.** Line 73 uses `di.roomAt(p.getLocation())` (good, physical), but line 90 `Floor.RoomNode cur = physicalRoom;` is correct. However line 103 `di.boss() != null` is global to the instance — in a party where members are split between rooms, every member's row 10 shows "BOSS ACTIVE" regardless of their actual room. Minor, since boss only spawns when all are present, but the locked-nearby hint (line 97) is computed from the physical room, so it's per-player — inconsistent semantics between rows 10's two branches.
- **`lastText`/`lastDisplayName` are shared mutable per-HUD but the HUD objects are per-player (map `huds`), so that's fine. `reset` unregisters objective `"dung"` but not the `h0..h12` teams** (line 38 only unregisters the objective). On `reset` then re-register, `registerRows` calls `b.registerNewTeam("h"+line)` — if the old teams still exist on the reused board (e.g. `reviveDeadPlayers` creates a **fresh** scoreboard, so OK; but `reset` is also called on the *same* board in some paths?) — if the objective is re-created on a board whose `hN` teams already exist, `registerNewTeam` throws `IllegalArgumentException` (team already exists). The death path removes the board, and `enterFloor` does not call reset on a reused board, so this is latent; but it is fragile. `TabUI.reset` (TabUI line 129) DOES clean up `d*` teams; HUD.reset does not clean up `h*` teams — asymmetric cleanup, a latent re-registration crash on a reused scoreboard.
- **No `isEmpty()` guard on `run.playerStateOf` cooldown iteration** beyond `rem > 0`, fine.

## 7. Concrete suggestions (priority order)
1. **Extract a single `durabilitySummary(...)`/`gearBar(...)` helper** shared by HUD and TabUI (small).
2. **Add a friendly-name map for ALL ability cooldown keys**, not just class abilities, so line 12 never shows internal IDs (small).
3. **Make `HUD.reset` also unregister the `h0..h12` teams**, symmetric with `TabUI.reset`, to remove the latent re-registration crash (small, safety).
4. **Add a non-color cue to the gear bar** (e.g. text "LOW" / percent) for colorblind players (medium).
5. **Label the melee/magic pair explicitly** (e.g. `DMG 32  MAG 12`) instead of `32/12` to remove the fraction misreading (medium).
6. **Add an on-screen slot-9 shield hint in the HUD** (e.g. one line) so it survives tutorial completion/skip (medium).

## 8. Shared-flaw note
The melee/magic `X/Y` convention, gear-bar thresholds/icons, and class-ability label mapping are all duplicated between HUD.java and TabUI.java — see `tab.md` §5 for the same items.
