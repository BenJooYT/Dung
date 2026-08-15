# Dung — Room-Based Dungeon Roguelite + MMORPG Combat

Live progress page.
- **Dungeon quality bar:** The Binding of Isaac (room flow, pacing, exploration, room types).
- **Combat/itemization quality bar:** Hypixel SkyBlock (rarity, stats, scaling, abilities, mana, builds).

## Goal
Minecraft transformed into a coherent RPG: a semi-roguelike where **run gear/currency is
lost on death** but **permanent progression (coins, unlocks, class) persists**. UIs are
distributed (sidebar = live info, boss bar = encounters, chat = notifications/actions,
tab = detailed build/run/progression).

## Current status (verified working)
- [x] Gradle 9.7 + Paper 1.21.11 toolchain; plugin jar builds (`Dung-1.0.0.jar`).
- [x] **Headless server boot verified:** `[Dung] Dung enabled.` + `Done (12.790s)!`, no exceptions.
- [x] Core lifecycle: `/dung start`, floor generation, room building, descend, leave.
- [x] Isaac-style floor gen: random-walk branching, BFS-farthest = BOSS, shop/treasure/elite/secret placement.
- [x] MMORPG stats: DMG/DEF/CRIT/fire-rate recomputed from gear SkyBlock-style.
- [x] Rarity (COMMON→MYTHIC) with stat multipliers + colored lore; gear via ItemPool.
- [x] Weapons + armor in 4 slots; abilities gated by mana + cooldown.
- [x] Enemies (Gaper/Fly/Spider/Mulliboom/Charger/Maw + elite variants) with distinct AI.
- [x] Boss: Warden with HP bar, dash + AoE patterns, arena lock, descend on defeat.
- [x] Semi-roguelike: death loses run, persistent coins/deaths/clears/class in `saves.yml`.
- [x] UI: sidebar (HP/mana/coins/keys/bombs/room), boss bar, tab menu (build+dungeon), clickable chat.

## Known gaps / next iteration (from critic loop)
### Iteration 2 — fixed (both critics' blocking issues)
- [x] **Unwinnable fix:** added `PlayerMoveEvent` room-transition wiring (`onPlayerMoved` → `enterRoom`),
      adjacent-gated. Combat/boss/descend now actually trigger.
- [x] **Real door lock/unlock:** combat rooms seal with barrier blocks on entry, open on clear
      (was permanent air holes).
- [x] **Room confinement:** only the current room's enemies tick; gone is global enemy leak.
- [x] **Melee actually connects:** hit test moved to each enemy's Y plane (horizontal+vertical
      reach) — swords can finally damage grounded mobs.
- [x] **Defense now real:** PlayerState is the single source of truth; HP synced each tick
      (was two divergent health systems).
- [x] **Mana regenerates** each tick.
- [x] **Distinct weapon abilities:** id/cost stored in PDC; Rush/Cleave/Smash/Blade Storm/
      Arcane Bolt/Ravage each change how you fight; cast via sneak+right-click.
- [x] **Boss telegraphs:** dash + slam now wind up 12/14 ticks with particles before damaging.
- [x] **Branching floors:** added fork/dead-end pass; floor-gen verified 500/500 floors connected,
      boss reachable, treasure/shop/elite guaranteed (offline JVM harness).
- [x] **In-room shop:** emerald block spends run coins on gear (per-floor cap).

### Iteration 3 — build depth + multi-phase boss + secrets
- [x] **Build depth:** rarity now adds crit chance and crit damage (weapon + armor); gear/class
      speed multiplier actually applied to walk speed. Stats recompute on item held/armor change.
- [x] **Multi-phase boss:** below 50% HP the Warden enrages — faster movement, shorter telegraphs,
      quicker cadence, and a radial flame burst pattern.
- [x] **Secret rooms:** placed on deep dead-end leaves with hidden high-tier loot (guaranteed weapon
      + armor) — distinct reward alcoves. Floor-gen re-verified **800/800** connected/guranteed.

### Iteration 4 — critic loop fixes (two critics reviewed the build)
- [x] **Real room passages:** doors now carve a true tunnel through each room's wall and the
      inter-room gap (aligned with `sealDoors` at the wall plane). Before this, no room was
      reachable from START — dead run loop.
- [x] **Melee unblocks:** `fireCd` now drains in `tick()` (was never decremented -> one hit/run);
      AOE abilities also reach the boss so it is never ability-immune.
- [x] **Death actually fires:** death is driven from `PlayerState.dead` (not `player.isDead()`),
      and HP is allowed to drop to 0 — permadeath/progression loop now reachable.
- [x] **Floor branching fixed:** the snake path had no 1-door leaves so branches never generated
      (room count always == target, no secrets). Now branches grow off any combat room; verified
      **1000/1000** connected, symmetric doors, exactly 1 treasure+elite each, secret ~9/10.
- [x] **No locked-room softlock:** invalid/despawned enemies are evicted so rooms always clear.
- [x] **Class actually applies:** player's saved class copied into the run `PlayerState` before
      recompute (was hardcoded warrior).
- [x] **Persistent economy wired:** boss clear banks run coins into the persistent wallet and
      saves; death persists/saves too. `/dung shop` (20-coin gate) and `give` now meaningful.
- [x] **One coin source:** room-clear no longer double-credits; boss drops real coins (not a dead
      GOLD_INGOT item).
- [x] **First-visit loot:** treasure + secret rooms gate drops on first entry (no infinite re-roll).
- [x] **Armor equip recompute:** right-click equipping armor now re-triggers stat recompute.
- [x] **Sneak+RMB canceled** so ability cast doesn't also fire the vanilla block interaction.
- [x] **Rarity flood tamed:** floor push cap lowered 0.45 -> 0.24 so deep floors stay mixed.

### Iteration 5 — Party system + parallel dungeons
- [x] **Party system:** `/party create`, `/party invite <player>`, `/party accept`, `/party decline`,
      `/party leave`, `/party kick <player>`, `/party disband`. Max 4 players per party.
- [x] **Parallel dungeon instances:** `GameManager` is now a registry of `DungeonInstance`s.
      Each party gets its own instance with its own floor, enemies, boss, and per-player state.
      Multiple parties can run dungeons simultaneously.
- [x] **Boss HP scaling:** Boss HP scales with party size (multiplier applied to base HP).
- [x] **Shared room progression:** Any party member entering a room triggers it for all.
      Room clear requires all enemies dead (any party member can kill them).
- [x] **Per-player loot/coins:** Each player gets their own drops and coins.
- [x] **Party-aware HUD/Tab:** Each player sees their own HUD with party context.
- [x] **Boss bar shared:** All party members see the boss HP bar.
- [x] **Death handling:** A player dying in a party removes them from the instance.
      If the party becomes empty, the instance ends.

### Remaining candidate work
- [x] **Log flood / "stall" root cause:** `TabUI.team` passed legacy `§`-coded strings to Adventure's
      `Component.text()`, which throws `LegacyFormattingDetected` + a 60-line stacktrace for every
      component. Since `tab.refresh` also runs each game tick, this spiked to ~2000 such warnings
      per second while in a run (40k+ in the log) — the actual cause of the frozen console / giant
      log, plus colors were never applied. Fixed via `LegacyComponentSerializer.legacySection()`.
- [x] **Restart hygiene:** `fireCd`/`tick`/`curRoom` reset in `startRun`; sealed barriers from a
      prior run are stripped on teardown so a new run (reused fixed grid coords) can't be
      ghost-softlocked. Notes: old-floor wall/floor blocks left stale (cosmetic overlap only);
      rooms small for the multi-phase boss; melee reach probe (2.2) narrower than MC swing (~3.0);
      `tab.refresh` every tick is wasteful (only HUD needs per-tick cadence).
- [ ] Truly wall-hidden secrets (bomb-through-wall) vs connected room marked secret.
- [ ] Real pedestal presentation for treasure/shop loot.
- [ ] Class-specific active abilities keyed per class (not just weapon abilities).

## Build / run
```
gradlew build            # compiles + jars
gradlew runServer        # boots a Paper 1.21.11 server with the plugin
```
Commands: `/dung start|descend|leave|shop|stats|class|give|help`
