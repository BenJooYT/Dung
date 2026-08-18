# Room Editor — Authoring CLI + Tutorial (RoomCommand, RoomTutorial, RoomEditor)

## 1. Purpose
Admin/author-facing interface for building, validating, exporting, and testing dungeon room templates, plus a step-by-step chat tutorial that auto-advances as the author runs the right commands. This is a distinct user-facing UI (the developer/authoring surface), exposed via `/room`.

## 2. Files / locations
- `src/main/java/com/lieyabull/dung/command/RoomCommand.java` — `onCommand` line 47; `help` line 83; `newRoom` line 103; `setPos` line 122; `region` line 131; `spawnFloor` line 140; `conn` line 149; `marker` line 177; `capture` line 209; `info` line 220; `validate` line 231; `export` line 241; `test` line 262; `tutorial` line 279; `list` line 314; `toggle` line 322; `onTabComplete` line 332.
- `src/main/java/com/lieyabull/dung/room/RoomTutorial.java` — `start`/`next`/`back`/`skipTo`/`reset` lines 142–184; `onPlayerCommand` line 203 (auto-advance); `scheduleAdvance` line 263; `showStep` line 276.
- `src/main/java/com/lieyabull/dung/room/RoomEditor.java` — `openEditor` line 44; `export` line 52; `manifestText` line 68.

## 3. Structure
- Subcommands: help, new, pos1, pos2, region, spawnfloor, conn, marker, playerspawn, shopkeeper, capture, info, validate, export, test, testlocal, list, open, tutorial, toggle.
- Tutorial = ordered `STEPS` array of (title, instructions); state per player in `playerSteps` (UUID→index). `showStep` prints a box + instructions + nav hints. Auto-advance listens to `PlayerCommandPreprocessEvent` and matches exact command strings, deferring the step bump by 5 ticks (`scheduleAdvance` line 263).
- Editor world + export writes a JSON + `MANIFEST.md` into the data folder.

## 4. Strengths
- The tutorial is replayable and auto-advances on the *expected* commands, with a clear box header, step count, and explicit next/back/skip affordances.
- Validation gates export (`export` line 249 re-validates and refuses invalid rooms), and `validate` lists issues by severity — a good safety gate before bundling.
- `RoomEditor.export` writes both the JSON asset and a self-contained integration manifest (line 68), and never mutates the running JAR — well-documented and safe.
- Tab completion is provided for the main subcommands and the parameterized ones (line 332).
- The `toggle` command persists to config and clearly explains the effect (line 322).

## 5. Weaknesses / UX issues
- **`/room new` silently overwrites an in-progress session** (`s.start(id, types)`, line 117) with no warning that an existing draft is being replaced — a destructive no-confirm action.
- **`/room export` silently overwrites an existing file** (RoomEditor line 55: `new File(exportDir, tpl.id+".json")` then `Files.write`, no existence check) and re-validates but the overwrite is unconditional.
- **`conn`/`marker` argument parsing is fragile.** `conn` (lines 149–175) parses width/height with `Integer.parseInt` in a `try/catch` that emits a single generic "Bad arg" without naming the offending value; the connection is added even if the width/height parse to defaults silently when omitted (fine) but a typo like `w=abc` gives an unhelpful error.
- **`info` mixes counts with the `validated` flag** (line 227) but never shows which markers/spawns are missing; the tutorial does a better job than the actual `info` screen at explaining what remains.
- **Tutorial auto-advance uses exact string matching.** `onPlayerCommand` (line 203) compares `msg.equals("/room open")`, `msg.startsWith("/room new")`, etc. A player typing `/room open 1`, `/room  open` (double space), or `/room pos1 x` won't auto-advance, and will instead think the tutorial is broken (it silently stays). `startsWith` on some, `equals` on others — inconsistent matching rules across steps.
- **Tutorial steps with `\n` in instructions** rely on `split("\n")` (line 284) and prefix every line with the box; the multi-line instructions are long and can overflow chat width for narrow clients.

## 6. Bugs / risks
- **`scheduleAdvance` races with manual navigation.** It captures `nextStep` but re-reads the current step and only advances if `cur < nextStep` (lines 266–271). If the author manually `skipTo`'d past while a deferred advance is pending, the guard prevents regression — good. But if the author hit `next` and then a pending auto-advance fires, it can over-advance to `nextStep` even though the manual `next` already advanced — the guard `cur < nextStep` only protects when manual advance went *further*, not equal. Low severity.
- **Tutorial listeners are registered globally** (Dung.onEnable line 51) and do a hash lookup per player per command; with `playerSteps` empty for non-participants it returns `step < 0` quickly — negligible.
- **`RoomCommand` requires `dung.admin` OR OP** (line 52) and does **not** check for the editor world before `capture` (it does, line 214) — consistent. But `newRoom`/`setPos`/`region` operate on the player's current world, which need not be the editor world; an author can build in the wrong world and `capture` then rejects it (line 214) — the failure mode is understandable but the author finds out only at capture.
- **Export writes into the plugin data folder with no directory-existence guard in `export`** — `exportDir` is created in the constructor (line 33), so it exists; safe.
- **`toggle` reads/writes `custom-rooms` from the main config and saves** (line 326) — fine, but a config-edit while a run is mid-floor won't re-evaluate existing rooms (only affects `enterFloor`, line 488) — expected but undocumented.

## 7. Concrete suggestions (priority order)
1. **Warn before `/room new` and `/room export` overwrite** an existing session/file (small, safety).
2. **Unify tutorial auto-advance matching** — use a single `startsWith("/room " + sub)` with token normalization instead of a mix of `equals`/`startsWith` (small).
3. **Improve `conn` parse error messages** to name the offending argument (small).
4. **Add a "what's missing" summary to `info`** (missing marker types, missing spawn floor, etc.) so authors know what to add next (medium).
5. **Add an editor-world guard at the start of every editing subcommand** (new/pos1/region/spawnfloor/conn/marker) rather than only at capture (medium).

## 8. Shared-flaw note
Like every other surface, the editor CLI hand-builds `§`-coded messages (see `chat.md` §8). The tutorial is the one UI in the plugin with a dedicated step/tracking model and box-drawing header — a pattern other command UIs (party, leaderboard) could reuse for guided flows.
