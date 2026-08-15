# Dung — Architecture & Contracts

Room-based dungeon roguelite (Binding of Isaac structure) + MMORPG combat/progression
(Hypixel SkyBlock formula). Minecraft's blocks/mobs/items transformed into an RPG system.

## Package layout (shipped)
```
com.lieyabull.dung
  Dung                        main plugin: lifecycle, tick pipeline, command registration, wiring
  game/Run                    per-run state: current floor, room graph, floor index
  game/PlayerState            player runtime: hp/hearts, mana, coins, class, gear-derived stats,
                              recomputeStats (flat dmg/def + rarity crit/speed), hurt/canCast
  game/GameManager            orchestrator: applies changes, runs room transitions (enterRoom /
                              onPlayerMoved), enemy spawn + room confinement, combat AI hooks,
                              ability dispatch (tryCastAbility/dispatchAbility), shop, wiring to UI
  dungeon/RoomType            START, COMBAT, TREASURE, SHOP, SECRET, ELITE, BOSS, CORRIDOR
  dungeon/Floor               2D grid of RoomNode (doors[], cleared, type)
  dungeon/FloorGenerator      Isaac-like branching floor: farthest=BOSS, treasure/shop/elite
                              guaranteed, secret on deep dead-end leaf (verified 800/800 connected)
  dungeon/RoomGen             builds enclosed room + doors (barriers) in the world
  entity/MobType              enemy catalog + AI kind (melee/ranged) + stats
  entity/Enemy                enemy runtime: move/attack, per-enemy melee Y-plane hit probe
  boss/BossController         boss: boss bar, arena, dash/slam telegraphs, multi-phase enrage addon
  items/Rarity                enum COMMON..MYTHIC + colors + stat multipliers (crit/speed)
  items/ItemPool              loot tables per room type + floor scaling (weapon/armor/pickups)
  items/GearFactory           builds ItemStacks with PDC tags + rarity lore
  pickup/Pickup               redeemable ground pickups (coins/heals)
  ui/HUD                      sidebar (health/mana/coins/floor) + actionbar
  ui/TabUI                    tab menu (detailed build/run/progression)
  ui/ChatUI                   clickable chat messages + notifications
  listener/GameListener       event wiring: PlayerMoveEvent, ItemHeld/Armor change recompute,
                              right-click ability + shop, inventory, pickups
  meta/MetaManager            permanent coins/class/unlocks (persisted)
  command/DungCommand         /dung subcommands (start|descend|leave|shop|stats|class|give|help)
```

## Key contracts (shared so subsystems stay consistent)
- Keep ONE Player instance per run (single-player focus; non-participants untouched).
- Stats are computed from gear (SkyBlock-style) via PlayerState.recomputeStats; triggered on
  item-held change and armor-equip/unequip through GameListener. Gear reads the same PDC as
  GearFactory writes (`minecraft:` NamespacedKeys: `dung.ability`, `dung.cost`, `dung.damage`,
  `dung.defense`, `dung.rarity`, etc.).
- Rarity now contributes to crit chance/crit multiplier (weapon + armor) and gear/class affects
  move speed (speedMult applied to walkSpeed each tick).
- Mana + ability cooldowns live in PlayerState; abilities are triggered by sneak+right-click with
  the main hand and dispatched via GameManager.dispatchAbility (Rush/Cleave/Smash/Blade Storm/
  Arcane Bolt/Ravage).
- PlayerState is the single HP source of truth; GameManager.playerHurt applies defense mitigation
  and syncs back to the real player each tick (`setHealth`). Reach combat via static GameManagerRef.
- Items are not raw MC loot: every entity item is tagged via PersistentDataContainer.
- Sync all world edits on the server main thread (tick pipeline); UI refreshed on tick.
- Colors: rarity COMMON=gray, UNCOMMON=green, RARE=blue, EPIC=purple, LEGENDARY=gold, MYTHIC=dark red.
- No external libs beyond Paper API. No comments unless they clarify a non-obvious rule.

## Room types (Isaac pacing)
- START: spawn room; travel gated by adjacency via PlayerMoveEvent (onPlayerMoved -> enterRoom).
- COMBAT (many): clear enemies to unlock doors (real barrier-block sealing via sealDoors); only the
  current room's enemies tick (room confinement).
- TREASURE (1/floor): guaranteed high-tier pedestal loot.
- ELITE (1/floor): mini-boss with guaranteed rare+ loot.
- SHOP (1/floor): emerald block spends run coins on gear, per-floor cap.
- SECRET (hidden, 0-1/floor): reward alcove on a deep dead-end leaf, drops weapon + armor.
- BOSS: arena, boss bar, telegraphs, enrage phase (2 below 50% HP); descend to next floor,
  difficulty scales with floor index.
- CORRIDOR: linking rooms.
```

## Current build state
- Compiles clean via `gradlew --offline compileJava` (only fast compile is used for verification).
- Floor generation invariant-checked offline: 800/800 floors connected, boss reachable,
  treasure/shop/elite guaranteed.
- Iteration 3 done: rarity crit build depth, applied move speed, multi-phase boss enrage,
  hidden secret rooms, plus earlier iteration-2 fixes (real door locking, working melee,
  mana regen, single HP source, boss telegraphs).