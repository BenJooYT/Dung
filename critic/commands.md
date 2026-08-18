# Commands — Chat Command Feedback (DungCommand, PlotCommand, /salvage, /party, leaderboard)

## 1. Purpose
The command-line surface: `/dung`, `/dungeon`, `/shop`, `/upgrades`, `/salvage`, `/party`, `/balance`, `/leaderboard`, `/plot`, `/plots`. These present chat-based UI (stats, balance, leaderboard with pagination, party info/invites, salvage feedback, plot claim). They are a first-class user interface for meta-progression and party management.

## 2. Files / locations
- `src/main/java/com/lieyabull/dung/command/DungCommand.java` — `onCommand` line 36; `dungCmd` line 100; `partyCmd` line 177; `shopCmd`/`upgradesCmd` lines 291/302; `salvageCmd` line 313, `salvageHeld` line 323, `toggleFavorite` line 358, `salvageAll` line 373; `balance` line 460; `leaderboard` line 490; `stats` line 626; `classCmd` line 635; `give` line 650; `bossbarCmd` line 58.
- `src/main/java/com/lieyabull/dung/command/PlotCommand.java` — `onCommand` line 41; `blockIfInRun` line 32.
- `src/main/java/com/lieyabull/dung/plot/PlotManager.java` — `showClaimOptions` line 200; `claimPlot` line 259; `unclaimPlot` line 322; `setNamePlot` line 385.

## 3. Structure
- Single `CommandExecutor` dispatches on the command label (line 46). `dungCmd` switches on the first arg (start/descend/shieldswitch/leave/party/shop/upgrades/salvage/balance/stats/class/give/stop/reset/help).
- Party: create/invite/accept/decline/leave/kick/disband/info, with clickable Accept/Decline (lines 218–223).
- Salvage: `held`/`all`/`fav` modes; per-floor shard counter `run.salvageShards`.
- Leaderboard: 5 categories, paginated by 5, with Prev/Next and category-switcher buttons (lines 490–602).
- Balance/stats: single/multi-line chat dumps.
- Plots: claim (shards/coins), home, name, warp, unclaim, plus `showClaimOptions` clickable purchase buttons.

## 4. Strengths
- Clean, consistent `§c` error + `§a` success convention throughout.
- Leaderboard pagination + category switcher is genuinely usable and rich.
- Salvage protects favorites and starter gear (`isSalvableArmor` line 405), and bulk-salvage correctly limits to the bag (slots 9–35) and excludes persistent/starter/favorited.
- Party invites are clickable and the invite only succeeds if the target is not already in a party (PartyManager).
- Plot claim shows the actual balances before committing (line 217).

## 5. Weaknesses / UX issues
- **`/salvage all` and `/plot unclaim` are destructive with no confirmation.** `salvageAll` (line 373) destroys every salvable bag armor piece in one command with a single message after the fact; `PlotManager.unclaimPlot` (line 322) permanently frees a plot and deletes the starter chest with no confirm. Both should require a confirm (e.g. `/salvage all confirm` or a re-type), especially `/salvage all` which can nuke a full loadout.
- **`/plot unclaim` in a run is not blocked.** `blockIfInRun` guards warp/home/plots but not `unclaim` or `claim` (PlotCommand lines 84–146). Claiming/unclaiming while inside a dungeon run works (no error), which is inconsistent with the other gated plot commands.
- **Class switching is free and instant** (`classCmd` line 635) with no confirmation or cost; since class permanently shapes the run, a fat-fingered `/dung class mage` is an immediate irreversible change (persisted line 646).
- **`/dung give` leaks debug/help text to admins only, fine, but the `coins` option adds GOLD_NUGGET items (line 663)** with no handler to convert them into run coins — a dead-end affordance that does nothing meaningful.

## 6. Bugs / risks
- **`salvageHeld` zero-amount stack bug.** `salvageHeld` (line 346) does `held.setAmount(held.getAmount() - 1)` and never clears the slot. When the held piece's amount reaches 0 (salvaging the last one), a zero-amount stack remains in the main hand instead of the slot being cleared. In Bukkit, an `ItemStack` with amount 0 is an invalid/ghost state and can behave inconsistently (a "broken" empty item the player cannot remove, or tooltip surprises). The slot must be set to `null` (or the item removed) when amount ≤ 0.
- **`blockIfInRun` messaging:** all plot gating returns the same generic "can't use plot warps while in a dungeon run" even for `/plot claim` which isn't gated — inconsistent.
- **Party invite component:** `ChatUI.command(...).append(...).hoverEvent(null)` (lines 219–222) strips the parent hover but the two child buttons retain theirs — correct, but the trailing plain-text line (224) duplicates the invitation message.
- **`/dung start` with a party where the leader is offline** uses `party.leader()` (line 117) — if the offline leader is checked but `Bukkit.getPlayer` isn't used here; `startRun` iterates `party.members()`. Low risk.

## 7. Concrete suggestions (priority order)
1. **Fix the `salvageHeld` zero-amount stack** — clear the slot when amount reaches 0 (small, correctness).
2. **Add confirmation to `/salvage all` and `/plot unclaim`** (small–medium, safety).
3. **Gate `/plot claim`/`unclaim` consistently** with the other plot commands during a run (medium, UX).
4. **Add a confirm for `/dung class`** or document that it is permanent (small–medium).
5. **Remove the dead `/dung give coins` nugget path** or wire it to run coins (small).

## 8. Shared-flaw note
Every one of these commands hand-builds `§`-coded chat strings (see `chat.md` §8). Salvage/favorite/protection logic (favorite, starter, persistent) is re-implemented in `DungCommand` (`isSalvableArmor`, `salvageValue`) and again in `ShopUI` (repair) and `WorkstationUI`/`DungeonInstance` — the gear "eligibility" concept exists in at least three places (`isSalvableArmor`, `isWorkstationOrPersistentGear`, `WorkstationRules.isWorkstationGear`) with subtly different rules, a strong refactor target.
