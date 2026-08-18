# Boss — Boss Bar, Telegraphs, and Combat Feed

## 1. Purpose
The Warden boss fight presents a **BossBar** (`dung_boss_*`, red segmented-10, "The Warden" + HP) plus in-world telegraph particles and chat warnings for its telegraphed attacks. Defeating it opens the way down and triggers the banked-coin + `[Descend]` flow. This is a distinct user-facing "UI" channel (boss bar + telegraph + feed).

## 2. Files / locations
- `src/main/java/com/lieyabull/dung/boss/BossController.java` — all of it.
  - Constructor / boss bar creation line 46–80; `addViewer`/`removeViewer` lines 83/91; `tick` line 97 (warning + telegraph); `fire` line 137; `warnLane` line 170; `warnRing` line 179; `telegraphMsg` line 184; `directionName` line 192; `damage` line 214 (bar progress/title updates); `despawn` line 277.
- `DungeonInstance` — `onRoomEnterBossCheck` line 2284; `onBossDefeated` line 2311 (bank + `[Descend]`, clickable component lines 2346–2353); `lockDoors` line 2304.
- `DungCommand.bossbarCmd` line 58 — manual cleanup of leaked `dung_boss_*` bars.

## 3. Structure
- Bar: `Bukkit.createBossBar(barKey, "§4The Warden", BarColor.RED, BarStyle.SEGMENTED_10)` (line 76), progress set from HP (line 217), title updated to `§4The Warden §8<hp>/<max>` (line 218). Added to the target player (line 78) and extra viewers via `addViewer` (party members, line 2301 in DungeonInstance).
- Telegraphs: beam draws a 12-block FLAME lane in the threatened direction (`warnLane`, line 170); slam/radial draw an expanding CRIT ring (`warnRing`, line 179). Chat warning via `telegraphMsg` (line 133).
- On defeat: bar removed (lines 230–232), dead players revived, clickable `[Descend]` chat button.

## 4. Strengths
- Boss bar uses a **plugin-namespaced key** (`NamespacedKey(Dung.instance(), "dung_boss_"+uuid)`, line 75) — correct, unlike the GUI PDC keys.
- Progress + numeric HP title both updated in `damage` (line 217–218) — the bar is never stale.
- `removeAll`/`setVisible(false)`/`removeBossBar` on defeat (lines 230–232) and `despawn` (lines 280–282) cleanly release the bar.
- Per-party HP scaling (`maxHp` line 61) and `addViewer` for each member (DungeonInstance line 2301).
- Telegraph direction resolved from boss→player (`warnAngle`, line 129) — matches the beam that then fires toward the player; a real bug (direction reversed) was previously fixed and is now correct.

## 5. Weaknesses / UX issues
- **Slam vs radial are visually indistinguishable in the warning.** Both produce the same `warnRing` expanding ring (line 179) and the same chat line `"The Warden's core flares!"` (line 189), yet they have different danger radii (slam < 3 blocks, radial < 5 blocks) and different damage. A player cannot tell which is coming and therefore cannot pick the correct safe distance — a genuine telegraphing failure.
- **Beam warning is color/particle-only with no safe-zone indication.** The FLAME lane shows the threatened direction, but there is no marker for where the beam *stops* (12 blocks) or the perpendicular width (2 blocks), so judging "am I clear?" is guesswork.
- **Wardens telegraph only a direction word** ("beam to the South-East", line 187) — helpful for sound-off players but redundant with the visible lane; meanwhile the numeric HP/pit but no enrage notice is shown despite `enrage()` past 50% (line 42) silently increasing attack speed/damage. No "The Warden is enraged!" message or bar-color change, so the difficulty jump is undiscoverable.
- **Chat feed spam.** Every telegraphed attack sends a chat line (`p.sendMessage(telegraphMsg(...))`, line 133) plus hit confirmations (`"beam strikes through you!"` line 148) — during enrage (attackCd 25) this is a line roughly every second, competing with the HUD/action-bar and other feeds.

## 6. Bugs / risks
- **`bar.removeAll()` + `setVisible(false)` + `removeBossBar(barKey)` on defeat (line 230–232) but the constructor does not remove an existing bar with a conflicting key** — keys include a random UUID so collision is negligible, but if two bars were ever created for the same key the second `createBossBar` would throw (not guarded).
- **`warnLane`/`warnRing` and telegraph angles are computed from `boss.getLocation()` every tick** (line 104); if the boss dashes between warning and `fire`, the lane and the fired beam use the *same* `center`/`warnAngle` snapshot (captured at `tick` entry), so they stay aligned — fine.
- `directionName` index math (line 194): `(int)Math.round(angle/(PI/4)) % 8` with negative normalization (line 195) is correct.

## 7. Concrete suggestions (priority order)
1. **Distinguish slam vs radial telegraphs** — different particle color/size or explicit chat text ("The Warden slams!" vs "The Warden erupts!") with distinct radius cues (small, high value for readability).
2. **Add an explicit enrage announcement** (chat + optional bar-color change to a hotter color) so the difficulty spike is communicated (small).
3. **Add a perpendicular/range cue for the beam** (e.g. widen the particle lane or add end markers) so players can judge the safe zone (medium).
4. **Throttle or consolidate the combat chat feed** (telegraph + hit messages) so it doesn't crowd the action bar and HUD (medium).

## 8. Shared-flaw note
The `[Descend]` button and banking messages are produced in `DungeonInstance` with hand-built `§` strings and an inline Adventure Component — same chat-formatting duplication called out in `chat.md` §8. The bar is the one surface that correctly uses a plugin NamespacedKey (contrast `shop.md`/`workstation.md` `minecraft:` misuse).
