# Tab — Player-List (Tab) Menu

## 1. Purpose
A second, "detailed" information layer opened with the player-list (Tab) key. The vanilla player list is repurposed via a `PLAYER_LIST` objective (`dungtab`) to show combat build stats, equipment + durability, dungeon exploration status, and class ability state. Header carries run + class. Shown continuously during a run; refreshed every 10 ticks (`tabTickCounter % 10`).

## 2. Files / locations
- `src/main/java/com/lieyabull/dung/ui/TabUI.java` — all of it.
  - `refresh(...)` line 22; `reset(...)` line 127; `team(...)` line 138; `durabilitySummary(...)` line 94; `armorSlot(...)` line 84; `countCleared(...)` line 78.
- Driven from `DungeonInstance.refreshUI()` line 3578 (refreshTab throttle line 3580) and torn down in `reset` (also called from death/leave/end paths).

## 3. Structure
- Objective `dungtab`, `Criteria.DUMMY`, `DisplaySlot.PLAYER_LIST` (lines 28–30).
- Rows built by `team(o, i++, text)` which registers team `d<i>` (reused across refreshes) and an invisible entry `"§0" + " ".repeat(index+1)` with descending score `100 - index` so rows order top→bottom (lines 138–150). Uses a different invisible color than HUD's `§8` entries specifically to avoid entry/team collisions on the shared per-player board (comment lines 143–145).
- Rows: header (line 32), DMG/DEF/CRIT, Mana/Speed/FireRate, coins/keys/bombs, blank, `Equipment`, mainhand, 4 armor rows, durability summary, blank, `Dungeon` (explored/cleared + boss status), class ability + cooldown, controls hint.

## 4. Strengths
- Careful about the shared-scoreboard entry collision problem — deliberately uses `§0` invisible entries so Tab rows never collide with HUD's `§8` entries on the same per-player board (this is a real Paper gotcha handled correctly).
- Reuses team objects across refreshes (`getTeam` then `setPrefix`) instead of registering new teams each tick.
- Header includes class + floor at a glance.
- Durability summary with numeric `total/max` is informative.

## 5. Weaknesses / UX issues
- **Duplicated content/constants with HUD.** `durabilitySummary` (line 94) duplicates `HUD.gearCondition` (HUD line 133) byte-for-byte in logic (thresholds 0.67/0.34, 10-segment bar, `█`/`░`). The class-ability label switch (lines 61–66) duplicates HUD lines 114–119. The melee/magic `X/Y` line (line 40) duplicates HUD line 62 — including a double color code `§fCRIT §f` (line 40) where the first `§f` is dead.
- **`§fCRIT §f` redundant code** (line 40) — cosmetic but sloppy; also `CRIT` label prints a literal `x` multiplier with no space ("x2.0").
- **Boss status uses global `curRoom`** (line 58): `di.curRoom().type.name().equals("BOSS")`. In a party, a member in a different room sees "BOSS AWAITING" incorrectly, while a member physically in the boss room on a different floor state sees stale info. Should use the player's physical room like HUD does.
- **Offhand equipment omitted.** Line 46 shows mainhand + 4 armor but never the offhand, while HUD's gear-consistency check includes offhand. Inconsistent picture of loadout.
- **Rows are not cleared when the row count shrinks.** `team(...)` only ever sets/creates `d<i>`; if a refresh produces fewer rows than a previous one (e.g. `st == null`, or durability summary disappears), stale `d<i>` rows keep their prefix + score and remain visible. Since refreshes always append from `i=0` and the player-list shows by score, leftover rows from a longer previous frame can linger. (`refresh` returns early without clearing when `st == null`, leaving the last frame fully on screen.)
- **Boss state string building is fragile**: `(di.curRoom() != null && di.curRoom().type.name().equals("BOSS") ? ...)` uses `name()` (enum name) rather than a type compare / the existing `label`, easy to break if an enum is renamed.
- No `HUD.resetLastText`-style guard means the tab always re-sends prefixes every 10 ticks even when nothing changed (minor packet waste; the tab is throttled so acceptable, but the HUD does better with its diffing).

## 6. Bugs / risks
- **`reset` cleans ALL teams starting with `d`** (line 130: `if (t.getName().startsWith("d"))`). On the per-player board this is fine today because HUD uses `h<i>` names, but it is a hidden coupling: any future team whose name starts with `d` (e.g. a new HUD feature, or `dungtab`-like) would be silently destroyed. Prefer a dedicated prefix like `tab<i>`.
- **Score leak across runs**: `reset` iterates `getTeams()` and calls `board.resetScores(entry)` then `unregister`, which is thorough; good. But it only runs on the explicit reset paths — if a run ends via a path that skips reset (none found in the death/leave/end flows, which all call `reset`), stale tab rows would persist. Not currently hit.
- `team()` calls `getTeam(name)` on `o.getScoreboard()` then `addEntry` every refresh; `addEntry` on an existing team is idempotent, fine.

## 7. Concrete suggestions (priority order)
1. **Pull the durability bar and class-ability labels into shared helpers** (with HUD) so the two UIs cannot drift (small).
2. **Compute boss status from the player's physical room** (like HUD) instead of global `curRoom`; compare via `RoomType` not `name()` (small).
3. **Add the offhand item** to the Equipment section for a complete loadout (small).
4. **Clear `d<i>` rows beyond the current frame's row count** at the start of each refresh so stale rows can't linger (medium).
5. **Rename `reset` team-prefix filter to a dedicated prefix** (e.g. `tab<i>`) to remove the hidden coupling to HUD's naming (medium, robustness).
6. Fix the redundant `§fCRIT §f` code and space out the crit multiplier (cosmetic, small).

## 8. Shared-flaw note
Melee/magic `X/Y`, durability bar thresholds/icons, and class-ability labels are duplicated between TabUI.java and HUD.java — cross-referenced in `hud.md` §5/§8.
