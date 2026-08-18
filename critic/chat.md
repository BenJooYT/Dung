# Chat — Clickable Chat & Notifications

## 1. Purpose
Chat-based output and command shortcuts across the plugin: the join prompt (`ChatUI.startPrompt`), clickable run/shop/upgrades/stats/help buttons, generic notifications (pickup text, status), clickable party invite Accept/Decline, leaderboard pagination/category switcher, plot claim buttons, the in-run `[Descend]` button, and the shield-switch `[Switch]` prompt. Shown on join, on `/dung help`, and ad hoc throughout gameplay.

## 2. Files / locations
- `src/main/java/com/lieyabull/dung/ui/ChatUI.java` — `startPrompt` line 15, `notify` lines 26/32, `command` line 36 (the shared `runCommand` + hover builder).
- Callers:
  - `GameListener.onJoin` line 60/76 (prompt on join).
  - `DungCommand` — `dungCmd` help line 168; `partyCmd` invite lines 218–224; `leaderboard` lines 490–602 (page/category buttons).
  - `PlotManager.showClaimOptions` lines 200–254 (clickable claim buttons).
  - `DungeonInstance.onBossDefeated` lines 2346–2353 (`[Descend]`), `promptShieldSwitch` lines 3012–3025 (`[Switch]`).
  - `GameListener.onPickup` line 550 / `pickupMsg` line 568 (pickup notifications).

## 3. Structure
- `command(label, cmd, hover)` (line 36) parses a legacy label, attaches a gray `HoverEvent.showText` and `ClickEvent.runCommand`. Used to build bracketed buttons: `[ Start a run ]`, `[Shop]`, etc.
- Prompt is a header + 5 buttons (lines 16–23).
- Notifications use `LegacyComponentSerializer.legacySection().deserialize(msg)` (line 29) so arbitrary `§`-coded strings don't throw (avoids Adventure's `LegacyFormattingDetected`).
- Leaderboard builds a Component line with Prev/Next buttons and a category switcher row; the current category is shown non-clickable and green-bold (lines 574–601).

## 4. Strengths
- Centralizes the clickable-button builder so hover/click semantics are consistent.
- The `notify(String)` overload correctly routes legacy-`§` strings through the legacy serializer instead of `Component.text`, which would throw — a real and well-handled gotcha.
- Party invite cleanly composes Accept + Decline as independent clickable children and strips the parent hover (`hoverEvent(null)`, line 222).
- Leaderboard rank coloring and page/category navigation are usable and consistent.

## 5. Weaknesses / UX issues
- **No feedback when a clickable runCommand is blocked server-side.** `[ Start a run ]` runs `/dung start`; if already in a run the server replies in chat, but `[ Shop ]`/`[ Upgrades ]` are silently blocked inside a run (DungCommand lines 291–309). The prompt's Shop hover says "spend persistent coins on gear" with **no warning that it's unavailable during a run** — a player mid-run who clicks Shop gets a red error, then nothing. The prompt should gate/annotate these by run state.
- **Leaderboard "Next/Prev" disabled buttons are colored `§8` but still look clickable** (lines 578/584); minor affordance ambiguity.
- **Pickup notifications** (`pickupMsg`, GameListener line 568) show raw numbers in parens (`(x/max)`) but don't indicate capped vs. gained; and a pickup that's refused (no apply) sends no "full" feedback, so picking up a heart at full HP is silent (no feedback for a no-op).
- Party invite: the appended `.hoverEvent(null)` removes the container hover but the two child buttons keep their own hovers; however the whole line also sends a plain-text follow-up (`target.sendMessage("§a"+p.getName()+" invited you...")` line 224) that is redundant with the Accept button text.
- `[ Descend ]` (DungeonInstance line 2349) and `[Switch]` (line 3022) are green/bold text with hover-only descriptions — nothing communicates that the button only works if you're still in a run/not dead (dead players are spectators; a dead player clicking Descend gets `setStatus`/vote logic that excludes them but chat only says "voted" per others).

## 6. Bugs / risks
- **`command()` deserializes the label through legacy — if a caller passes a label containing a `§x` hex code or unbalanced `§` it can mis-render**, but more importantly the label is never length-checked; extremely long labels overflow the chat line (not currently abused).
- **`notify` overloads are ambiguous for callers passing raw `Component` vs legacy string** — two overloads (lines 26, 32) with a risk a caller passes a legacy string that Adventure re-wraps; currently handled by routing through the string overload, but a future caller passing a Component with legacy codes would throw.
- The join prompt is sent on **every join** (GameListener line 76), including returning players and players who logged out mid-run — a player rejoining mid-run gets a prompt advertising `/dung start` while their run may still be active, confusing.

## 7. Concrete suggestions (priority order)
1. **Annotate the Shop/Upgrades buttons in the prompt when the player is in a run** (gray them out / change hover to "unavailable during a run") (small).
2. **Gate/annotate the join prompt by run state** (don't advertise `/dung start` while an instance is active) (small–medium).
3. **Give pickup no-op feedback** (e.g. gray "hearts full" / "inventory full") so dropped pickups aren't silent (medium).
4. Centralize a `chat.affordance` helper for disabled button styling so Prev/Next and Shop-disabled states look consistent (medium).

## 8. Shared-flaw note
Chat messaging is highly duplicated and hand-`§`-coded across `DungCommand`, `PlotManager`, `DungeonInstance`, `GameListener`, and `ShopUI` (dozens of `p.sendMessage("§...")`). There is no centralized message/formatting layer (TextUtil only has `fmt`/`capital`); wording, colors, and punctuation are inconsistent between UIs — see `commands.md` and `shop.md`.
