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
- [x] Gradle 9.7 + Paper 1.21.11 toolchain; plugin jar builds (`Dung-1.0.1.jar`).
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

### Iteration 46 — Transformation potions for the plots world
- [x] **Transformation potion system:** New potion system for the plots world — splash potions that transform blocks using a BFS wave propagation engine.
- [x] **Forest Transmutation Elixir:** Transforms logs and leaves into random tree varieties (family-preserving: log→log, leaves→leaves) with 16 wood types equally weighted.
- [x] **Stone Transmutation Elixir:** Transforms stone/cobble/deep variants into stone variants and rare ores with weighted probabilities (diamond/emerald very rare).
- [x] **Propagation engine:** BFS outward from impact point through eligible blocks, grouped into waves by Manhattan distance, with configurable max range (12/10), max blocks (64/48), and spread probability (0.65/0.55).
- [x] **Wave animation:** Visual transformation in waves with per-wave particles and sounds via scheduled Bukkit tasks.
- [x] **Plot ownership enforcement:** Potions only work on the thrower's own plot.
- [x] **`/convert` toggle:** Per-player toggle controlling whether potions affect player-placed blocks (off by default — only natural blocks).
- [x] **ProvenanceManager:** Tracks which blocks are player-placed vs natural via BlockPlaceEvent/BlockBreakEvent.
- [x] **Shop integration:** Forest & Stone Transmutation Elixirs (55 coins each, slots 18/26) in the persistent shop. Elixir potion color matches the type (forest = green, stone = gray).
- [x] **Unit tests:** `PotionDefinitionTest` (9 tests: targets, weighted rolls, equality, empty pool), `PropagationEngineTest` (7 tests: family-preserving forest, weighted stone pool, custom definitions, PropagationResult).

### Iteration 47 — Plots world bedrock layer + thicker stone
- [x] **Bedrock layer at y=0:** `PlotChunkGenerator` now places bedrock at y=0 (was stone) for all new chunks.
- [x] **Stone layer increased to 30 blocks:** y=1..30 filled with stone (was dirt at y=1..50; previously cobblestone, now stone).
- [x] **Dirt layer reduced to 20 blocks:** y=31..50 filled with dirt (was y=1..50).
- [x] **Retroactive fill command:** `/plot filllayers` (op-only) fills bedrock+stone in all generated chunks within a 15-chunk radius, without touching builds above y=30. Only modifies y=0..30, well below the surface at y=52.
- [x] **Constants added:** `STONE_TOP_Y=30`, `STONE_BOTTOM_Y=1` in `PlotManager` for clarity.

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

### Iteration 29 — Persist pity system (guaranteed preserve after 3 consecutive failures)
- [x] **Persist pity threshold:** After 3 consecutive failed preserve attempts, the next attempt is guaranteed to succeed. The counter resets to 0 on a successful preserve.
- [x] **`WorkstationRules.PRESERVE_PITY`** — constant set to 3. `preserveGuaranteed(int)` returns true when the consecutive fail count reaches `PRESERVE_PITY - 1` (2 failures → 3rd attempt guaranteed).
- [x] **`DungeonInstance.tryPreserve`** — now tracks consecutive failures per player via the `preserveFails` map. Uses `preserveGuaranteed` to force success on the 3rd attempt. Shows pity status in the result message.
- [x] **`WorkstationUI`** — shows current fail count in the preserve detail panel: "Pity: N more fails → guaranteed" or "✦ PITY! Next attempt guaranteed!" when active.

### Iteration 28 — Unified dungeon progression workstations
- [x] **Five physical workstations replace the Persist Master NPC.** An UPGRADE room now spawns five
      workstation blocks (Smithing Table = UPGRADE, Grindstone = REFORGE, Anvil = PRESERVE, Barrel =
      SALVAGE, Ender Chest = STORAGE) on the floor with floating name tags, instead of a villager. The
      registered function determines behavior, not the block. Room guaranteed every 5 floors, unchanged.
- [x] **Affix (procedural stat modifier) system.** New `items/Affix` enum: VICIOUS (+damage), ARCANE
      (+magic damage), STURDY (+defense), VITAL (+health), AEGIS (+shield capacity). Each applies to a
      kind mask (weapon/armor/shield), rolls a value scaled by rarity (`countFor`: COMMON 0 → MYTHIC 3)
      and is serialized as `"id:value"` into the `dung.affixes` item tag. Bonuses fold into
      `PlayerState.recomputeStats` from the weapon + all armor slots.
- [x] **Item upgrades.** UPGRADE raises an item's `dung.upgrade_level` (max 5), boosting its core stat
      (weapon → damage/magic damage, armor → defense, shield → shield capacity) by 10% per level, folded
      into the stored stat tag. Cost scales with current level AND with floor depth.
- [x] **REFORGE** rerolls an item's affixes, keeping base stats, rarity, ability, and upgrade level
      (with a preview before committing). Cost scales with floor. **Pity:** every 10 rerolls is a
      guaranteed **max-affix roll** (all kind-eligible affixes at their rarity value), so reforge is
      never a pure RNG sink.
- [x] **PRESERVE** is a **gamble**, costing **all three** currencies (run coins + persistent coins +
      shards, AND not either/or) at floor-scaled prices: pays 50/200/250 (×floor tier) for a **40%
      chance** to queue the item at half durability for post-run delivery via the existing
      `pendingPersists` path; on **failure** the item is returned immediately **one rarity worse**
      (`downgradeRarity`).
- [x] **SALVAGE** destroys the selected item for **run coins** (`max(1, (rarityOrdinal+1)*2 + stat/10)`,
      a per-run currency lost on death — never persistent shards, so it can't be farmed between runs),
      gated behind a two-click confirm.
- [x] **PERSISTENT STORAGE** is a **read-only** in-run view of the player's persistent items (storage,
      armor and offhand slots). Withdraw is impossible inside a run via any path.
- [x] **Floor-scaled costs.** UPGRADE / REFORGE / PRESERVE costs scale linearly with the workstation
      tier (`floor / 5`, clamped ≥1) via pure `WorkstationRules.scaledCost`, so decisions stay meaningful
      as per-floor income grows late-game.
- [x] **Workstation blocks are protected** from breaking (`BlockBreakEvent`), so the registered function
      isn't lost mid-floor.
- [x] **Reliability:** all costs and validity checks live in pure, unit-tested `WorkstationRules`
      (`isWorkstationGear`, `primaryStat`, `salvageValue`, upgrade costs); every operation is applied
      server-side after re-validating the item is still present, still eligible, and the exact item the
      player selected (identity fingerprint); operations are guarded against double-click re-apply;
      `recomputeStats` runs after UPGRADE/REFORGE/SALVAGE/PRESERVE so equipped-item changes take effect
      immediately; live combat stats refresh without waiting for a re-equip. Keys/bombs (run items)
      are never workstation-eligible.
- [x] **Tests:** `AffixTest` (kind masks, pools, countFor, value scaling, distinct rolls, serialization,
      maxed pity rolls) and `WorkstationRulesTest` (upgrade/reforge/preserve/salvage costs, salvage
      scaling, upgrade bounds, floor scaling, reforge pity, affix-id parsing) — all pure JUnit, no
      Bukkit mocks.
- [x] **UI:** single unified `WorkstationUI` chest GUI for all five stations — item list → detail panel
      with exact costs and result preview → CONFIRM (two-click for destructive ops) → BACK. Removed the
      old `PersistUI` and its villager wiring in `GameListener`/`Dung`.

- [x] **UI:** single unified `WorkstationUI` chest GUI for all five stations — item list → detail panel
      with exact costs and result preview → CONFIRM (two-click for destructive ops) → BACK. Removed the
      old `PersistUI` and its villager wiring in `GameListener`/`Dung`.

### Iteration 30 — Mana Shield equip-slot + shield durability (v1.0.1)
- [x] **Manual shield equip slot:** hotbar slot 9 is now a pure **equip slot** — a shield is only active
      while the player places one there; it is never auto-pulled in from the inventory. While the slot
      is empty a **green "Equip Shield" pane** shows when the player owns a shield elsewhere (so they
      know they can equip one), or the standard black empty placeholder when they own none.
- [x] **No lingering indicator:** the green pane carries its own `dung.equip_indicator` tag (distinct
      from the click-blocking run-item tag, so it stays swappable) and `syncShieldSlot` sweeps up any
      stray panes the moment a shield is equipped — it only ever exists while the slot is empty.
- [x] **Charge while held:** shields now only charge while the player is **holding them** (slot 9
      selected) and sneaking; absorption still comes from the equipped slot-9 shield regardless of what
      is in the main hand.
- [x] **Persistent-shield switch prompt:** an equipped persistent shield with a strictly better shield in
      the inventory shows a clickable **Switch** button (runs `/dung shieldswitch`) instead of
      auto-replacing — the player keeps their chosen persistent shield unless they opt in.
- [x] **Real shield durability pool:** mana shields gain a native durability pool (50) that absorbing
      damage wears down; the vanilla durability bar is repurposed to display charge on shields, and is
      now also synced to custom durability on weapons/armor.
- [x] **Related fixes shipped in 1.0.1:** plot border/path height fix, party-leader transfer bugfix, and
      ShopUI updates.

### Iteration 31 — offline leaderboard, plot settings, multiple plots & neighbour paths
- [x] **Offline players on the leaderboard:** `/leaderboard` previously read only the in-memory profiles
      map, so offline players never appeared and their names could be null on Paper. `MetaManager` now
      persists a player `name` on each profile (set on join via `setName`) and exposes `allProfiles()`,
      which reads every saved profile from `saves.yml`. The command iterates `allProfiles()` (so offline
      players show), prefers the stored name (falling back to `Bukkit.getOfflinePlayer`, then "Unknown"),
      and appends a gray `(offline)` tag to offline entries.
- [x] **Leaderboard rank colors:** 1st = aqua `§b`, 2nd = blue `§9`, 3rd = dark blue `§1`, the rest stay
      gray `§7`.
- [x] **Leaderboard category-button fix:** the category switcher hover text was passing legacy `§`-coded
      strings into `Component.text()`, which throws `LegacyFormattingDetected` and rendered the codes as
      literal gray text (a warning every time `/leaderboard` ran). `ChatUI.command` now deserializes the
      hover text with the legacy serializer like it already did the label.
- [x] **Per-plot settings:** `PlotInfo` gains `pvp`, `fireSpread`, `isPublic` flags plus `buildTrust` and
      `containerTrust` UUID sets, all persisted in `plots.yml`. New commands let the owner (standing on
      their plot) configure it: `/plot pvp|fire|public on|off`, `/plot trust|untrust <name>` (build
      access), `/plot container|uncontainer <name>` (container access), `/plot settings` (summary).
- [x] **Settings enforced in `PlotListener`:** build access now allows the owner, trusted builders, or
      anyone on a public plot; chests open for the owner, public plots, or container-trusted players;
      PVP is cancelled on plots with `pvp` off (victim standing on the plot); fire may only burn/spread
      on plots with `fireSpread` on (`BlockBurn`/`BlockSpread`).
- [x] **Multiple plots per player:** ownership is derived from the `plots` map (no more single
      `playerPlots` reverse-map). A player may claim as many plots as they can afford. `unclaim`/`name`
      target the plot you're standing on; `/plot home` returns to your first plot.
- [x] **Rising claim price:** the price of each additional plot rises **×1.25 per plot already owned**
      (`claimShardCost`/`claimCoinCost` = base × 1.25^owned), applied to both `showClaimOptions` and the
      charge in `claimPlot`.
- [x] **Neighbour-plot path access:** a player owning two adjacent plots may build on the 2-wide path
      between them (`canUseSharedPath` in `PlotManager` + `canModify` in `PlotListener`). When one of the
      pair is unclaimed/owned by someone else the path reverts to protected automatically.
- [x] **Shared path survives reload/regen:** `buildPlotBordersAndPaths` skips re-laying/clearing any path
      side whose two bounding plots are owned by the same player, so the owner's shared path is exempt
      from the regen "clear" (the 2-block AIR clear above paths).

### Iteration 32 — enemy wall fix, room reliability, broken-gear preservation, plots QoL & run head-HP
- [x] **Enemies can't walk through walls:** `Enemy.isWalkable` rejected only two brick types, so mobs
      walked through mossy/blackstone/cobble walls and left the room. It now rejects **any solid block**
      (same fix in `BossController`), and a new `pathClear(from,to)` samples the straight line so the
      spider leap and charger dash can't skip over a wall in a single teleport.
- [x] **Rooms always clear:** the per-tick clear check now also **despawns and drops** an enemy that has
      strayed >3 blocks outside its room (`insideRoom(..., 3.0)`), so a wall-escaped mob can't keep a room
      locked forever. Clear also runs for a re-entered room even if its locked flag was reset.
- [x] **Rooms always activate:** combat/elite rooms are also spawned+locked from the per-tick room loop
      (not only on an `enterRoom` transition), so a room can't stay dormant when everyone is alive and
      inside but no member triggered a room-crossing event. Re-entering a spawned room no longer unlocks it.
- [x] **Broken gear is preserved, not deleted:** `handleBrokenArmor` unequips a broken persistent piece,
      moves it into a free main-inventory slot (drops on the ground only if the bag is full), and notifies
      the player with the `/shop` repair hint — instead of silently destroying it. Durability is applied by
      iterating every inventory slot **exactly once** (the old overlap double-damaged and re-broke pieces).
- [x] **Persistent items aren't duplicated on restore/revive:** the pre-run snapshot only re-inserts
      persistent items whose `dung.uuid` is still owned (or legacy uuid-less items); dropped/exchanged/
      preserved pieces aren't resurrected, and owned pieces aren't doubled as undamaged copies.
- [x] **HP bar above players' heads:** during a run each party member shows a green/red HP bar
      (`cur/max`) above their name (`updateHeadHp`, refreshed only when the value changes); dead
      (spectator) players are marked by drifting white-smoke particles.
- [x] **Key-room unlock QoL:** unlocking a LOCKED room no longer teleports the player inside (they walk
      in themselves), and the freed door blocks burst END_ROD particles where the barrier stood.
- [x] **Plots world gameplay:** `KEEP_INVENTORY` gamerule is on; a custom **daylight cycle** runs a full
      24h day over 20 real minutes with **day twice as long as night** (`ADVANCE_TIME` off); claimed plots
      never have their borders/paths regenerated (`buildPlotBordersAndPaths` early-returns).
- [x] **Plot tree growth across same-owner plots:** `isSameOwnerNeighbor` + `withinPlot` treat edge-adjacent
      same-owner plots (and the shared path) as one contiguous growth area, so a canopy isn't pruned at
      your own boundary.
- [x] **Plot leaf-decay acceleration:** cutting a log in the plots world scans a 7-block radius and quickly
      decays now-detached leaves (skipping builder-placed persistent leaves), so the canopy falls instead
      of lingering on vanilla random decay. `breakNaturally()` still drops saplings/apples.
- [x] **Plot item-pickup access:** `PlotInfo.pickupTrust` persisted; `/plot pickup|unpickup <name>`
      (owner-only, both plots on a shared path); enforced in `PlotListener.onPickup`
      (owner/public/pickup-trusted).
- [x] **Outside-dungeon weapon nerf:** Dung weapons deal only **25%** of their vanilla damage to hostile
      (`Monster`) mobs outside a run, so a run weapon isn't an overpowered freebie in the plots world.
- [x] **Salvage works anywhere:** `/salvage` no longer requires being inside a run — outside, shards go
      straight to the persistent balance; inside, they're banked to it on the floor boss's defeat.
- [x] **Life Drain & party polish:** right-click heal is blocked while sneaking (sneak+right-click casts
      the AoE ability) and plays a sound; `/party invite` has a 5s anti-spam cooldown; the death path
      guards against applying the persistent-gear durability penalty twice if a player quits mid-death.

### Iteration 33 — gacha slot-machine shop redesign (weapons / armor / mana shields)
- [x] **Unified 54-slot gacha GUI** replaces the old multi-item shop. Exactly **three tabs** — WEAPONS,
      ARMOR, MANA SHIELDS (slots 1/3/5) — shared by both the in-run shop (`openRunShop`) and the
      between-run persistent shop (`openPersistentShop`). Currencies preserved: run coins for the run
      shop, persistent coins for the persistent shop.
- [x] **Two-stage horizontal slot-machine roll:** the window (slots 11-15, center 13) first scrolls an
      **item** strip, then a **rarity** strip, decelerating to a stop. Pure `RollAnimationMath` builds
      the strip/frames/delays so the server-chosen result always lands in the window center on the final
      frame.
- [x] **Server-authoritative RNG:** the result (`ServerSideRollResult`) is generated **once, server-side,
      before any animation**, and the currency is charged exactly once on ROLL. The client/animations are
      pure presentation — it can never influence the outcome.
- [x] **KEEP / SALVAGE resolution:** the final item shows KEEP (add to inventory) and SALVAGE (convert to
      shards via the existing `WorkstationRules.salvageValue`). In the run shop, salvage shards are banked
      to the persistent balance on boss defeat (existing behavior); in the persistent shop they go straight
      to the persistent shard balance and are saved.
- [x] **Security hardening:** single purchase per roll (state machine guard), no roll while an animation is
      active, no tab switch mid-roll, no double KEEP/SALVAGE; all shop-GUI clicks are cancelled while
      player-inventory (non-shift) clicks are allowed so a player can free space; a **full inventory** never
      loses the item — the result is marked KEEP_PENDING and the player retries (or salvages) after freeing
      a slot; on disconnect the in-memory session is dropped, but persistent-shop pending results are
      written to `pending_shop_results.yml` (via `StashUI.encode/decode`) and restored on reopen so they
      can never be lost.
- [x] **Repairs + permanent upgrades preserved:** the persistent shop's idle view keeps repair / repair-all /
      permanent-upgrades utility buttons (slots 47/50/53), and `/upgrades` still opens the upgrades GUI.
      The old consumables/buffs (hearts/mana/keys/bombs/floor buffs) were removed per the 3-tab spec.
- [x] **Wiring:** `GameListener.onQuit` now calls `ShopUI.onQuit(p)` to drop the session; `ShopUI.onQuit`
      exists and `openRunShop`/`openPersistentShop`/`openUpgrades` signatures are unchanged.
- [x] **Pure, unit-tested core:** `shop/ShopTransaction` (state machine: one roll, one KEEP/SALVAGE, tab
      switch only while idle, KEEP_PENDING retry), `shop/ShopRules` (per-shop costs + salvage math),
      `shop/RollAnimationMath` (result lands in window center, decelerating delays), plus
      `shop/ShopPendingStore` (atomic disk persistence) and `shop/ShopType`/`shop/Category`. Added
      `ShopTransactionTest`, `ShopRulesTest`, `RollAnimationMathTest` — all pure JUnit, no Bukkit mocks.

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
- [x] **Leaderboards / statistics** — `/leaderboard` shows top players by persistent coins, shards, kills,
      clears, or max floor, including **offline players** (name persisted in `MetaProfile`), with
      clickable category switchers and 3-color top-3 rank styling.
- [ ] **Leaderboard page/personal stats** — a `/dung stats`-style personal page with more detail (history,
      per-category rank, class breakdown) and richer leaderboard pagination/visuals.
- [ ] **Class-specific passives/active balance** beyond the three defaults.
- [ ] **More room shapes** — cross/ring/bridge shapes and multi-level platforms for the structure library.

### Iteration 34 — WorldEdit structure library replaces the custom room-editor system
- [x] **Scrapped the custom JSON room-template system** (`RoomTemplate`/`RoomIo`/`RoomRegistry`/
      `RoomInstantiator`/`RoomTemplateRotator`/`RoomValidator`/`RoomEditor`/`RoomEditSession`/
      `RoomEditorWorld`/`RoomChunkGenerator`/`RoomTester`/`RoomTutorial`/`RoomCommand` and
      `resources/rooms/*`). No two competing room systems remain.
- [x] **WorldEdit schematic rooms:** every room is now a `structure.schem` (owned by WorldEdit) paired
      with a `structure.yml` metadata sidecar (SnakeYAML). New `structure` package:
      `StructureDefinition` (pure metadata), `StructureMetadata` (YAML round-trip), `StructureTransform`
      (rotates metadata consistently with WorldEdit's `AffineTransform.rotateY` — origin-based, so
      schematic and metadata stay in sync), `StructureValidator` (metadata + physical block checks via a
      `BlockLookup`), `StructureWorldEdit` (load/paste/blockLookup), `DefaultStructures` (built-in sealed
      boxes per room type), `StructureRegistry` (scans `plugins/Dung/structures/` + fills defaults),
      `StructureManager` (owned by `Dung`).
- [x] **DungeonInstance rewired:** `resolveTemplates` → `resolveStructures` (pick + rotate via
      `StructureTransform.requiredRotation`, respecting `allowed-rotations`); rooms paste via
      `StructureWorldEdit.paste`; sealed-box defaults get doorways carved (`carveStructureDoorways`) for
      exactly the floor's open door directions; `insideRoom`/`roomSpawn`/`shopkeeperLoc`/
      `templateEnemySpawns`/`sealDoors`/corridor carving all read structure metadata. Procedural
      `RoomGen` remains the fallback when no structure fits or `custom-rooms` is off.
- [x] **`/dung room list|reload|validate <id>|preview <id> [0-3]`** admin commands replace the `/room`
      editor CLI. Drop author files into `plugins/Dung/structures/` and `/dung room reload`.
- [x] **WorldEdit 7.3.19** compileOnly (7.4.x needs JVM 25; the server toolchain is Java 21). `plugin.yml`
      now `depend: [WorldEdit]`. Full `gradlew build` green; new pure tests
      `StructureTransformTest`, `StructureValidatorTest`, `StructureMetadataTest` added.
- [x] **Auto-detected doors + `/dung room gen`:** new `DoorDetector` scans a schematic's four wall faces
      for air openings carved at floor level and emits the matching connectors (direction, anchor,
      width, floor-y, height) automatically — no hand-written coordinates. `/dung room gen <id>
      [types]` reads the player's WorldEdit `//copy`, runs `DoorDetector`, writes `structure.yml`
      (bounds + detected doors + auto spawn floor + PLAYER_SPAWN at center) and saves `structure.schem`,
      then reloads. Doorways are carved open procedurally at build time by `carveStructureDoorways`.
      Added `StructureWorldEdit.save`, `DoorDetectorTest` (sealed box, single door, all four faces,
      sub-min-width hole ignored, two doors on one face stay distinct).
- [x] **Marker signs:** new `SignScanner` reads written signs out of the clipboard (WorldEdit schematics
      preserve sign text as tile-entity NBT, via `BaseBlock.getNbtReference()`). `/dung room gen` now
      turns `[PLAYER_SPAWN]` / `[SHOPKEEPER]` / `[LOOT]` / `[HAZARD]` / `[MECHANIC]` / `[SPECIAL]` /
      `[SPAWN_FLOOR]` signs into the matching markers / spawn floors and strips those signs from the
      saved schematic so they never appear in the generated room (unrecognized signs stay as decoration).
      Full `gradlew build` green.
- [x] **Room id = schematic name; procedural door carving.** The room id is now the schematic basename:
      `/dung room gen <id>` writes `<id>.schem` + `<id>.yml` and the registry scans any `*.yml` (id
      derived from the filename, sibling `<id>.schem`). `DoorDetector` is gone — doors are no longer
      detected from carved holes or stored as connectors. The connector metadata was removed from the
      structure model (`StructureDefinition`/`StructureMetadata`/`StructureTransform`/
      `StructureValidator`/`DefaultStructures`); only corridor connections remain, and those are carved
      procedurally at build time. `carveStructureDoors` + `carveStructureCorridors` carve structure-room
      doorways and corridors on the same fixed `PERP_CENTER` line the procedural rooms use, so a
      structure opening always lines up with the corridor (never a hole between the room's outer wall
      and the corridor wall) and stays well away from corners. `sealDoors` seals that same opening.
      Rotation is picked randomly among `allowed-rotations` (`StructureTransform.pickRandom`) for
      variety, since there are no connectors to align. Deleted `DoorDetector`+`DoorDetectorTest`;
      updated `StructureTransformTest`/`StructureValidatorTest`/`StructureMetadataTest`. Full
      `gradlew build` green.

### Iteration 35 — plot crop harvesting & composter feeding
- [x] **Right-click harvest & auto-replant:** right-clicking a fully grown crop (wheat, carrots,
      potatoes, beetroot) on a plot you can modify breaks it exactly like a player would — all its
      natural drops fall on the ground — then immediately plants its seed back as a fresh age-0 crop,
      so the field stays planted without re-seeding by hand. Only the owner, a public plot, or a
      build-trusted player may harvest; others' crops are untouched.
- [x] **Drop-to-compost composter mechanic:** dropping a compostable item while looking at a
      composter consumes the dropped amount from your inventory and feeds it using the **same
      per-item vanilla RNG** as vanilla (`Material.getCompostChance()`). Any leftover material is
      stashed inside the composter and persisted to `compost.yml` (atomic writes). When it reaches
      the **level-8 "content ready" finished state**, right-clicking collects a bone meal and the
      composter keeps filling from the leftovers until the buffer is empty. Breaking the composter
      returns its buffered material so nothing is lost.

### Iteration 36 — shop GUI hardening & affordances (run + persistent)
- [x] **Menu + per-category roll GUIs:** the single 54-slot tabbed shop is split into a sparse
      27-slot **main menu** (WEAPONS / ARMOR / MANA SHIELDS entries in one row, each showing its
      roll cost; persistent-only Repair / Upgrades / Repair All below) and a dedicated 54-slot
      **roll GUI** per category with just Back, the two slot-machine windows, ROLL and
      KEEP/SALVAGE — no tabs. A paid-for pending result always reopens its roll GUI so it must be
      resolved; Back is blocked while a result is pending (persistent results additionally survive
      in the disk-backed pending store).
- [x] **Single repair-cost helper:** the "next 10 durability / 5 coins per 10 / round up, × (1 +
      repair count)" math that existed in FOUR places (`repairHeld`, `repairAll`,
      `makeRepairItemButton`, `makeRepairAllButton`) is now one `repairCost(ItemStack)` helper — the
      displayed cost can never drift from the charged cost again. Inventory + armor scanning was also
      deduplicated into `damagedPersistentGear(Player)`.
- [x] **Plugin-owned PDC key:** `NamespacedKey.minecraft("dung_gui")` (reserved `minecraft:`
      namespace anti-pattern) replaced with `new NamespacedKey(plugin, "dung_gui")`.
- [x] **Named constants for repair-broken:** inline 150 coins / 100 shards replaced with
      `REPAIR_BROKEN_COINS` / `REPAIR_BROKEN_SHARDS`; all messages use them.
- [x] **Affordances before failure:** the ROLL button renders grayed-out ("Can't afford — need N
      more") when the balance is short instead of failing with a red chat line after the click;
      maxed upgrades show a non-clickable BARRIER "MAXED" icon; unaffordable upgrades render as a
      gray GRAY_DYE icon with a "Need N more shards" line.
- [x] **Crit description matches the applied effect:** `Upgrades.CRIT_DELTA_PCT` (0.5%/level) is now
      the single source of truth shared by the upgrade tooltip (`ShopUI.effectDesc`) and the actual
      application in `PlayerState` (previously 0.005/level was hardcoded in two files).
- [x] **Persistent-shop quality disclosure:** the persistent shop's cost pane states that rolls
      produce base-quality gear (floor-scaled rolls are run-shop only).
- [x] **Decluttered shop GUI:** removed all purely decorative/redundant items from both shops —
      the ◀/▶ window rails, the gold/diamond info pane (balance already lives in the title), the
      sunflower cost pane, the paper status pane, and the duplicate result PREVIEW item. Cost +
      rules now live in the ROLL button's lore (one line each: what it rolls + KEEP/SALVAGE,
      currency caveat); KEEP/SALVAGE lore condensed to one line apiece. The GUI is now just tabs,
      the two roll windows, ROLL, KEEP/SALVAGE and (persistent only) repair/upgrade utilities.
- [x] **Condensed lores everywhere:** upgrade icons show "Lv x/y · Effect …" on one line plus a
      single cost/affordance line (3 lines max, down from 5); repair buttons merge their two intro
      lines into one.
- [x] **Overhead HP bars now actually render:** `Player.setCustomName` does not change a player's
      nametag on Paper, so the old head-HP display was invisible. Replaced with a non-persistent
      `TextDisplay` (billboard, 60% view range) riding the player's head: green/gray 10-segment bar
      plus current/max hearts, re-sending metadata only when the value changes. Removed on leave,
      death/party-wipe teardown and run start; stale passenger displays are also cleaned up.
- [x] **Workstation GUI cleanup:** the five workstation info panels and the per-item detail panel
      now use condensed lores (merged wrapped half-lines; upgrade costs on one line — PRESERVE drops
      from 9 lines to 4). `NamespacedKey.minecraft("dung_ws")` / `"_slot"` replaced with
      plugin-owned keys, same as ShopUI. StashUI audited — already minimal, no changes needed.
- [x] Minor: null-guard on held-item display name in the Repair Item button lore.

### Iteration 37 — audit fixes: high-severity logic + cosmetic sweep
- [x] **Combat correctness:** entering a room no longer grants invulnerability to the whole party
      (only the entering player; floor-start keeps the party grant); class/ability targeting now
      resolves enemies from the caster's own room (`playerRoom`) instead of global `curRoom`, fixing
      split-party ability mis-hits; attack cooldown is only consumed on a swing that isn't blocked by
      a broken weapon or a full Soul Siphon.
- [x] **Data integrity:** Doomblade mythic-downgrade now rewrites its stat/rarity LORE to match the
      new stats (was showing MYTHIC values); `PartyManager.acceptInvite` rejects players who already
      joined another party (no more dual membership); `/plot claim|unclaim` are gated mid-run like
      home/warp.
- [x] **Cosmetic sweep:** "persistent wallet" → "persistent coins"; descend-vote progress and pass
      messages use the same yes/needed denominator; shard-cost errors styled like coin errors; broken
      -item messages cleaned (double spaces / stray codes); salvage success green; head-HP bar shows
      red current / gray max; death message no longer claims coins are lost when boss revival restores
      them; room-seal action-bar hint deduped (no per-tick spam); bomb success messages green like key
      unlocks; plot unknown-subcommand usage lists all subcommands; disabled leaderboard Prev/Next no
      longer look clickable; unknown leaderboard category errors with the valid list instead of
      silently falling back; invite-cooldown display uses ceil (no phantom extra second); identical
      "Run started!" wording for party/solo; `[Shop]`/`[Upgrades]` prompt hovers warn they're
      unavailable during a run; join prompt hides Start-a-Run for players already in a run.
- [x] **Salvage rules unified:** bulk eligibility (`isBulkSalvageable`) and per-item shard value
      (`salvageValueOf`) now live only in `WorkstationRules`; `DungCommand`'s divergent private
      copies delegate to them (held salvage of weapons/shields now values via `primaryStat`, matching
      workstations and the shop).
- [x] **Pickup feedback:** a heart picked up at full HP is now refused and stays on the ground for
      later (previously it was silently consumed and wasted), with chat feedback ("Hearts is full
      — left on the ground"); coins/keys/bombs are uncapped and always consumed.
- [x] **Elite heart burst:** a dying elite now explodes into hearts that scatter randomly around the
      room (inside its bounds): 3 solo, 5/6/7 for party sizes 2/3/4+. Hearts are NOT grabbable for
      the first 1.75 seconds (pickup delay), then stay until the next combat/elite room triggers
      (`spawnEnemies` clears them; floor change/run teardown also clean up). Picking a heart up
      plays the same witch-drink sound as the Soul Siphon heal and bursts red dust particles around
      the player (hearts at full HP are still refused and remain on the ground). The Soul Siphon
      player-to-player heal now also bursts particles around the healed target, not just along the
      beam between caster and target.
- [x] **Armor-roll rarity leak fixed:** armor trims encode rarity, so the shop's rolling decoys
      leaked the not-yet-revealed rarity before it landed. Armor is now rolled TRIMLESS
      (`GearFactory.armor` applies no trim); a new `GearFactory.applyRarityTrim` finalizes the trim
      only when an item is revealed — on the shop roll's server-decided result, on pedestal/world
      drops (`spawnPedestal`), and on starter kit pieces.
- [x] **Trim/rarity stays in sync on downgrade:** `GearFactory.downgradeRarity` now re-applies
      `applyRarityTrim`, so a downgraded armor piece no longer wears its old rarity's trim.
- [x] **Plot container protection covers all containers:** the right-click guard checked only
      chests, so barrels, furnaces, hoppers, dispensers/droppers, shulker boxes and brewing stands
      on someone else's plot were openable. It now blocks every `Container` block (ender chests are
      unaffected — they're personal); message generalized to "container".
- [x] **Scattered floor spawns:** on run start and every descend, party members teleport to random
      spots inside the starting room (reusing the elite-heart scatter logic) facing the room center,
      instead of all stacking on the spawn point.
- [x] **Spectators can't claim pedestal loot:** dead players (spectators during a run) get their
      pedestal interactions cancelled — they can look but not take.
- [x] **Full-hearts pickup spam fixed:** the "X is full" warning is throttled to once per 3 seconds
      per player (the pickup event re-fires every tick while standing on a refused heart).
- [x] **Mana-shield colors no longer leak during rolls:** the shield's rarity banner gradient
      (like armor trims) was baked in at creation and visible on rolling decoys. Shields now roll
      plain; `GearFactory.finalizeRarityLook` (renamed from `applyRarityTrim`) applies the trim OR
      banner colors only to finalized items — shop results, pedestal drops, starter kit — and is
      re-applied on rarity downgrade.
- [x] **Run-shield wear rebalanced:** per-run mana shields now take 1-3 (random) durability per 2
      damage absorptions; persistent shields keep the gentle 1-per-5 pace.
- [x] **Second run no longer silently fails to start:** `GameManager.startRun` could bail because a
      member still held a stale instance mapping (e.g. dead members removed from the party before
      endRun's cleanup, so the per-member purge missed them) — and the command broadcast "Run
      started!" anyway without teleporting anyone. startRun now purges mappings whose instance has
      ended, refuses only genuinely active runs, and returns success; the command only announces the
      run when it actually started.
- [x] **Legacy-format hover warning fixed:** the Shop/Upgrades prompt hovers carried raw `§` codes
      inside a `Component.text()`, spamming Paper's `LegacyFormattingDetected` warning when the
      client rendered the tooltip. Hover strings are now parsed through the legacy serializer.
- [x] **Damage knockback:** taking damage in a run now launches the player AWAY from their attacker
      (nearest living enemy, or the boss), scaled with the damage taken — light hits nudge, heavy
      hits (boss slams) launch up to ~4.5 blocks. Applies to normal hits and invuln-bypassing
      explosions alike.
- [x] **Three-row head HP tag:** the overhead TextDisplay now shows the player name (top), the
      green/gray HP bar (middle), and the current/max hearts numerically (bottom) on separate lines,
      translated 0.25 blocks up so it sits just above the head. Tag lifecycle hardened: dying
      removes it immediately, spectators get none, and removal dismounts the passenger before
      deleting so no ghost tag survives a run end or floor change.
- [x] **Equip/swap/GUI fixes:** right-click equipping armor over a starter piece now removes the
      displaced starter (the cleanup previously only ran for inventory clicks, not right-click
      equips); shift-clicking a mana shield while another is equipped now SWAPS — the unequipped
      shield goes into the first free bag slot (never the slot the new one came from, dropped if
      the bag is full); WorkstationUI's `busy` guard no longer bricks the open GUI when an
      operation is rejected (it was set once and never cleared — after one failed CONFIRM every
      further click on any item was silently ignored).
- [x] **`/dung lobbykit` (op-only):** hands the caller the full lobby-building palette (~50 items —
      blackstone core, gold/lantern accents, crimson/spruce wood, amethyst/purpur + quartz themes,
      moss/greenery softening) packed into named pre-filled shulker boxes (`Lobby Kit (1..n)`,
      as many as needed), added straight to the inventory. Unknown/renamed materials are skipped
      with a notice instead of aborting (`CHAIN` → `IRON_CHAIN` in 1.21.x with copper chains).
- [x] **`/dummy setavatar <playerName>`:** turns the nearest dummy into a player-looking figure —
      the stand becomes visible with arms and wears the named player's skin (a `PLAYER_HEAD`
      helmet skinned via a Paper `PlayerProfile`, resolved from Mojang async so no main-thread
      network stall). Online players resolve instantly; offline names fetch their skin. The
      avatar persists in `dummies.yml` and is re-applied on respawn/restart; tab completion
      suggests online players.
- [x] **Dummy name screen-bomb fix:** dummy display names are now capped at **48 characters per
      line** and **4 lines maximum** (excess truncated with `…`). Previously an arbitrarily long
      name filled the player's entire viewport through the TextDisplay billboard.
- [x] Confirmed as intended, not changed: sneak+Q cancels drop because it is the ability activation;
      pending salvage shards are awarded on each floor's boss defeat.

### Iteration 38 — world separation remaster (lobby / plots / per-run dungeon worlds)
- [x] **Three world types:** a persistent `dung_lobby` void world (obsidian spawn platform, no mob
      spawning, keepInventory, frozen noon) that players join into and return to; the existing
      `dung_plots` world (unchanged); and a **dedicated `dung_run_<id>` void world per run**.
- [x] **New `WorldManager`:** get-or-create lobby, create sanitized per-run worlds (void generator,
      no structures/mobs), and delete-on-end (`teleport stragglers → unload → recursive folder
      delete`, never throws). Dungeon geometry is unchanged — each instance now owns its whole
      world instead of a 1000-block offset region of the shared one.
- [x] **Lifecycle:** `GameManager.startRun` creates the run world first and hands it to the
      instance; `endRun` deletes the world 2 ticks after everyone is out; leaving mid-run never
      deletes (other members may remain). Run ends always send players to the LOBBY (never back
      into a deleted world); joining teleports players to the lobby spawn (deferred 1 tick).
- [x] **Lobby gamerules & persistence:** the lobby world folder persists on disk like any world
      (only `dung_run_*` worlds are deleted), and operator/regular edits survive restarts — the
      plugin re-loads the existing folder and re-asserts the rules on every load: no mob/phantom
      spawning, no raids, keepInventory, frozen noon + clear weather, no fire tick/spread, no
      random ticks (no grass spread/leaf decay), no advancement spam, PvP off.
- [x] **Lobby protection:** the lobby world is now read-only for regular players — block
      break/place, ignite/burn/explosions, buckets, hanging item frames/placement and farmland
      trampling are cancelled with a "The lobby is protected." note. Bypass via `dung.admin`
      (ops have it by default), so operators can still decorate the lobby.
- [x] **`/setlobby` (op / `dung.admin`):** standing in the lobby world, sets the lobby spawn to your
      current location + facing. Persisted in `lobby.yml` across restarts and used by every
      lobby-related teleport (join, run end, leave, run-world cleanup) — the default `(0, 64, 0)`
      spawn is only applied when no custom spawn exists.
- [x] **Join always routes to the lobby:** the old "restore last known location" on join fought the
      lobby model (it could drop players back into a deleted run world or anywhere else); joining
      now deterministically sends everyone to the lobby spawn (deferred 2 ticks, skipped only if
      they're somehow already there). New **`/lobby`** command teleports back to the lobby spawn
      from anywhere — blocked mid-run ("use /dung leave first") and redundant inside the lobby.
      ROOT CAUSE of both failing at once: the lobby gamerule block dispatched
      `gamerule allowFireTicksAwayFromPlayer` — a rule that doesn't exist on this server version,
      whose CommandSyntaxException escaped `getLobby()` and killed every `lobbySpawn()` call
      (join teleport + `/lobby`). Replaced with the typed (deprecated-but-functional)
      `GameRule.DO_FIRE_TICK` constant under a targeted `@SuppressWarnings("removal")`.
- [x] Supplies tab (in-run shop): direct purchases with run coins — Keys/Bombs (12c), Red Heart
      heal (10c), Mana refill (8c), Damage/Defense Tonic (+2 for the rest of the run, 25c), with
      grayed-out unaffordable affordances; tonic bonuses survive gear swaps   via PlayerState.

### Iteration 39 — /dummy NPCs + admin lobby onboarding (v1.2.0)
- [x] **`/dummy` clickable NPC command (op / `dung.admin`):** bare `/dummy` shows help. Ops spawn
      stationary dummy NPCs (`/dummy create <name[/r line2...]>`) composed of an invisible
      invulnerable armor stand + Interaction hitbox + billboard TextDisplay name. Each dummy can
      run a different command per click type as the clicking player: `/dummy setcommand left|right
      <command>`, cleared with `removecommand`, retitled with `name` (multi-line via `/r`),
      relocated with `tp`, listed, removed. Persists in `dummies.yml` across restarts; clicks play
      a button-click sound; dummies are damage-proof.
- [x] **First-join admin notice:** the first time an operator or `dung.admin` player joins, they're
      told once that the lobby world is editable by them.

### Iteration 40 — exploit-hunt fixes
- [x] **Stale shop session closed:** leaving a run or the run ending now force-closes any open
      shop/supplies GUI and drops the session; all shop handlers additionally require the session's
      instance to still be the player's active one — no more buying supplies against a dead run's
      state or KEEPing paid rolls into post-run inventories.
- [x] **Respawn safety:** deaths inside `dung_run_*` worlds route their respawn to the lobby spawn
      when the instance is already gone, instead of respawning into a world deleted 2 ticks later.
- [x] **Tonics reset per descend** (design change): Damage/Defense tonics now last for the current
      FLOOR — `enterFloor` zeroes them (and recomputes stats) on every descend; lore updated.
- [x] **`/stash` gated mid-run**, matching `/shop` and `/upgrades`.
- [x] **Dead-quit double durability penalty removed:** dead players who quit no longer take the
      leave penalty on top of the death penalty (mirrors endRun's guard).
- [x] **Compost feeding consumes the held item:** feeding uses the main-hand stack directly
      (type + amount verified, deducted in place) instead of cancel-then-deferred-remove of "the
      first matching item found" — no more identity desyncs with full inventories.

### Iteration 41 — chat color codes, lobby platform removal & ProtocolLib avatar fix
- [x] **No obsidian spawn platform in the lobby:** removed `buildSpawnPlatform` (and its call)
      from `WorldManager.getLobby()` — the void world generates bare. Note: a lobby folder created
      by an older build still has the 9×9 obsidian square at y=63 around (0,0) saved on disk;
      delete it manually or remove the `dung_lobby` world folder to regenerate.
- [x] **Ampersand color codes in chat + commands:** players can type `&a`-style codes anywhere —
      new `ChatFormatListener` translates them to real `§` codes at LOWEST priority:
      `AsyncChatEvent` rewrites the message through `TextUtil.translateAmp`, and
      `PlayerCommandPreprocessEvent` translates only the arguments (after the first space), so
      e.g. `/plot name &aHome` stores a colored name without touching the command itself.
      Colors `&0-&f`, formats `&k-&o`, `&r` and hex marker `&x` are translated; bare `&`
      ("fish & chips") is left alone. Applies to all players (no permission gate).
- [x] **Dummy-avatar PLAYER_INFO crash root-caused:** `WrappedGameProfile.GET_PROPERTIES` is a
      static reflection accessor ProtocolLib resolves against Mojang's authlib; 1.21.9+ rewrote
      authlib's GameProfile so the accessor stayed null and every fake-player packet NPE'd.
      First replacement jar (a "5.4.0" build) STILL failed — the fix (`a11b403`) and 1.21.11
      support (`#3578`) only exist in the **5.5.0-SNAPSHOT dev builds**, now published on
      GitHub's `dev-build` release (the dmulloy2 Maven repo is stale at 5.4.0-SNAPSHOT).
      Deployed `ProtocolLib 5.5.0-SNAPSHOT-fec45cf` to `run/plugins/ProtocolLib.jar` (Java 17
      bytecode, fine on the Java 21 server); old jar kept as `.bak-5.4.0`. Compile target
      stays `5.4.0-SNAPSHOT` — identical API surface for what Dung uses.
- [x] **Broken ProtocolLib degrades gracefully:** `FakePlayerRenderer` latches a `broken` flag on
      the first fatal packet send — warns ONCE (with an "update ProtocolLib" hint) instead of
      spamming per-dummy warnings, clears viewer state, and disables further packet rendering.
      `show()` now returns whether any viewer got the fake player; `DummyManager` only hides the
      armor stand when that succeeded, otherwise falls back to the visible skinned-head stand
      (never an invisible dummy).
- [x] **New `TextUtilTest`:** color/format/hex code translation, non-code ampersands untouched,
      null/no-op passthrough.

### Iteration 42 — dummy name-tag lift + full persistence
- [x] **SPAWN_ENTITY replaces NAMED_ENTITY_SPAWN:** the player spawn packet was removed in
      1.20.2 — players now spawn via the generic SPAWN_ENTITY packet with entity type PLAYER
      (ints `[entityId, data]`, bytes `[xa, ya, za, yRot, xRot, yHeadRot]` → body yaw at byte
      index 3). Head orientation still comes from ENTITY_HEAD_ROTATION.
- [x] **PLAYER_INFO write-index fixed (avatars now actually render):** with a working ProtocolLib
      the next failure surfaced in OUR code — `getPlayerInfoDataLists().write(1, …)` is out of
      bounds on the modern PLAYER_INFO packet, which has exactly ONE list-typed field (`entries`).
      Both the ADD_PLAYER and REMOVE_PLAYER writes now use index 0. This was never hit before
      because the old build died earlier at `profile.getProperties()`.
- [x] **Name tag raised 0.15 blocks:** the riding TextDisplay now carries a Transformation with a
      `+0.15` Y translation (`NAME_TAG_LIFT`) on top of its mount point.
- [x] **Dummies survive chunk unloads / world switches / late world loads:** the entity
      composition is deliberately non-persistent, so an unloaded chunk silently deleted it and
      nothing respawned it. New self-heal in `DummyManager`: `ensureSpawned()` respawns any dummy
      whose stand/interaction/display is invalid (only when its world AND chunk are loaded, never
      force-loading terrain), driven by `ChunkLoadEvent`, `WorldLoadEvent`, and
      `PlayerChangedWorldEvent`; the world-switch handler also re-sends the player's avatar
      packets via `renderer.resendTo` (switching worlds clears all client-side entities).
      Restart persistence was already handled by `dummies.yml` + deferred `loadAll()`.

### Iteration 43 — /flyspeed
- [x] **`/flyspeed [1-10|reset]`:** sets the caller's creative fly speed (`level × 0.1`,
      vanilla default = 1). Bare command shows the current level. Gated to `dung.admin`
      (ops) and blocked mid-run so it can't be used as a combat cheat.

### Iteration 44 — command handler split
- [x] **Non-dung commands moved to `MetaCommand`:** `/shop`, `/upgrades`, `/salvage`, `/stash`,
      `/party`, `/balance` and `/leaderboard` (plus their tab completion and the party-invite
      cooldown) now live in their own handler file. `DungCommand` keeps only `/dung` + `/dungeon`
      (run lifecycle, admin subcommands, room/tutorial/lobbykit) and delegates the moved
      subcommands so `/dung party|shop|upgrades|salvage|balance` still work.

### Iteration 45 — dummy armor-stand hands
- [x] **Dummies have arms + giveable hands:** the dummy base stands now spawn with arms enabled.
      Because the Interaction hitbox eats all clicks, **sneak + right-click** with an item in hand
      (admin) swaps that itemcontinue into the stand's main hand and returns whatever was held — instead
      of running the right-click command. The equipped item is persisted in `dummies.yml`
      (`hand-item`) and re-applied on respawn, so it survives restarts, chunk reloads and world
      switches. Skipped while a packet-based avatar model is visible (the invisible base stand
      isn't shown then). Regular player-placed vanilla armor stands already support hands natively.
- [x] **Hand-placed vanilla armor stands get arms:** vanilla spawns them with arms disabled, so
      they can't hold anything. `EntityPlaceEvent` now enables arms on every player-placed
      armor stand (deferred 1 tick), making them item-giveable like the dummies.
- [x] **Skin-layer metadata modernized:** the ENTITY_METADATA packet was built with the legacy
      `WrappedDataWatcher`, whose objects throw `DataItem → DataValue ClassCastException` when
      serialized on 1.21.x. Replaced with a `WrappedDataValue` list via
      `getDataValueCollectionModifier()` — the last broken packet in the fake-player chain.
- [x] **Avatar packets no longer kick clients:** with the packets finally sending, both online
      players disconnected ~1s after `setavatar` (and re-joiners after the deferred resend).
      Fixes, per known-good 1.21.11 recipes: SPAWN_ENTITY now calls `getModifier()
      .writeDefaults()` (unset fields stayed null → malformed packet → client disconnect); the
      info entry uses the modern `PlayerInfoData(uuid, latency, listed=true, …)` ctor plus
      `UPDATE_LISTED` in the actions (unlisted entries can't back a spawned player entity);
      rotation bytes moved to indices 0/2 (1.21.x dropped velocity bytes); and fake entity ids
      come from a counter starting at 1.5B instead of a hash that could collide with real
      client-side entity ids.
- [x] **Skin-layer index resolved from the server:** the skin-overlay byte's data-watcher index
      is no longer hardcoded to 17 — the player metadata layout shifts between versions and a
      byte at the wrong index (where the client expects another type) disconnects them with a
      protocol error. `FakePlayerRenderer` now reflects
      `Player.DATA_PLAYER_MODE_CUSTOMISATION`'s accessor id from the running Mojang-mapped
      server (fallback: 17), so the ENTITY_METADATA packet always targets the correct field.
- [x] **Avatar model no longer shows its own name:** the fake profile registers under a blank
      name, and the tab entry is dropped 30 ticks after spawn via `PLAYER_INFO_REMOVE` (the
      entity + cached skin keep rendering); `hide()` uses the same dedicated packet. The dummy's
      configured multi-line TextDisplay is now the only visible name.
- [x] **Name tag floats above the avatar model:** when a packet-based avatar renders, the riding
      TextDisplay's translation lifts to +1.45 (`AVATAR_TAG_LIFT`) so it sits above the ~1.9-block
      player model instead of inside its chest; the fallback head-stand keeps the +0.15 lift.
- [x] **`/dummy pos`:** moves the nearest dummy to your position AND look direction (rebuilds its
      live composition and persists). `Dummy`'s position fields are now mutable to support it.
- [x] **Dummies staring into the sky fixed:** on 1.21.x the SPAWN_ENTITY rotation bytes are
      `[xRot, yRot, yHeadRot]` — yaw was being written into the pitch slot (xRot), so a
      180°-facing dummy got pitch −180° and gazed straight up. Pitch/yaw/head now go to their
      proper slots.
- [x] **Removing a dummy now despawns its player model:** `hide()` sent the tab-removal and the
      entity-destroy in ONE try block — if `PLAYER_INFO_REMOVE` construction threw (silently
      swallowed), `ENTITY_DESTROY` never went out and the client kept a ghost model. The two
      packets are sent independently (destroy first) with a legacy REMOVE_PLAYER fallback, so
      `/dummy remove` always despawns the fake player.
- [x] **Blank tab entry removed:** the delayed tab-drop via `PLAYER_INFO_REMOVE` wasn't taking
      effect, so a nameless row lingered. The un-listing now reuses the proven multi-action
      PLAYER_INFO packet — `UPDATE_LISTED` with `listed=false` 30 ticks after spawn — which
      clears the tab row while keeping the skin registration (and the rendered model) intact.
- [x] **Dummies now actually survive restarts:** `loadAll()` ran 1 tick after enable — before
      anything lazy-created the lobby world — so every dummy in `dung_lobby` was skipped as an
      "unloaded world" on each boot. `onEnable` now resolves the lobby world eagerly and loads
      dummies 5 ticks later; the ChunkLoad/WorldLoad self-heal covers any remaining races.
- [x] **Avatar skins load after boot:** avatars resolved once at spawn-time — with no one online
      at boot, `show()` had zero viewers and the dummy settled into the fallback head-stand
      forever (joins never retried). Avatars in fallback look are now automatically upgraded to
      true player models when a viewer arrives (`PlayerJoinEvent` deferred 10 ticks, and on
      world switches), detected via the visible-stand fallback marker. Resolved profiles are
      cached per name so retries never re-hit Mojang.
- [x] **Duplicate name inside the model fixed:** two hardening changes — (1) `resolveAndApplyAvatar`
      is now guarded against concurrent double-invocation, so overlapping async resolves can't
      send duplicate packet bursts for the same dummy; (2) every dummy spawn sweeps orphaned
      manager-style entities near its position (invulnerable + non-persistent
      ArmorStand/TextDisplay/Interaction not owned by a live ref) — leftovers from earlier buggy
      builds that rendered a second name tag at body height. Vanilla player-placed stands are
      persistent and are never touched. If a duplicate still shows, check `/dummy list` for two
      dummies stacked at the same coordinates and `/dummy remove` the extra.
- [x] **`/convert` fixed:** the bare command dispatched through `args[0]`, so it fell into the
      no-args help screen and never reached its case (only an undocumented `/plot convert`
      would have worked). The label is now handled directly, sharing one `convertCmd()` with
      the `/plot convert` subcommand.
- [x] **Potions can reach buried fill-layer stone:** a splash on a non-target surface (grass over
      the filled stone layer) now drills straight down (≤64 blocks) to the first target block and
      propagates from there. Splash failures are also diagnosed precisely — "no stone/ores found
      here", "placed by a player — enable /convert", or "plot infrastructure" — instead of one
      generic no-targets message, so the actual blocker is always visible.
- [x] **Forest potion can transform sapling-grown trees:** planting a sapling marks its spot as
      player-placed, and growth never cleared that mark — so the trunk base of every home-grown
      tree stayed flagged and the potion rejected it (natural trees were unaffected). Structure
      growth and bone-meal fertilization now un-mark the origin plus all grown blocks in the
      provenance store, keeping grown wood transmutable without /convert. Existing stuck marks
      clear on the next growth; one-off cleanup for already-grown trees: toggle /convert once.
- [x] **Splash target acquisition rewritten:** a potion thrown at a tree splashes into the AIR
      beside/above it — the old down-only scan never saw wood that grows upward, so forest
      potions "found no logs or leaves" even on direct hits. `drillToTarget` now checks the
      impact block, then a 5×5 horizontal × +3/−2 vertical neighborhood (catches adjacent trunks
      and canopies), and only then drills straight down through cover for buried stone.
- [x] **Shield-switch prompt warning fixed:** `promptShieldSwitch` built its prefix with raw `§`
      codes inside `Component.text()` (the same `LegacyFormattingDetected` pattern fixed earlier
      in TabUI/ChatUI/ShopUI), spamming a stacktrace every 8s while a better shield sat in the
      inventory. The prefix is now parsed through the legacy section serializer before appending
      the clickable Switch button.
- [x] **Workstation UI selection fixed:** clicking a gear item did nothing — the SELECT button
      writes its slot index under the plugin-owned PDC key (`dung:dung_ws_slot`), but the click
      handler still read it from the reserved `minecraft:dung_ws_slot` (a leftover from the
      Iteration 36 namespace migration that was only half-applied). The read returned null and
      the handler bailed silently. Both sides now use `slotKey`.
- [x] **`/setlobby` now survives restarts:** the saved spawn was loaded in the `WorldManager`
      constructor — before any world exists, and `dung_lobby` is lazy-created by `getLobby()` —
      so `Bukkit.getWorld("dung_lobby")` always returned null and the saved position was
      silently discarded every boot (join TP fell back to `(0, 64, 0)`). The load is now
      retried once from `getLobby()` after the lobby world exists.

### Iteration 46 — transformation potions (plots QoL)
- [x] **Forest Potion:** throwable splash potion that transforms wood blocks (logs + leaves) into
      random tree varieties via a weighted pool. Family-preserving: logs→logs, leaves→leaves.
      BFS propagation up to 12 blocks, 64 blocks max, 65% spread probability.
- [x] **Stone Potion:** transforms stone/stone-type blocks into random stone variants and ores
      (rare). Weights heavily favor common stone (15:1 vs diamond ore). Range 10, max 48 blocks,
      55% spread probability.
- [x] **Propagation engine:** BFS-based wave propagation through 6-directional neighbors, grouped
      by Manhattan distance for wave-animation. Respects plot ownership (potion only works on the
      thrower's own plot), max range, max transformed blocks, and spread probability.
- [x] **Player-placed block tracking (`ProvenanceManager`):** monitors `BlockPlaceEvent`/`BlockBreakEvent`
      in the plots world, persisted to `provenance.yml`. `/convert` toggle controls whether
      potions can transform player-placed blocks (default: off).
- [x] **Wave animation:** transforms blocks in wave layers (3 ticks apart), spawning particles
      (forest: `HAPPY_VILLAGER`, stone: `ASH`) and playing sounds per wave.
- [x] **Persistent shop integration:** Forest Potion (50 persistent coins) and Stone Potion (35 persistent
      coins) purchasable from the main persistent shop menu (slots 18 and 26).
- [x] **Potion returned to stash:** if no valid targets are found (wrong plot, no eligible blocks),
      the potion cost is refunded to the player's stash.
- [x] **Unit tests:** 18 tests verifying `PotionDefinition` (targets, weighted rolls, equality) and
      `PropagationEngine.resolveTransformation` (family-preserving forest, weighted stone, custom
      definitions). All tests passing.

## Build / run
```
gradlew build            # compiles + jars
gradlew runServer        # boots a Paper 1.21.11 server with the plugin
```
Commands: `/dung start|descend|leave|stats|class|give|help` `/party create|invite|accept|decline|leave|kick|disband` `/shop` (opens GUI) `/upgrades` (opens GUI) `/salvage [all|favorite]` `/leaderboard [category] [page]` `/plots [warp <name>]` `/plot claim|home|name|warp|settings|pvp|fire|public|trust|untrust|container|uncontainer|pickup|unpickup|unclaim` `/convert`
