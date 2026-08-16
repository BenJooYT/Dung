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
- [x] **In-room shop:** chest GUI with weapons, armor, consumables (hearts, mana, keys, bombs), and floor buffs.

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

### Iteration 6 — hidden secrets, locked rooms, pedestals & gear durability
- [x] **Bomb-through-wall secrets:** SECRET rooms are now fully disconnected from the door graph —
      a `secretParent` combat room gains a visible `CRACKED_STONE_BRICKS` destructible wall; a
      player right-clicks it (using 1 bomb) to blow a 3-wide hole and reveal the hidden loot.
      Floor-gen re-verified with the headless harness (simulated player clears every non-secret
      room; secrets excluded from door reachability + each wired to a parent).
- [x] **Locked rooms:** `LOCKED` rooms sit on dead-ends with an `IRON_BLOCK` door barrier; entering
      consumes 1 key from `PlayerState`, unlocks + clears the room, and spawns pedestal loot.
- [x] **Real pedestal presentation:** treasure/locked/room-clear loot now spawns on a
      `POLISHED_BLACKSTONE_SLAB` pedestal with an invulnerable, invisible item frame — claim by
      right-click (no more loose item drops). Pedestals are torn down with the run.
- [x] **Persistent gear durability:** on death, every `dung.persistent` item takes 10% max
      durability damage (min 1); a piece that breaks is removed from the inventory. Gives death a
      real persistent cost beyond losing the run.
- [x] **Tab refresh throttled:** `TabUI.refresh` now runs every 10 ticks (only the HUD action bar
      keeps a per-tick cadence) — cuts per-tick component work.
- [x] **Keys/bombs as enchanted hotbar items:** keys and bombs are now enchanted items locked into
      hotbar slots 7-8. Right-click locked doors with the key item, cracked walls with the bomb item.
      Items are synced every tick and can't be dropped or moved. Counts still tracked in PlayerState.
- [x] **Room variety:** 3 new room shapes (L-shaped, pillar, split) with per-type visual themes
      (nether for ELITE, quartz for TREASURE, wood for SHOP, mossy for SECRET).
- [x] **Melee reach aligned:** reach is now 3.0 (matching MC swing range) — was 2.2.
- [x] **Unit tests added:** `PlayerStateTest`, `PartyManagerTest`, `ItemPoolTest`, `UpgradesTest` —
      129 total tests, all passing.

### Iteration 7 — polish, durability, locked rooms, bomb secrets, pedestals & class abilities
- [x] **Log flood / "stall" root cause:** `TabUI.team` passed legacy `§`-coded strings to Adventure's
      `Component.text()`, which throws `LegacyFormattingDetected` + a 60-line stacktrace for every
      component. Since `tab.refresh` also runs each game tick, this spiked to ~2000 such warnings
      per second while in a run (40k+ in the log) — the actual cause of the frozen console / giant
      log, plus colors were never applied. Fixed via `LegacyComponentSerializer.legacySection()`.
- [x] **Restart hygiene:** `fireCd`/`tick`/`curRoom` reset in `startRun`; sealed barriers from a
      prior run are stripped on teardown so a new run (reused fixed grid coords) can't be
      ghost-softlocked. Notes: rooms small for the multi-phase boss.
- [x] **Truly wall-hidden secrets** (bomb-through-wall) vs connected room marked secret — done.
- [x] **Real pedestal presentation** for treasure/shop loot — done.
- [x] **Chest GUI shop:** In-run shop rooms and the between-run `/shop` command now open a chest GUI with multiple items (weapons, armor, hearts, mana potions, keys, bombs, floor buffs). `/upgrades` also opens a GUI with a back button to the main shop.
- [x] **Class-specific active abilities:** Warrior (War Cry — party damage boost + invuln), Mage (Arcane Nova — AoE 2x damage), Ranger (Shadow Step — teleport behind enemy + guaranteed crit). Triggered by sneak+drop (Q).
- [x] **Locked rooms:** `LOCKED` rooms sit on dead-ends with an `IRON_BLOCK` door barrier; right-click with a key item to unlock, consumes 1 key, spawns pedestal loot.
- [x] **Bomb-through-wall secrets:** SECRET rooms fully disconnected from door graph; `CRACKED_STONE_BRICKS` destructible wall on parent combat room; right-click with bomb item to blast open.
- [x] **Pedestal loot presentation:** treasure/locked/clear rewards spawn on `POLISHED_BLACKSTONE_SLAB` with invulnerable invisible item frame; right-click to claim.
- [x] **Persistent gear durability:** on death each `dung.persistent` item loses 10% max durability (min 1); broken pieces are removed from inventory.
- [x] **Keys/bombs as enchanted hotbar items:** keys (TRIPWIRE_HOOK) and bombs (TNT) locked into hotbar slots 7-8, synced every tick, can't be dropped or moved.
- [x] **Room variety:** 3 new room shapes (L-shaped, pillar, split) with per-type visual themes.
- [x] **Melee reach aligned:** reach is now 3.0 (matching MC swing range).
- [x] **Tab refresh throttled:** `TabUI.refresh` runs every 10 ticks; only HUD action bar keeps per-tick cadence.
- [x] **Unit tests added:** `PlayerStateTest`, `PartyManagerTest`, `ItemPoolTest`, `UpgradesTest` — 129 total tests, all passing.

### Iteration 8 — Plots system
- [x] **Plots system:** Separate flat world (`dung_plots`) with 16x16 plots separated by oak slab borders and 2-block-wide stone brick paths.
- [x] **Plot claiming:** `/plot claim` costs 250 shards or 150 persistent coins. Spiral search finds the next available plot.
- [x] **Starter chest:** Each claimed plot spawns a chest with 2 oak saplings, 2 water buckets, and 1 lava bucket.
- [x] **Commands:** `/plots` teleports to the plots world; `/plot claim` claims a plot; `/plot home` teleports to your plot.
- [x] **Persistence:** Plot ownership saved to `plots.yml` with atomic writes.
- [x] **Plot naming:** Players can name their plot with `/plot name <name>`. `/plot warp <name>` and `/plots warp <name>` teleport to a named plot. Tab completion shows the player's plot names.
- [x] **Plot protection:** new `PlotListener` — players may only break/place/ignite/bucket-blocks inside their own plot's buildable area; chests are owner-only; explosions are cancelled in the plots world (previously the plot boundary was purely visual and chests were lootable).
- [x] **Globally unique plot names:** `setNamePlot` now rejects a name already used by *any* player (was only checked against the caller), so no plot name can be silently overwritten/unwarpable. `getPlayerPlotNames` returns original-case names.
- [x] **Claim hardening:** plot is built before it's persisted, and the shard/coin charge is saved to `saves.yml` immediately (no crash mid-claim rollback).
- [x] **`/plot unclaim`:** abandons the plot (frees the coord + removes the starter chest) for re-claiming.
- [x] **Defensive fixes:** `plotAt` no longer NPEs on a null world; corrupt `plots.yml` backup uses the plugin logger.
- [x] **Reliable plot terrain:** replaced the fragile vanilla superflat `generatorSettings` preset (which never produced the grass/dirt layers on this Paper version) with a custom `PlotChunkGenerator` that programmatically fills each chunk: stone (y=0), dirt (y=1–50), grass (y=51) — matching `SURFACE_Y`.
- [x] **GameRule migration:** the four deprecated `GameRule` constants (`DO_MOB_SPAWNING`, `DO_DAYLIGHT_CYCLE`, `DO_WEATHER_CYCLE`, `DO_FIRE_TICK`) moved to the new `GameRules` API (`SPAWN_MOBS`, `ADVANCE_TIME`, `ADVANCE_WEATHER`, `FIRE_SPREAD_RADIUS_AROUND_PLAYER`=0) — clears the removal warnings.

### Iteration 9 — economy rebalance + ability global cooldown
- [x] **Upgrade economy rebalance:** upgraded tracks now give **less per level**, **cost more**, and
      have **higher caps** to absorb the generous per-floor income (60-100 shards, 30-60 coins/floor):
      Damage delta 2→1 (base 3/2→6/3, cap 10→15), Hearts 10→5 (4/3→8/4, cap 15), Defense 1→1
      (5/4→8/5, cap 15), Crit 1%→0.5%/lvl (4/3→8/4, cap 15), Speed 5%→3%/lvl (6/5→10/6, cap 5→8),
      Mana 10→5 (3/2→6/3, cap 15). Maxing all six now costs **2,783 shards (~28-46 floors)** vs 900.
      Crit/speed multipliers + GUI effect text updated to match.
- [x] **Ability global cooldown (GCD):** every weapon/class ability now shares a **400ms player-scoped
      GCD** (`PlayerState.GCD_KEY`/`GCD_MS`) checked before any cast. Since it is keyed to the player
      (not the weapon's ability id), rapid weapon-swapping can no longer bypass per-ability cooldowns
      and burst-stack multiple abilities. Per-ability cooldowns still apply on top; the internal GCD
      key is excluded from the HUD cooldown label.

### Iteration 10 — corridor carving, locked doors & shop rooms
- [x] **Corridor floor leak fixed:** the door-passage carve ran `t=0..spacing`, carving the 3-wide
      andesite tunnel + floor through BOTH room interiors. It now carves only the inter-room corridor
      (`t` in `[innerWallT, nextWallT]`, between the two wall faces), so corridor floors no longer
      leak into the rooms. Neighbours still merge into one continuous tunnel (same fixed `PERP_CENTER`).
- [x] **Locked doors always generated:** the IRON_BLOCK barrier was placed during each room's own
      build, so a later-built neighbour room's corridor carve overwrote it (key-opened door vanished).
      Barrier placement moved to a `placeLockedDoorBarrier` **post-pass** in `enterFloor` after every
      room is built — it shares geometry with `sealDoors`/`removeLockedDoorBarrier` so unlock clicks
      still line up.
- [x] **Shop rooms redesigned:** instead of a lone emerald block in the floor, a SHOP room now has a
      **raised emerald counter** (right-click to shop), a standing **"SHOP" sign**, and a passive
      named **Villager shopkeeper** (no AI, invulnerable) that also opens the shop on right-click.
      Spawned once per room per floor; shopkeepers are cleaned up on floor change / teardown.

### Iteration 11 — run-start gear safety, secret corridors & key/bomb dup hardening
- [x] **Persistent armor preserved on run start:** `grantStarters` checked the wrong slot — it read
      `getArmorContents()` (indexed boots→helmet) but wrote to `slots[i]` (HEAD→FEET), so an empty boot
      slot caused a cloth helmet to overwrite equipped persistent HEAD armor. It now reads/writes the
      same target slot (`inv.getItem(slots[i])`), so starter cloth only fills truly-empty armor slots.
- [x] **Secret-room corridors rebuilt:** `carveSecretPassage` previously punched a bare 3-wide air tunnel
      `t=1..spacing` through the world — cutting through the parent room's interior, its floor, and any
      unrelated walls. It now carves a proper **walled corridor** (3-wide tunnel + solid stone side walls,
      floored + roofed) between the secret's destructible-wall face and the parent combat room's near
      wall face (same `[innerWallT, nextWallT]` scheme as normal doors), then seals the secret's end with
      the CRACKED_STONE_BRICKS blast wall.
- [x] **Key/bomb duplication closed:** `onInventoryClick`/`onInventoryDrag` now cancel any click/drag
      touching a run item, so keys/bombs can never be moved out of hotbar slots 6/7 — previously moving
      one emptied the slot and `syncHotbarItems` re-created a fresh copy (counter desync → free items).
      `stripRunGear` now also scans the entire inventory + offhand for orphaned run items so no moved
      key/bomb survives death or run end into a later run.

### Iteration 12 — single-writer action bar (no fighting UIs)
- [x] **Action-bar flicker fixed:** the HUD action bar had **two competing writers** on different
      cadences — `HUD.sendBar` (hearts+mana every 5 ticks) and the "sense a hidden room" hint
      (`sendActionBar` every 20 ticks). Each overwrote the other, so the bar flickered between them
      whenever a bomb-secret was adjacent. The action bar is now owned by **one writer**: the hint is
      computed in the tick loop and folded into `HUD.sendBar(p, st, hint)` as a persistent suffix, so
      it shows stably alongside hearts+mana (no second cadence, no overwrite). Sidebar scoreboard was
      already a single-writer via `setLine`; boss bar and tab menu remain distinct surfaces.

### Iteration 13 — boss fixes (laser origin/direction + chase speed)
- [x] **Boss "core" now on the boss:** all attack origins (beam lane, slam, radial, contact sting)
      previously used the room-center `anchor`, so the laser core was pinned mid-room while the
      ZOGLIN's native AI chased the player. They now originate from `boss.getLocation()`, so the
      beam/telegraphs track the boss's actual position.
- [x] **Laser fires toward the player:** the telegraph `warnAngle` was computed as
      `atan2(centerZ - pZ, centerX - pX)` — the player→boss direction — so the beam launched away
      from the player. Flipped to `atan2(pZ - centerZ, pX - centerX)` (boss→player); the hit test
      projects the player onto that ray, so the lane now threatens the player's side of the room.
- [x] **Boss only slightly outrunnable:** the ZOGLIN's `MOVEMENT_SPEED` attribute is set to `0.12`,
      above base player walk (`0.1`) but below sprint (`~0.13`), so the Warden can run a walking
      player down but a sprinting player can still just barely escape (speed upgrades widen the gap).

### Remaining candidate work
- [ ] Class-specific passives/active balance beyond the three defaults.

## Build / run
```
gradlew build            # compiles + jars
gradlew runServer        # boots a Paper 1.21.11 server with the plugin
```
Commands: `/dung start|descend|leave|stats|class|give|help` `/party create|invite|accept|decline|leave|kick|disband` `/shop` (opens GUI) `/upgrades` (opens GUI) `/salvage [all|favorite]` `/plots [warp <name>]` `/plot claim|home|name|warp|unclaim`
