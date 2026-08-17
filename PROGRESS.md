# Dung — Room-Based Dungeon Roguelite + MMORPG Combat

Live progress page.
- **Dungeon quality bar:** The Binding of Isaac (room flow, pacing, exploration, room types).
- **Combat/itemization quality bar:** Hypixel SkyBlock (rarity, stats, scaling, abilities, mana, builds).

## Goal
Minecraft transformed into a coherent RPG: a semi-roguelike where **run gear/currency is
lost on death** but **permanent progression (coins, unlocks, class) persists**. UIs are
distributed (sidebar = live info, boss bar = encounters, chat = notifications/actions,
tab = detailed build/run/progression).

## Current status (verified working)e     
- [x] Gradle 9.7 + Paper 1.21.11 toolchain; plugin jar builds (`Dung-1.0.0.jar`).
- [x] **Headless server boot verified:** `[Dung] Dung enabled.` + `Done (12.790s)!`, no exceptions.
- [x] Core lifecycle: `/dung start`, floor generation, room building, descend, leave.
- [x] Isaac-style floor gen: random-walk branching, BFS-farthest = BOSS, shop/treasure/elite/secret placement.
- [x] MMORPG stats: DMG/DEF/CRIT/fire-rate recomputed from gear SkyBlock-style.
- [x] Rarity (COMMON→MYTHIC) with stat multipliers + colored lore; gear via ItemPool.
- [x] Weapons + armor in 4 slots; abilities gated by mana + cooldown.
- [x] Enemies (Gaper/Fly/Spider/Mulliboom/Charger/Maw + elite variants) with distinct AI: Gaper spits,
      Fly swarms+ flees, Spider leaps, Mulliboom explodes on death, Charger dashes, Maw shoots.
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

### Iteration 14 - mid-run party-system bug fixes
- [x] **Quit/leave/kick no longer end everyone's run:** `DungeonInstance.removePlayer(Player)` now removes
      only that member (state, gear display, tab, playerRoom) and calls `endRun()` only when the party
      empties. `GameListener.onQuit`, `/party leave`, `/party kick`, and `/party disband` are wired so the
      party membership is mutated before instance removal (correct emptiness detection).
- [x] **Split-room softlock + enemy duplication fixed:** per-player room tracking (`playerRoom` map +
      `spawnedRooms` set) replaces the single global `curRoom` for combat. `onPlayerMoved` records each
      member's room; `tick` clears/enemies-tick every occupied room (deduped); `registerAttack` hits the
      attacker's own room. A room's enemies now spawn exactly once (guard prevents re-entry dup).
- [x] **Empty-room softlock fixed:** `spawnEnemies` returns whether enemies actually spawned; a room is
      only locked when the spawn succeeded (no reference player can no longer trap a room open forever).
- [x] **Mid-run join blocked:** `/party invite`/`accept` now refuse while either party is mid-run, so a
      stateless joiner can't be counted in boss scaling/spawns.
- [x] **Boss bar viewer leak fixed:** `BossController.removeViewer` added and called on member death/leave.

### Iteration 15 — descend + boss-clear for any member, all-members room seal, party teardown audit
- [x] **Non-leader / non-global descend fixed:** `descend()` checked the single global `curRoom` (which any
      member could clobber), so a member in any other room got "Defeat the boss first!" even after the boss
      died. It now checks `run.floor.boss.cleared`. Also `onBossDefeated` now marks the actual boss room
      cleared via a captured `bossRoom` field (was `curRoom` — a moving member could keep it unmarked).
- [x] **All-members room seal:** a COMBAT/ELITE (and BOSS) room now only seals + spawns its enemies once
      EVERY online party member is inside it (`allMembersInRoom` gate) — fights start together; members
      still outside get a "won't seal until everyone is inside" hint.
- [x] **Party teardown audit (verified):** leave/kick/disband fully detach the leaving player(s) from
      `playerInstance` + party, so ex-members can immediately create/join a party and `/dung start` again.
      Leave/kick clean via `removePlayer`→`removePlayerFromInstance`; disband ends the run and
      `removeInstance` clears every member (the `Party` object still holds members after disband).

### Iteration 16 — hit-target caps, party-scaled mobs & log warning fix
- [x] **Basic swings hit a few, not everyone:** a melee swing now damages only the nearest 3 enemies
      within reach (sorted by distance) instead of every mob in the room.
- [x] **Skills target per their description:** added a shared `hitTargets`/`inCone` helper and gave each
      ability a target limit matching its lore — Slash=1 ("a quick, heavy strike ahead"), Cleave=3 (cone),
      Smash=3 (nearby blast), Blade Storm=4 (around you), Arcane Bolt=3 (line), Ravage=all ("devastate
      every enemy in the room"). Rush stays mobility-only.
- [x] **Dungeons compensate for party size:** boss HP already scaled with party size; now regular mobs do
      too. Enemy count multiplies by party size and enemy HP gains a `+30%` per extra member (`hpMult` on
      `Enemy`), so a group run is harder, not just faster. Solo play is unchanged.
- [x] **Log spam fixed:** `ChatUI.notify(String)` passed legacy `§` pickup text to `Component.text()`,
      throwing `LegacyFormattingDetected` (the same bug already fixed for `TabUI`) — every heart/coin/bomb
      pickup dumped a ~40-line stacktrace. It now deserializes via `LegacyComponentSerializer.legacySection()`.

### Iteration 17 — held armor no longer grants stats
- [x] **Equipping armor in-hand is cosmetic:** `PlayerState.recomputeStats` read the mainhand item's
      `HEALTH`/`REACH`/`RARITY` tags regardless of item kind, so *holding* an armor piece (which carries
      those tags) silently granted its health + rarity crit without it being equipped — misleading and
      abusable (carry your best armor for free HP/crit). Mainhand stats now only apply when the held item
      is a real weapon (`dung.kind == "weapon"`); armor contributes only from the 4 armor slots.

### Iteration 18 — return to pre-run location on leave/death
- [x] **`/dung leave` and mid-run death teleport you back to where you were before starting the run**
      (previously they dumped you at the dungeon world spawn). `startRun` now records each member's
      location (`returnLocs`); a new `teleportOut` uses it on death, `/dung leave`/kick, and when the run
      ends (disband/last member), falling back to the dungeon spawn only if no saved spot exists.

### Iteration 19 — console cleanup for a stuck boss HP bar
- [x] **`/dung bossbar`** (console or op) iterates all `KeyedBossBar`s keyed `dung_boss_*` and removes
      them — a fast fix for a boss bar stuck on screen after a leaked/aborted boss fight, without a
      server restart.

### Iteration 20 — template rotation + height-aligned corridors
- [x] **Template rooms rotated to match door directions:** `resolveTemplates` now determines the
      required rotation (0&deg;/90&deg;/180&deg;/270&deg; CW) to align a template's connectors with
      the room's open door directions, then stores a rotated copy on the room node. Previously,
      templates were used as-authored — if their connector directions didn't match the room's doors,
      the room fell back to procedural generation. Now a template with NORTH+EAST connectors can be
      rotated 90&deg; to serve a room needing EAST+SOUTH doors.
- [x] **Height-aligned corridor carving:** `carveMixedCorridors` now spans the full vertical range
      between template and procedural rooms — from the lower of the two floors to the higher of the
      two ceilings — so a multi-level template (e.g., `combatfloored1` with connectors at floorY=0
      and floorY=3) connects correctly to a standard procedural room (floor at BASE_Y, ceiling at
      BASE_Y+ROOM_HEIGHT). The wall punching on the procedural side also spans the full corridor
      height.
- [x] **`RoomTemplateRotator` utility:** New class that creates a rotated copy of a template,
      transforming blocks, connectors, bounds, markers, and spawn floors around the template's
      vertical center axis using double-precision arithmetic for symmetric results on odd-dimensioned
      templates.

### Iteration 22 — undo unified corridor carving + custom rooms toggle
- [x] **Unified corridor carving reverted:** `RoomGen.build` once again carves corridors for
      procedural rooms (as it did before Iteration 21). The `carveAllCorridors` method in
      `DungeonInstance` has been replaced with the original `carveMixedCorridors` that only handles
      template↔template and template↔procedural pairs. Procedural↔procedural corridors are carved
      by each room's own `RoomGen.build` call, so overlapping corridors merge naturally.
- [x] **Custom rooms config toggle:** Added `custom-rooms: true` to `config.yml`. When set to
      `false`, `resolveTemplates` is skipped entirely and all rooms are built procedurally.
- [x] **`/room toggle` command:** Toggles the `custom-rooms` config setting and saves it to
      `config.yml`. Players are notified whether custom rooms are enabled or disabled for new
      dungeon floors. Only affects new floor generation, not existing runs.

### Iteration 23 — enemy redesign: matching entity types + distinct AI behaviors
- [x] **Entity types now match mob names:** Gaper→Zombie, Fly→Bee, Spider→Spider, Mulliboom→Creeper,
      Charger→Ravager, Maw→Warden. Each mob now uses a Minecraft entity that visually matches its name
      and role, instead of the old mismatches (Spider was a Phantom, Fly was a Phantom, Maw was a Blaze,
      Charger was a Pig).
- [x] **Distinct AI behaviors per mob type:**
  - **Gaper** (ai=1): Shambles slowly toward the player. Occasionally stops to spit a short-range
        snowball projectile (every ~3s). Melee attack when close.
  - **Fly** (ai=2): Swarms erratically around the player in an orbit pattern, changing direction
        every 0.5-1.25s. Flees for 3 seconds when HP drops below 30%. Dive-bomb attacks when close.
  - **Spider** (ai=3): Moves faster at range, slower in melee. Leaps at the player from 2-7 blocks
        away (every ~2.5s), dealing damage on landing. Normal melee when close.
  - **Mulliboom** (ai=4): Slow walk toward player. Explodes on death dealing 1.5x damage AoE to all
        players within 3 blocks (visual explosion + flame particles, no block damage).
  - **Charger** (ai=5): Telegraphed dash attack (0.75s windup) with fast lunge. Walks slowly toward
        player while on cooldown. Melee when close.
  - **Maw** (ai=6): Stationary ranged enemy — does not move. Fires sonic boom projectiles at the
        player every ~1.75s with visual particle telegraph. Initial 1.5s delay before first volley.
- [x] **Random room spawn placement:** `placeInFov` replaced with `placeRandomlyInRoom` — enemies now
      spawn at random walkable positions throughout the room (up to 20 attempts to find a non-wall
      spot), instead of all spawning in the player's field of view. Falls back to room center.
- [x] **Updated mob composition:** `composeMobs` now includes Maw and Gaper more naturally in the
      spawn pool, with a `RANGED` fallback array for better variety.

### Iteration 24 — unified action bar (no conflicting status texts)
- [x] **All status texts now go through the single action bar writer:** Previously, 5 different places
      called `p.sendActionBar()` directly (room-locked, doors-opened, everyone-inside, warden-awaits,
      key/bomb warning), each overwriting the HP/mana counter or the secret room hint. Now all status
      texts are routed through `DungeonInstance.setStatus()` which stores a per-player transient message
      with a 3-second expiry. The `refreshUI()` tick loop collects both the secret hint and any active
      status message and passes them together as a suffix to `HUD.sendBar()` — the single owner of the
      action bar. No more competing writers, no more flickering or overwritten messages.

### Iteration 25 — 4 new weapons + Mana Shield
- [x] **Chain Lightning (Storm Rod):** Single-enemy raycast selection, primary target takes 2x damage, chains to up to 3 nearest enemies with diminishing multipliers (1.6x, 1.2x, 0.9x). Curved lightning arc particles (CRIT) drawn along a sine-wave path between chained targets. Thunder sound on cast.
- [x] **Fireball (Blaze Staff):** Launches a real `Fireball` projectile (no block damage, no fire). On impact, deals AoE 2x damage to all enemies and boss within 3 blocks. Flame/LAVA particle burst + explosion sound. Projectile tagged `dung.fireball` and handled in `GameListener.onProjectileHit`.
- [x] **Mana Shield:** New held item (SHIELD material) with `dung.kind = "shield"` — does NOT count as a weapon for stat computation. Shield capacity scales with rarity (COMMON=30 to MYTHIC=130). When sneaking with the shield in main hand, spends ~15 mana/sec to charge the shield. When not sneaking, shield decays at ~30/sec. `PlayerState.hurt()` absorbs damage with shield first before applying to hearts. Available in both in-run shop and persistent shop at lower weight than weapons/armor.
- [x] **Life Drain (Soul Siphon):** Melee attacks add 25% of damage dealt to the weapon's stored health (capped at 50). The Life Drain ability does the same as an AoE drain on all enemies in the room + boss. Right-click another player in the same run to transfer stored health as healing. Stored health shown in item lore.
- [x] **Loot wiring:** Storm Rod, Blaze Staff, Soul Siphon added to `ItemPool.WEAPONS`. Shields added via `ItemPool.randomShield()` with 25% weight in `roomReward` gear rolls (40% weapon, 35% armor, 25% shield). Shields available in both in-run and persistent shops.
- [x] **Persistent item star marker:** `GearFactory.markPersistent()` now prepends a ★ star emoji to the display name of persistent items, making them visually distinct in inventories.

### Iteration 26 — Mulliboom explosion buff, magic damage separation, magic damage upgrade
- [x] **Mulliboom explosion damage tripled:** `damage * 1.5` → `damage * 4.5` so the death explosion is a meaningful threat instead of a minor nuisance.
- [x] **Magic weapons deal 1 melee damage:** Storm Rod, Blaze Staff, and Soul Siphon now have a separate `dung.magic_damage` PDC tag. Their melee damage is set to 1 (negligible basic attacks), while their ability damage uses the magic damage value. `dispatchAbility` and `dispatchClassAbility` (Mage's Arcane Nova) check for `st.magicDamage` and use it for magic-based abilities (Arcane Bolt, Chain Lightning, Fireball, Life Drain).
- [x] **Separate magic damage upgrade:** Added `MAGIC_DAMAGE` track to `Upgrades.java` (base cost 8, +4/level, cap 15, delta +1/level). Wired into `PlayerState.applyUpgrades()` so permanent magic damage upgrades apply on top of gear.
- [x] **New items verified in loot pools:** Storm Rod, Blaze Staff, and Soul Siphon are in `ItemPool.WEAPONS` so they drop from `randomWeapon()` which is used by `roomReward()` (treasure rooms, elite rooms, boss rooms, etc.) and both the in-run and persistent shops. Shields are in `randomShield()` which is wired into `roomReward()` at 25% weight and both shops.

### Iteration 27 — "Try to Persist" upgrade rooms
- [x] **UPGRADE room type:** new `RoomType.UPGRADE`, guaranteed on every 5th floor (5, 10, 15, …). It
      replaces a deepest combat room (kept in the door graph so it stays reachable) and gets its own
      amethyst/purpur visual theme.
- [x] **Persist Master:** an UPGRADE room spawns a passive named Villager ("Persist Master", once per
      room per floor) that opens a chest GUI on right-click. It lists every persistable run item the
      player is carrying (weapon/armor/shield run gear, excluding persistent and starter-kit gear).
- [x] **Try-to-persist cost & odds:** attempting to persist an item costs **50 run coins + 200 persistent
      coins + 300 shards**. **40% success** → the item is queued and delivered after the run ends as
      persistent gear at **half durability**. **60% fail** → the item is returned immediately, **one
      rarity worse** (stats scaled down by the rarity multiplier, display recolored, lore rewritten).
- [x] **Persistent delivery:** successfully-persisted items are held in a per-player queue and given to
      the player's inventory when the run ends (or if they leave early), never lost to death.
- [x] **`GearFactory` helpers:** `persistize` (mark persistent + init durability at half) and
      `downgradeRarity` (scale stat tags + rewrite lore to the next-lowest rarity).

### Remaining candidate work
- [ ] **Status effects** — Poison (DoT), Slow, Weakness, Stun on both players and enemies. Weapons/
      abilities could apply them; enemies could apply them on hit. Adds strategic depth to combat.
- [ ] **More room shapes** — Cross-shaped, ring/circular, bridged (gap to jump), multi-level platforms,
      hazard rooms (lava/fire patches). The `RoomGen` shape-building system supports adding new layouts.
- [ ] **More boss variety** — A mage boss (teleports + homing projectiles), swarm boss (spawns minions),
      tank boss (damage gates). Each tied to a floor range for variety across deep runs.
- [ ] **Persistent gear upgrades** — Enchanting/reforging/repair for persistent gear using shards/coins.
      Gives players something to grind toward with their persistent currency.
- [ ] **Floor-specific biomes/themes** — Nether brick (floors 3-4), End stone (5-6), deepslate (7+).
      Each with different enemy distributions and visual identity.
- [ ] **Leaderboards / statistics** — `/dung leaderboard` for best floor, clears, kills. Personal
      stats page with more detail. Mostly UI work on existing `MetaManager` data.
- [ ] **Class-specific passives/active balance** beyond the three defaults.
- [x] **Room editor tutorial** — `/room tutorial` walks through building, capturing, validating,
      exporting, and testing a room template step by step. Auto-advances when the player runs the
      expected command. Replayable any number of times.
- [ ] **Room editor authoring/test/export polish** (Iterations 11-13 shipped the subsystem; generate-template
      floor mode, editor CLI, tests, and registry are in place).

## Build / run
```
gradlew build            # compiles + jars
gradlew runServer        # boots a Paper 1.21.11 server with the plugin
```
Commands: `/dung start|descend|leave|stats|class|give|help` `/party create|invite|accept|decline|leave|kick|disband` `/shop` (opens GUI) `/upgrades` (opens GUI) `/salvage [all|favorite]` `/plots [warp <name>]` `/plot claim|home|name|warp|unclaim`
