# Dung

A **room-based dungeon roguelite** for Paper 1.21.x, built in the spirit of *The Binding of
Isaac* but with **MMORPG-style combat, loot, and progression**. Each run generates a branching
floor of rooms, spawns enemies you fight with a melee arc + weapon abilities, and ends with a
telegraphed boss ("The Warden") that opens the way down to the next floor. Defeating bosses and
clearing rooms banks persistent coins and kill/full-clear stats that survive death.

- **Language / platform:** Java 21, Paper API `1.21.11-R0.1-SNAPSHOT`
- **Build:** Gradle (offline-friendly: `.\gradlew.bat --offline`)
- **Gameplay:** `The Binding of Isaac` (random room graph, rooms lock until cleared, boss per
  floor) × `SkyBlock` (rarity-scaled gear, HP/mana resource pools, stat-based combat).

---

## Table of contents

1. [Quick start](#quick-start)
2. [Architecture overview](#architecture-overview)
3. [Commands](#commands)
4. [Package reference](#package-reference)
   - [`com.lieyabull.dung` — plugin root](#comlieyabullndung--plugin-root)
   - [`dungeon` — floor & room generation](#dungeon--floor--room-generation)
   - [`room` — room editor & template system](#room--room-editor--template-system)
   - [`entity` — enemies & AI](#entity--enemies--ai)
   - [`items` — gear, rarity & loot](#items--gear-rarity--loot)
   - [`game` — run state, combat & lifecycle](#game--run-state-combat--lifecycle)
   - [`boss` — the Warden](#boss--the-warden)
   - [`listener` — Paper event wiring](#listener--paper-event-wiring)
   - [`meta` — persistent progression](#meta--persistent-progression)
   - [`pickup` — floor pickups](#pickup--floor-pickups)
   - [`ui` — HUD, tab menu & chat](#ui--hud-tab-menu--chat)
5. [Combat & stats model](#combat--stats-model)
6. [Death & persistence model](#death--persistence-model)
7. [Design notes & known issues](#design-notes--known-issues)

---

## Quick start

```bash
# Build offline (no network needed if deps are cached)
.\gradlew.bat --offline build

# Run a Paper server with the plugin (run-paper, Minecraft 1.21.11)
.\gradlew.bat --offline runServer

# In-game
/dung start       # begin a run
/dung class mage  # pick a class (warrior | mage | ranger) before starting
/shop             # between runs: spend persistent coins on gear
/upgrades         # between runs: spend shards on permanent upgrades
/salvage          # in a run: break held armor into permanent shards
/party create     # create a party for multiplayer dungeons
/plots            # teleport to the plots world
/plot claim       # claim a plot (250 shards or 150 coins)
```

Requires permission `dung.admin` (default: OP) for the debug command `/dung give`; everything
else (including `/shop`, `/upgrades`, `/salvage`) is open to all players.

---

## Architecture overview

The plugin supports **multi-player parties and parallel dungeon instances**: `GameManager` is a
registry of `DungeonInstance`s — each party gets its own instance with its own floor, enemies,
boss, and per-player state. Multiple parties can run dungeons simultaneously in the same world
at different coordinates. Each player has their own `PlayerState` (the single source of truth
for HP/mana/stats); gear is never duplicated — it stays in the real player inventory and stats
are recomputed from it on every change. Dungeon geometry is generated purely as data (`Floor`,
`FloorGenerator`, `RoomNode`) and then projected into the world by `RoomGen`, all at a fixed
`BASE_Y = 80` in the resolved non-End world.

```
 Dung (JavaPlugin)
  ├─ MetaManager          persistent progression (saves.yml)
  ├─ GameManager          registry of DungeonInstance(s), routes events per player
  │   ├─ PartyManager     party lifecycle: create, invite, accept, leave, kick, disband
  │   │   └─ Party        group of players sharing a dungeon run (max 4)
  │   └─ DungeonInstance  one active dungeon per party
  │       ├─ Run          per-run data (rng, floor, coins, kills)
  │       ├─ PlayerState  per-player HP/mana/stats/cooldowns (source of truth)
  │       ├─ Floor        room grid (data)
  │       ├─ FloorGenerator  builds the branching room graph
  │       ├─ RoomGen      projects rooms/corridors into the world
  │       ├─ Enemy        runtime mob + AI
  │       └─ BossController  the Warden (HP scales with party size)
  ├─ GameListener         Paper events -> GameManager -> correct DungeonInstance
  ├─ DungCommand          /dung, /dungeon, /shop, /upgrades, /salvage, /party
  ├─ Upgrades             permanent stat-upgrade tracks (shards)
  ├─ ItemPool / GearFactory / ItemTags / Rarity   loot system
  ├─ Pickup               floor pickup identity & effects
  └─ HUD / TabUI / ChatUI display
```

---

## Commands

`DungCommand` routes `/dung` + `/dungeon` (subcommands), and the top-level `/shop`,
`/upgrades`, and `/salvage` commands. Only `/dung give` is admin-restricted.

| Command | Permission | Effect |
|---|---|---|
| `/dung start` | all | Begins a new run (errors if one is active). |
| `/dung descend` | all | After beating the boss, generates and enters the next floor. |
| `/dung leave` | all | Ends the current run (strips run gear, keeps persistent items). |
| `/shop` | all | Opens the between-run shop GUI: spend persistent coins on persistent gear. |
| `/upgrades` | all | Opens the upgrades GUI: spend shards on permanent stat upgrades. |
| `/salvage` | all | Break the held Dung armor piece into permanent shards (in a run). |
| `/salvage all` | all | Salvage every Dung armor piece in your bag outside the hotbar, equipped slots, and offhand — favorites are skipped. |
| `/salvage favorite` | all | Toggle the favorite flag on the held armor piece (favorited gear can never be salvaged). |
| `/dung stats` | all | Prints the profile: class, coins, shards, deaths, best floor, kills, clears. |
| `/dung class <w\|m\|r>` | all | Sets the class for the next run; persists immediately. |
| `/dung give <t>` | `dung.admin` | Debug: `rareweapon`, `heal`, `coins`. |
| `/dung help` | all | Shows clickable chat actions (`ChatUI.startPrompt`). |
| `/party create` | all | Create a new party (you become leader). |
| `/party invite <player>` | all | Invite a player to your party (leader only). |
| `/party accept` | all | Accept a pending party invite. |
| `/party decline` | all | Decline a pending party invite. |
| `/party leave` | all | Leave your current party. |
| `/party kick <player>` | all | Kick a member from the party (leader only). |
| `/party disband` | all | Disband the party entirely (leader only). |
| `/plots` | all | Teleport to the plots world. |
| `/plots warp <name>` | all | Teleport to a named plot. |
| `/plot claim` | all | Claim the next available plot (costs 250 shards or 150 coins). |
| `/plot home` | all | Teleport to your own plot. |
| `/plot name <name>` | all | Name your plot (globally unique). |
| `/plot warp <name>` | all | Teleport to your named plot. |
| `/plot unclaim` | all | Abandon your plot (frees it for re-claiming). |
| `/dung bossbar` | `dung.admin` | Remove any stuck boss bars from the server. |
| `/room tutorial [next|back|reset|skip <n>]` | all | Walk-through tutorial for the room editor (replayable). |
| `/room open` | all | Teleport to the room editor world. |
| `/room new <id> [types]` | all | Create a new room template. |
| `/room pos1` / `/room pos2` | all | Mark selection corners for bounds / spawn floors. |
| `/room region` | all | Add the selection as a bound cuboid. |
| `/room conn <dir> [type] [w] [h]` | all | Add a doorway connection. |
| `/room playerspawn` | all | Set the player spawn marker at your position. |
| `/room spawnfloor` | all | Add the selection as an enemy spawn floor. |
| `/room capture` | all | Snapshot blocks inside all bound regions. |
| `/room validate` | all | Validate the template (must pass before export). |
| `/room export <id>` | all | Write the asset + manifest to `plugins/Dung/rooms/`. |
| `/room testlocal` | all | Test the in-progress template in a test pad. |
| `/room list` | all | List registered production room templates. |

`/dung give` is a free admin debug surface: `rareweapon` now spawns a persistent weapon at no
coin cost (real purchases go through `/shop weapon`); `/dung give coins` drops 10 gold-nugget
run-coin pickups; `/dung give heal` calls vanilla `setHealth(20)`.

### Persistent economy

- **Persistent coins** — earned by beating bosses (banked each floor), survive death, and are
  spent in `/shop` on gear that persists between runs.
- **Shards** — earned in a run by breaking armor with `/salvage` (held piece) or `/salvage all`
  (every armor piece in the bag outside hotbar/equipment). Value scales with rarity and defense.
  Favorited pieces (`/salvage favorite`) are always skipped by salvage. Spent in `/upgrades` on
  permanent stat upgrades: **Permanent Damage** (+1/lv),
  **Max Hearts** (+5/lv), **Defense** (+1/lv), **Crit Chance** (+0.5%/lv), **Move Speed**
  (+3%/lv), **Max Mana** (+5/lv). Each has a rising cost and a level cap (see
  [meta → Upgrades](#meta--persistent-progression) for exact curves).

---

## Package reference

### `com.lieyabull.dung` — plugin root

**`Dung`** (extends `JavaPlugin`) — plugin lifecycle & shared accessors.

- `onEnable()` — saves default config, loads `MetaManager`, builds `GameManager`, registers
  `GameListener`, `RoomTutorial`, and `ShopUI`, and binds `DungCommand` to `/dung`, `/dungeon`,
  `/shop`, `/upgrades`, `/salvage`, and `/party`.
- `onDisable()` — shuts down the run (`game.shutdown()`) and saves meta.
- `world()` / `resolveWorld()` — **lazy** world resolution: skips the End, falls back to the
  first world. Resolved lazily to avoid NPEs during `onEnable` before worlds load.
- `instance()` — static singleton accessor.
- `game()` / `meta()` — accessors to the managers.

### `dungeon` — floor & room generation

**`Floor`** — pure-data room grid for one floor.

- `key(x, z)` — packs `(x,z)` into a `long` map key (`x*4096 + z`).
- `add`, `at`, `inBounds`, `rooms`, `roomCount` — graph accessors.
- `RoomNode` — one room: grid coords, `RoomType`, interior `sizeW/sizeH`
  (13×13 square or 17 long), connectivity `doors[4]` (N/E/S/W), and flags `cleared`,
  `visited`, `looted`, `shopBought`.
  - `randomizeShape(rng)` — mostly square, occasionally elongated along one axis.

**`FloorGenerator`** — builds the Isaac-like branching graph.

- `generate()` — a random walk carves `roomCount` connected rooms with no overlaps; a
  backtrack escape prevents trapping the walk. A BFS from START finds the **farthest room**,
  which becomes the BOSS. A branching pass forks up to 2 dead-end leaves so the floor is a
  real tree, not a snake. Placement then guarantees exactly one `SHOP` (shallow), `TREASURE`,
  `SECRET` (a deep single-door dead-end, then **disconnected** from the door graph and wired to
  a `secretParent` combat room with a destructible wall), `ELITE` (deepest remaining combat
  room), and 1–2 `LOCKED` (key-gated dead-ends), all distinct, non-boss rooms.

**`RoomType`** — enum mapping each room to its `kind` (0–7) and label. The kind index drives
loot-table odds and difficulty. Kinds: `START` (0), `COMBAT` (1), `TREASURE` (2), `SHOP` (3),
`SECRET` (4, bomb-through-wall, detached from the door graph), `ELITE` (5), `BOSS` (6), and
`LOCKED` (7, key-gated behind an iron-block barrier).

**`RoomGen`** — projects a `RoomNode` into the world at `BASE_Y`.

- `baseFor(n, spacing)` — base coordinate of a room (`n.x*spacing, n.z*spacing`).
- `build(w, n, baseY, spacing)` — hollows a walled, ceilinged, floored room. **Boss rooms**
  read differently: deepslate-brick walls, polished-blackstone floor, red `SHROOMLIGHT`
  ceiling + a `REDSTONE_BLOCK` warning tile at each doorway. Carves 3-wide door passages
  whose **perpendicular center is anchored to a fixed offset** (`PERP_CENTER`) shared by all
  rooms, so a square (13) + long (17) pair carve the same tunnel line. Fills the corridor gap
  with solid stone (except the 3-wide passage) so corridors read as tunnels, hangs a lantern
  in odd-length corridors, and lights interiors with ceiling `GLOWSTONE`.
- `center(w, n, baseY, spacing)` — the exact room floor center (+1 for the player).
- Constants: `SQUARE=13`, `LONG=17`, `WALL=1`, `ROOM_HEIGHT=4`, `PERP_CENTER=9`.

### `room` — room editor & template system

**`RoomEditor`** — coordinates the room-editor subsystem: the isolated editor world, per-player
authoring sessions, the production registry, and asset export.

- `openEditor(p)` — teleports the player to the editor world (flat, no mobs, no weather).
- `session(p)` — returns the `RoomEditSession` for a player (created on first access).
- `export(tpl)` — writes the template JSON + a human-readable manifest to `plugins/Dung/rooms/`.
- `registry()` — the `RoomRegistry` of production templates loaded from JAR resources.

**`RoomEditSession`** — per-player authoring state for one in-progress room template.

- `template()` — the `RoomTemplate` being edited.
- `pos1`/`pos2` — world-coordinate selection corners for bounds / spawn floors.
- `newRoom(id, types)` — creates a fresh template with the given id and room types.
- `addRegion()` — adds the current selection as a bound cuboid.
- `addSpawnFloor()` — adds the current selection as an enemy spawn floor.
- `addConnector(dir, type, width, height)` — adds a doorway connection at the player's position.
- `setPlayerSpawn()` — sets the `PLAYER_SPAWN` marker at the player's position.
- `setShopkeeper()` — sets the `SHOPKEEPER` marker at the player's position.
- `addMarker(type, name)` — adds a generic marker at the player's position.
- `capture()` — snapshots all blocks inside bound regions into the template.
- `info()` — returns a summary of the in-progress template.

**`RoomTemplate`** — pure-data room definition: bounds, blocks, connectors, spawn floors, markers.

- `id`, `types`, `description`, `version`, `validated` — metadata.
- `bounds` — list of `RoomBounds` cuboids defining the room's volume.
- `blocks` — list of `RoomBlock` entries (x, y, z, block state string).
- `connectors` — list of `RoomConnector` entries (direction, type, position, width, height, floorY, clearance).
- `spawnFloors` — list of `SpawnFloor` cuboids where enemies can spawn.
- `markers` — list of `RoomMarker` entries (type, position, optional name).
- `total()` — computes the overall bounding box across all regions.
- `connectorFacing(dir)` — finds a connector on the given direction.
- `markersOf(type)` — filters markers by type.

**`RoomIo`** — JSON serialization/deserialization for `RoomTemplate` (Gson-based).

**`RoomTemplateRotator`** — creates a rotated copy of a `RoomTemplate` so its connectors align with
the door directions required by the floor graph.

- `requiredRotation(template, openDoors)` — determines the rotation (0&deg;/90&deg;/180&deg;/270&deg; CW)
  needed to align the template's connectors with the room's open door directions. Returns null if no
  rotation makes all doors match.
- `rotate(template, rotation)` — creates a rotated copy of the template, transforming blocks,
  connectors, bounds, markers, and spawn floors around the template's vertical center axis using
  double-precision arithmetic for symmetric results on odd-dimensioned templates.
- `Rotation` enum — `NONE`, `CW_90`, `CW_180`, `CW_270` with helper methods `applyToDirection(dir)`
  and `applyInverseToDirection(dir)`.

**`RoomValidator`** — validates a room template for self-containment, connectivity, and safety.

- `validate(tpl)` — returns a `Result` with `valid` flag and a list of `Issue`s.
- Rules: all blocks must be inside bounds, all connectors must be open (air), player spawn must
  be on a solid floor with headroom, spawn floors must be solid, no disconnected geometry.
- SECRET rooms may lack a player spawn and connectors (they're entered via a destructible wall).

**`RoomTester`** — tests a room template by instantiating it in a test pad.

- `test(tpl, p)` — builds the room at a safe offset, verifies connectors are open, checks the
  spawn is safe, and spawns a test enemy. Returns a `Result` with feedback lines.

**`RoomTutorial`** — walk-through tutorial for the room editor. Guides a player step by step
through building, capturing, validating, exporting, and testing a room template.

- `start(p)` — begins or resumes the tutorial.
- `next(p)` / `back(p)` — advance or go back one step.
- `skipTo(p, n)` — jump to a specific step (1-based).
- `reset(p)` — restart from the beginning.
- Auto-advances when the player runs the expected command (e.g. `/room open` advances to step 2).
- Triggered with `/room tutorial`; replayable any number of times.

### `entity` — enemies & AI

**`MobType`** — the enemy catalog. Each type has a base HP/damage/speed, an `ai` behavior kind,
and an `id`. Elite variants use `id >= 100`.

- `isElite()` — `id >= 100`.
- `hpAt(floor)` — `baseHp * (1 + floor*0.5)`.
- `damageAt(floor)` — `baseDamage * (1 + floor*0.15) * 5` (the ×5 matches the 100-HP pool; was ×10).

| Mob | AI kind | Behavior |
|---|---|---|
| Gaper / Elite Gaper | 1 | contact: approaches and melees at <1.8 |
| Fly | 2 | fast contact, faster movement |
| Spider / Elite Charger | 3 / 5 | Spider = contact; Charger = telegraphed dash |
| Mulliboom | 4 | burst-range, attacks at <2.2 |
| Charger | 5 | charger dash (windup, lunge, cooldown) |
| Maw | 6 | long-range, attacks at <3.2 |

**`Enemy`** — a runtime mob: a real vanilla `Zombie`/`Phantom`/`Pig`/`Blaze` spawned and tagged
`dung.entity`, with its own HP tracked independently of the mob's native health.

- `Enemy(...)` — spawns the mob, sets `maxHealth`, applies infinite slowness (mobs are steered
  by `tick`, not vanilla AI), sizes/skews the model, and shows a `Name hp/maxHp` bar.
- `tick(p, deltaMs)` — per-tick steering: knockback, post-attack freeze, charger dash
  (windup → lunge → cooldown), then homing movement toward the player. Incremental movement
  (small capped steps) so mobs can't teleport through walls. Attacks only when in range and off
  cooldown, then freezes ~0.5s to give a dodge window. Knockback freezes ~0.4s and zeroes any
  residual velocity before homing resumes, so the physics push doesn't snap into teleporting.
- `damage(dmg, source, dx, dz)` — applies damage, knockback, updates the name bar, plays hit
  feedback (`ENTITY_PLAYER_HURT` sound + `CRIT` particles) on non-lethal damage, and triggers
  the death poof + sound when defeated.
- `alive()`, `despawn()`, `playDeathAnimation()`, `deathSound()`, `faceTarget()`,
  `isWalkable()` (walls include boss-room deepslate; floors stay walkable).

### `items` — gear, rarity & loot

**`ItemTags`** — the single source of truth for every PDC tag key. Centralizing them turns
typos into compile errors instead of silent save incompatibility. All tags live under the
**vanilla** namespace (`NamespacedKey.minecraft(...)`) for save compatibility.

| Key | Type | Meaning |
|---|---|---|
| `dung.gear` | string | `"true"` — marks an item as Dung gear |
| `dung.persistent` | string | `"true"` — bought with persistent currency; survives death |
| `dung.favorite` | string | `"true"` — protected from salvage |
| `dung.kind` | string | `"weapon"` or `"armor"` |
| `dung.base` | string | base id (e.g. `longsword`, `iron_2`) |
| `dung.rarity` | string | `Rarity.name()` |
| `dung.damage` | int | weapon damage |
| `dung.reach` | double | weapon melee reach override |
| `dung.defense` | int | armor defense |
| `dung.health` | int | health affix (adds max hearts) |
| `dung.ability` | string | weapon ability id |
| `dung.cost` | int | ability mana cost override |
| `dung.runitem` | string | `"key"` or `"bomb"` — marks a hotbar run item |

**`Rarity`** — SkyBlock-style enum: `COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`,
`MYTHIC`. Each carries a text color, a `statMult` (damage/defense multiplier), a `floorUnlock`
(earliest fractional floor it may appear), and a `baseChance`.

**`GearFactory`** — builds rarity-colored `ItemStack`s with lore.

- `weapon(id, name, mat, r, dmg, health, ability, cost)` — tags GEAR/KIND/BASE/RARITY/DAMAGE
  (+HEALTH/ABILITY/COST), hides attributes/enchants, adds an enchant to rare+ items, and writes
  damage/health/ability lore.
- `withReach(s, reach)` — attaches a `dung.reach` tag.
- `armor(id, name, mat, r, defense, health)` — tags defense/health, builds armor lore.
- `weaponLore`, `armorLore`, `usage(ability)` — lore/helper text.

**`ItemPool`** — template + roll logic.

- Weapon templates (Frayed Blade, Crude Axe, Longsword, War Hammer, Crystal Shard, Arcane
  Staff, Doomblade) with base damage, ability, and mana cost.
- Armor base sets (Cloth → Netherite) with per-material defense.
- `rollRarity(floor)` — rarity is eligible once `floor >= floorUnlock`; weights are
  `baseChance * (1 + floor*0.05 * ordinal * 0.5)` so deep floors push toward rarer tiers (uncapped)
  while low floors keep COMMON as the most common tier.
- `randomWeapon(floor)` / `randomArmor(floor, slot)` — roll a template + rarity, scale damage/
  defense by `statMult`, add reach (Longsword 3.8, Arcane Staff 4.5, Doomblade 4.0) and health
  affixes.
- `roomReward(floor, roomKind)` — chance to drop gear per room kind (treasure/shop/elite/boss
  always drop; combat 30%; secret 55%) plus coins.

**Health affix roll** — `rollWeaponHealth` (bruiser melee weapons only: war hammer, doomblade,
longsword, crude axe) and `rollArmorHealth` (per material-tier × slot weight {head .65, chest
1.0, legs .80, boots .55} × rarity). All applied to the max-heart pool.

### `game` — run state, combat & lifecycle

**`Run`** — per-run mutable data lost on death: `rng` (seeded), `floorIndex`, `floor`,
`startNanos`, `runCoinsEarned`, `bankedCoins`, `kills`, and per-player `PlayerState` objects.
Gear lives in the inventory, not here.

**`PlayerState`** — the live MMORPG stats + resource bars (single source of truth). Fields:
`maxHearts`/`hearts` (100 base), `mana`/`maxMana`, `manaRegen`, `coins`, `keys`, `bombs`,
combat stats (`damage`, `defense`, `reach`, `critChance`, `critMult`, `speedMult`,
`fireRateTicks` (default 3, reduced from 12)), `classId`, `cooldowns`, `invulnUntil`, `dead`.

- `recomputeStats()` — rebuilds combat stats from held weapon + 4 armor slots (SkyBlock style):
  damage from mainhand, reach override, rarity crit/knockback, defense/health from armor, then
  class passives, then permanent shard upgrades. The health affix is applied as a **symmetric
  reservoir** (see below).
- `applyClassPassives()` — warrior: ×1.15 damage, +2 defense; mage: 160 max mana, 8 mana/s;
  ranger: +10% crit, faster fire rate. Resets mana baselines first so class swaps never leave a
  stale pool.
- `applyUpgrades()` — folds the player's purchased permanent upgrades (track id -> level, loaded
  from meta at run start) on top of gear + class passives: damage, defense, crit, speed, and max
  mana; the hearts upgrade feeds into the max-heart reservoir.
- `isInvuln()`, `hurt(dmg)` — invuln check; damage mitigated by defense
  (`dmg * 100/(100+def)`, min 1), 500ms i-frame after each hit, records `lastDamageTime` (gates
  natural regen), sets `dead` at ≤0.
- `hasDamageBoost()` / `hasGuaranteedCrit()` — class ability buffs: War Cry applies a
  timed damage multiplier; Shadow Step applies a timed guaranteed crit.
- `heal(amount)` — clamp to max.
- `regenMana()` — per-tick mana regen (rate/20).
- `regenHearts()` — **out-of-combat** HP regen: only when alive, not at max, and >5s since the
  last hit (`HEAL_DELAY_SECONDS`); rate `healPerSecond` (default 2/s), 0 disables it.
- `canCast(id, cost, cdMs)`, `spendMana`, `startCooldown` — ability resource/cooldown gating.
  A player-scoped **global cooldown** (`GCD_KEY`/`GCD_MS`, 400ms) is shared by every weapon and
  class ability so rapid weapon-swapping can't bypass per-ability cooldowns and burst-stack casts.
- `bestEquipRarity()` — highest rarity across equipped gear.

**`GameManager`** — registry of `DungeonInstance`s, routes events per player.

- `startRun(party, seed)` — creates a new `DungeonInstance` for the party, generates the first
  floor, and teleports all party members to the START room.
- `instanceOf(p)` — returns the `DungeonInstance` the player is currently in, or null.
- `leaveInstance(p)` — removes the player from their instance (calls `endRun` if party becomes
  empty).
- `tick()` — iterates all active instances and ticks each one.
- `shutdown()` — ends all active instances on plugin disable.

**`DungeonInstance`** — one active dungeon per party. Owns the lifecycle, combat, rooms, enemies,
boss, HUD, and world cleanup for a single run.

Lifecycle:
- `startRun(party, seed)` — resolves world, creates `Run` + per-player `PlayerState` (loads
  class + permanent upgrades, applies held gear), sets up scoreboards, grants starter kits,
  fires tutorial (including key/bomb hotbar instructions) for new profiles, then `enterFloor(0)`.
- `enterFloor(i)` — randomizes spacing (22–28), generates + builds the floor, teleports all
  party members to the START room. Templates are rotated as needed to align connectors with
  room door directions; corridors span the full vertical range between template and procedural
  rooms so multi-level templates connect correctly.
- `enterRoom(n)` — marks visited, applies spawn-grace invuln, spawns enemies for un-cleared
  COMBAT/ELITE rooms (locks doors), spawns room pickups, opens the shop GUI on SHOP rooms,
  and checks the boss on BOSS rooms.
- `onPlayerMoved(loc)` — room-crossing detection from movement.
- `descend()` / `endRun()` / `onPlayerDeath(p)` / `resetPlayerToSpawn()` — see the
  [death model](#death--persistence-model).

Combat:
- `tick()` (per game tick): checks death, syncs stats from real gear, drains melee cooldown,
  applies speed, clears rooms when all enemies die, ticks current room's enemies + boss,
  regens mana/HP, syncs the real HP bar proportionally, keeps food low, and throttles the
  action bar.
- `registerAttack()` — melee arc: damages enemies within reach (horizontal + vertical), with
  a wider arc on the boss. Applies class ability damage boosts (War Cry) and guaranteed crits
  (Shadow Step).
- `tryCastAbility(p, item)` / `dispatchAbility(id, st)` — casts the held weapon's stored ability
  if mana + cooldown allow. Abilities: **Rush** `[5,1000]`, **Slash** `[12,2500]`, **Cleave**
  `[15,3000]`, **Smash** `[18,3500]`, **Blade Storm** `[25,4500]`, **Arcane Bolt** `[20,3500]`,
  **Ravage** `[40,8000]`. All casts (weapon + class) respect a shared **400ms global cooldown**
  keyed to the player, so swapping between different-ability weapons cannot stack casts.
- `tryCastClassAbility(p)` / `dispatchClassAbility(id, st, caster)` — casts the player's
  class-specific active ability: **Warrior — War Cry** (10 mana, 8s cd: party damage boost +
  invuln), **Mage — Arcane Nova** (25 mana, 6s cd: AoE 2x damage), **Ranger — Shadow Step**
  (15 mana, 5s cd: teleport behind nearest enemy + guaranteed crit). Triggered by sneak+drop (Q).
- `openShop(p)` — opens the chest GUI shop for the player in a SHOP room.
- `tryUnlockRoom(p, loc)` — right-click an IRON_BLOCK barrier with a key item to unlock a
  LOCKED room (consumes 1 key, spawns pedestal loot).
- `tryBombWall(p, loc)` — right-click a CRACKED_STONE_BRICKS wall with a bomb item to blast
  open a hidden SECRET room (consumes 1 bomb, reveals pedestal loot).
- `syncHotbarItems(p)` — locks key (TRIPWIRE_HOOK) and bomb (TNT) items into hotbar slots 7-8,
  synced every tick; items can't be dropped or moved. Empty slots get a `BLACK_STAINED_GLASS_PANE`
  tagged as a run item to prevent duplication via inventory click/drag.
- `makeEmptySlotItem()` — creates a black stained glass pane marked with `dung.runitem` so
  inventory click/drag handlers block moving it (prevents tick-by-tick duplication).
- `tryCastAbility(p, item)` — after casting a weapon ability, persistent weapons lose 1-2
  random durability via `GearFactory.damageItem()`; broken items are removed from the main hand.
- `endRun()` — now calls `damagePersistentGear(p)` for each online member after inventory
  restore, so persistent items take durability damage when the run ends normally.
- `reviveDeadPlayers()` — resets `PlayerState.dead = false` after removing from `deadPlayers`,
  so the tick loop doesn't re-trigger `onPlayerDeath` on the next cycle.

Rooms/rewards:
- `onRoomClear(n, k)` — clears the room, opens doors, awards coins + gear.
- `onRoomEnterBossCheck()` / `onBossDefeated()` — spawn/despawn the Warden, open doors, drop
  guaranteed rare+ loot, bank coins into persistent wallet (delta capped at 40).
- `stripRunGear(p)` — removes run loot (Dung gear without `dung.persistent` + coin nuggets)
  from storage/armor/offhand, plus key/bomb hotbar items; permanent purchases survive.
  Runs on death and quit.

Utilities:
- `playerHurt(p, dmg)` (static) — applies `PlayerState.hurt` + red-hurt animation/sound.
- `clearRoomEntities()` / `tearDownDungeon()` — despawn mobs, unseal barriers, reset blocks.

### `boss` — the Warden

**`BossController`** — a large, telegraphed floor boss with a `KeyedBossBar`. Spawns a Zoglin
tagged `dung.entity`, anchored in the boss room. `tick()` runs an attack state machine: the boss
holds still while warning, then fires:

- **ATTACK_BEAM** — a red lane telegraphed toward the player (dodge perpendicular).
- **ATTACK_SLAM** — hits within 3 blocks.
- **ATTACK_RADIAL** — enrage-only (below 50% HP): expands a ring, hits within 5 blocks.

A contact sting damages if you walk into the boss. `enraged()` past 50% HP speeds up patterns and
raises damage. `damage(dmg)` updates the boss bar, plays hit feedback (`ENTITY_PLAYER_HURT`
sound + `CRIT` particles), and at 0, despawns + calls `GameManager.onBossDefeated()`.

### `listener` — Paper event wiring

**`GameListener`** — routes Paper events to the game, only for the active run's player.

- `onJoin` — restore players to their last saved location (world, coords, yaw/pitch from
  `MetaProfile`) on rejoin; first-time players go to world spawn. Shows the help prompt.
- `onCreatureSpawn` — suppress natural/world mob spawns inside the run world while running
  (Dung mobs are `CUSTOM`-reasoned so they're unaffected).
- `onMove` (MONITOR) — room-crossing detection.
- `onDeath` / `onRespawn` — clean death teardown; force respawn at world spawn (see
  [death model](#death--persistence-model)).
- `onHeldItem` / `onArmor` / `onInteract` — recompute stats on gear change; block block-place;
  cast abilities on sneak+right-click; open the chest GUI shop on the emerald block;
  unlock locked doors with key item on IRON_BLOCK; bomb destructible walls with bomb item
  on CRACKED_STONE_BRICKS; claim pedestal loot on POLISHED_BLACKSTONE_SLAB (checked before
  armor-equip detection so holding an armor piece doesn't equip it when clicking a pedestal).
- `onAttack` — left-click triggers `registerAttack()` and cancels the vanilla hit.
- `onEnemyDamage` — **cancels all vanilla damage from Dung entities** (mobs + their projectiles,
  via `isDungSource`) so only `PlayerState`-based damage applies.
- `onPickup` — intercepts pickups (heart/coin/key/bomb) and applies their effect.
- `onDropItem` — sneak+drop (Q) casts class ability; non-sneak drop of key/bomb run items
  is cancelled to keep them locked in the hotbar.
- `onQuit` — saves the player's current location to `MetaProfile` (for rejoin restoration),
  then ends the run (clears inventory) on logout.

### `meta` — persistent progression

**`MetaManager`** — permanent progression in `saves.yml`, with **atomic writes** and **corrupt
backup**.

- `load()` — on a corrupt save, renames the file to `saves.yml.corrupt-<ts>` for recovery
  instead of silently wiping it.
- `save()` — writes to a temp file then atomically moves it over the target. Persists coins,
  shards, per-track upgrade levels, deaths, clears, class, kills, and best floor.
- `profile(uuid)` — lazily creates/loads a `MetaProfile` (`persistentCoins`, `shards`,
  `upgrades`, `deaths`, `clears`, `classId`, `kills`, `bestFloor`, `lastWorld`/`lastX`/`lastY`/
  `lastZ`/`lastYaw`/`lastPitch` for rejoin location restoration).
- `addPersistentCoins(uuid, amount)` — permanent coins that survive death.
- `MetaProfile` — per-player data; `upgrades` is a `track id -> level` map.

**`Upgrades`** — the permanent stat-upgrade catalog: 6 tracks (`damage`, `hearts`, `defense`,
`crit`, `speed`, `mana`), each with a label, `baseCost`/`costPerLevel` price curve, a `maxLevel`
cap, and the per-level stat `delta`.

- `byId(id)` — look up a track by id.
- `cost(t, level)` — shard cost of the next level (`baseCost + costPerLevel * level`).
- `delta(t)` — permanent stat gained per level.

Current curves (rebalanced for the high per-floor income):

| Track | Delta/lv | baseCost / costPerLevel | Cap | Total to max | Max stat |
|---|---|---|---|---|---|
| Damage | +1 | 6 / 3 | 15 | 405 | +15 dmg |
| Hearts | +5 | 8 / 4 | 15 | 540 | +75 HP |
| Defense | +1 | 8 / 5 | 15 | 645 | +15 def |
| Crit | +0.5% | 8 / 4 | 15 | 540 | +7.5% |
| Speed | +3% | 10 / 6 | 8 | 248 | +24% |
| Mana | +5 | 6 / 3 | 15 | 405 | +75 mana |

Maxing all six tracks costs **2,783 shards** (~28–46 floors at typical income).

The upgrades are applied in `PlayerState.applyUpgrades()` (after class passives):
damage +1/lv, max hearts +5/lv, defense +1/lv, crit +0.5%/lv, move speed +3%/lv, max mana +5/lv.

### `pickup` — floor pickups

**`Pickup`** — floor-pickup identity via raw `Material` (no literal item copying).

| Material | Type | Effect |
|---|---|---|
| `RED_DYE` | HEART | heals 8 HP |
| `GOLD_NUGGET` | COIN | +1 run coin |
| `TRIPWIRE_HOOK` | KEY | +1 key (used to unlock LOCKED rooms) |
| `TNT` | BOMB | +1 bomb (used to blow through SECRET room walls) |

- `isPickup(m)` / `typeOf(m)` / `apply(m, st)` / `stack(m)`.

### `ui` — HUD, tab menu & chat

**`HUD`** — sidebar scoreboard with combat stats, run consumables (keys/bombs show hotbar slot
hints `[slot 7]`/`[slot 8]`), current room, boss status, class, and the longest running ability
cooldown. `sendBar` paints the action bar:
`♥ <hearts>/<maxHearts> (<pct>)   ✦ <mana>/<maxMana>` (integer counts + percent).

**`TabUI`** — tab menu (player-list slot) with layered detail: header (floor + class), combat
stats, mana/speed/fire-rate, equipment (mainhand + 4 armor slots), and dungeon exploration
status (rooms explored/cleared, boss state).

**`ChatUI`** — clickable chat actions (`startPrompt`), notifications, and the reusable
`command(label, command, hover)` builder.

---

## Combat & stats model

- **HP** — a 100-HP base pool tracked in `PlayerState.hearts`, raised by gear health affixes and
  the hearts upgrade. The vanilla heart bar is a **proportional projection**
  (`hearts/maxHearts × 20`), so a full bar always means full health regardless of pool size
  (earlier builds mapped absolute hearts/5, which showed a full bar at 40% once the pool passed
  100). The action bar adds an exact numeric + percent readout.
- **Damage** — melee arc damages enemies within `reach` (horizontal) and 2 blocks (vertical).
- **Defense** — reduces incoming damage: `dmg * 100/(100+defense)`, minimum 1.
- **Critical hits** — `critChance` chance of `critMult` damage (rarity pushes both).
- **Speed** — `speedMult` scales walk speed (`min(0.3, 0.2*speedMult)`).
- **Mana** — regenerates per second; spent on weapon abilities with per-ability cooldowns. A
  shared **400ms global cooldown** applies to all ability casts so weapon-swapping can't stack them.
- **Permanent upgrades** — shard-bought levels from `/upgrades` add damage, max hearts, defense,
  crit chance, move speed, and max mana on top of gear and class every run.
- **Natural healing** — out-of-combat regen (`healPerSecond`, 5s after damage). Vanilla hunger
  regen is suppressed by pinning food to 10 / saturation 0.
- **Health affix (reservoir)** — a gear health bonus raises `maxHearts`. Growing the pool heals
  the gained amount; shrinking it refunds exactly that amount, so rapid gear-swapping cannot
  farm free HP. This is the key anti-exploit detail of the health system.

### Classes

| Class | Passive |
|---|---|
| Warrior | ×1.15 damage, +2 defense |
| Mage | 160 max mana, 8 mana/s |
| Ranger | +10% crit, faster fire rate |

---

## Death & persistence model

There are **two distinct death paths**, handled differently so the player is never stranded on
the vanilla death screen:

1. **Dung-system death** (`st.dead`) — when `PlayerState.hurt` drops hearts to ≤0. The real HP
   bar never hits 0 (it's a projection), so there's no vanilla death screen. `tick()` calls
   `onDeath()` then `resetPlayerToSpawn()`, which revives + teleports to world spawn.
2. **Vanilla death** (void/fall/suffocation) — the real player is dead and the death screen
   shows. `tick()`/`onDeath` tear down the run and force `player.spigot().respawn()`; the
   `onRespawn` handler sets the respawn location back to world spawn. Crucially, this path does
   **not** teleport/revive the player while they're still on the death screen.

**What's kept vs. lost:**
- Kept: class, persistent coins, **shards**, purchased **upgrade levels**, clears, best floor,
  kills, and any items bought with persistent coins (stamped `dung.persistent` by `/shop` or
  `give`) — these are never destroyed, and they survive `/dung leave` too.
- Lost: run coins and run gear. On death or quit, `stripRunGear()` physically removes every
  Dung item **without** `dung.persistent` plus the run's coin nuggets — so a death really
  restarts you (and the message is true). Run gear can be converted before a death by breaking
  it with `/salvage` into permanent shards.

Persistent coins are **banked** on boss defeat from the delta earned that floor (capped at 40),
so the same run coins aren't re-banked on every floor. Shards earned via `/salvage` are added
immediately and spent in `/upgrades`.

**Onboarding:**
- Empty-handed players get a **starter kit** (Frayed Blade + cloth set) each new run — it's run
  loot, so death-strip reclaims it and it never duplicates.
- First-run profiles (`hasSeenTutorial`) get a one-time title + chat tutorial covering attack,
  ability input (sneak + right-click), heart pickups, `/salvage`, and `/dung leave`.

---

## Design notes & known issues

- **Multi-player parties, parallel dungeons.** `GameManager` is a registry of `DungeonInstance`s.
  Each party gets its own instance with its own floor, enemies, boss, and per-player state.
  Multiple parties can run dungeons simultaneously in the same world at different coordinates.
- **Dungeon placement** — everything is built at `BASE_Y = 80` in the first non-End world and
  fully torn down (`tearDownDungeon`) when a run ends, so no persistent world edits remain.
- **Vanilla mobs as enemies** — Dung mobs/boss are real vanilla entities whose native AI is
  suppressed (slowness + manual steering), and all their vanilla damage is cancelled
  (`onEnemyDamage`/`isDungSource`). Their HP is tracked independently; the mob's own health bar
  is only cosmetic.
- **Fixed corridor geometry** — door passages and barriers share the fixed `PERP_CENTER` line so
  square + elongated neighbours carve aligned tunnels. `spacing` (22–28) leaves a 3–9-block
  corridor for any shape.
- **Health reservoir** prevents the gear-swap heal exploit; natural healing is out-of-combat
  only.
- **Keys and bombs** — purchasable in the in-run shop GUI for 4 coins each, and tracked in
  `PlayerState`. Both now have real sinks: **keys** unlock `LOCKED` rooms (dead-end rooms sealed
  by an iron-block door barrier), and **bombs** blow through the `CRACKED_STONE_BRICKS`
  destructible wall of a hidden **SECRET** room.
- **Bomb-through-wall secrets** — a `SECRET` room is fully detached from the door graph. Its
  `secretParent` combat room gains a visible cracked-wall segment; right-clicking it while holding
  a bomb consumes 1 bomb, blasts a 3-wide opening, and reveals pedestal loot. This is the "true"
  hidden secret (no visible doorway).
- **Locked rooms** — `LOCKED` rooms sit on dead-end branches behind an `IRON_BLOCK` barrier.
  Crossing the threshold with a key spends 1 key, unlocks + clears the room, and spawns loot.
- **Pedestal loot** — treasure/locked/clear rewards spawn on a `POLISHED_BLACKSTONE_SLAB`
  pedestal with an invulnerable, invisible item frame; right-click to claim. Pedestals are torn
  down with the run (`clearPedestals`).
- **Persistent gear durability** — on death each `dung.persistent` item loses 10% of max
  durability (min 1); a piece that breaks is removed from the inventory. Death now costs
  persistent gear, not just the run.
- **Tab throttle** — `TabUI.refresh` runs every 10 ticks; only the HUD action bar keeps a
  per-tick cadence.
- **Return to pre-run location** — `/dung leave` and mid-run death teleport you back to where
  you were before starting the run (recorded per-player in `returnLocs`).
- **Rejoin location persistence** — Players who quit return to the same world they left from
  (e.g. plots world), saved in `MetaProfile.lastWorld`/`lastX`/`lastY`/`lastZ`/`lastYaw`/`lastPitch`.
- **Persistent gear durability on run end** — Persistent items now take durability damage when
  the run ends normally (not just on death), applied after inventory restore so the damage sticks.
- **Weapon ability durability cost** — Persistent weapons lose 1-2 random durability each time
  their weapon ability is used, saved after the run ends.
- **Hit feedback** — Enemies and the boss play `ENTITY_PLAYER_HURT` sound + `CRIT` particles
  when taking non-lethal damage, so players get audio-visual feedback.
- **Pedestal armor equip fix** — Picking up rewards from a pedestal while holding an armor piece
  no longer equips the armor (pedestal check runs before the armor-equip check).
- **Player attack cooldown reduced** — Player `fireRateTicks` reduced from 12 to 3 (quarter of
  original); monster attack cooldown set to 12 ticks (the old player value).
- **Stained glass pane duplication fixed** — The black stained glass pane in empty key/bomb
  hotbar slots is now tagged as a run item, so inventory click/drag handlers block moving it.
- **Revived players no longer re-die** — `reviveDeadPlayers()` now resets `PlayerState.dead`
  so the tick loop doesn't re-trigger `onPlayerDeath` on the next cycle.
- **Plot crop trampling prevented** — `PlotListener` cancels `EntityChangeBlockEvent` when a
  player tramples farmland on a plot they don't own.

### Tests

Headless JUnit tests under `src/test` cover the pure-logic paths that don't need a live Bukkit
server: corridor geometry (`CorridorGeometryTest`), simulated floor generation where an agent
clears every generated floor including bomb-disconnected `SECRET` rooms
(`SimulatedPlayerFloorTest`), rarity distribution across floors (`ItemPoolTest`), per-player
stats (`PlayerStateTest`), permanent upgrades (`UpgradesTest`), and party lifecycle
(`PartyManagerTest`). Run with `.\gradlew.bat --offline cleanTest test`.
