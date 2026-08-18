# Shop — Chest GUI (Run Shop, Persistent Shop, Upgrades)

## 1. Purpose
Three 27-slot chest GUIs:
- **In-run shop** (`openRunShop`): weapons/armor/shield/heal/mana/key/bomb/buffs for run coins; opened by right-clicking the shopkeeper Villager in a SHOP room (GameListener line 543 → `openShop` line 1964).
- **Persistent shop** (`openPersistentShop`, `/shop`): persistent gear + repair + a button into upgrades, for persistent coins/shards; blocked inside a run (DungCommand line 291).
- **Upgrades GUI** (`openUpgrades`, `/upgrades`): permanent stat tracks purchasable with shards; also reachable from the persistent shop (slot 5).

## 2. Files / locations
- `src/main/java/com/lieyabull/dung/ui/ShopUI.java` — all of it.
  - `openRunShop` line 80; `openPersistentShop` line 142; `openUpgrades` line 279.
  - `onClick` line 328; `onClose` line 354; `onDrag` line 362; `reopen` line 373 (deferred 1-tick GUI re-open).
  - Handlers: `handleRunShopClick` line 379; `handlePersistentShopClick` line 437; `handleUpgradesClick` line 599.
  - Buttons/helpers: `makeRepairAllButton` line 183; `makeRepairItemButton` line 240; `makeShopItem` line 637; `fillEmpty` line 653; `effectDesc` line 665.
- Registered as a Listener in `Dung.onEnable` line 54.

## 3. Structure
- `RUN_SHOP_SIZE = PERSISTENT_SHOP_SIZE = UPGRADES_SIZE = 27` (lines 37–39). Named cost constants (lines 42–54) are good.
- Run shop slots: 0 weapon, 1 armor, 2 shield, 3 heart, 4 mana, 5 key, 6 bomb, 9 dmg buff, 10 def buff; then `fillEmpty` (gray panes).
- Persistent shop: 0 weapon, 1 armor, 2 shield, 3 repair item, 4 repair all, 5 upgrades, 6 repair broken.
- Upgrades: 7 tracks in slots 0–6, back button slot 22.
- GUI type/action encoded in the item PDC under `NamespacedKey.minecraft("dung_gui")` (line 342); clicks matched by `openGuis.get(e.getInventory())` keyed by inventory (ConcurrentHashMap).
- All clicks cancelled (`e.setCancelled(true)`, line 333) and drags cancelled (line 363).
- Re-open deferred one tick (`reopen`, line 373) to avoid the InventoryCloseEvent clobbering tracking — a well-commented Paper pitfall handled correctly.

## 4. Strengths
- Named constants for all run-shop prices; cost shown in item lore (`"§e"+RUN_WEAPON_COST+" coins"`).
- Click/drag fully locked; bottom inventory clicks can't move items while a shop is open.
- The deferred-reopen fix (line 373) is correct and clearly explained — a genuine, subtle Bukkit timing bug avoided.
- Repair buttons pre-compute and display the actual cost (items to repair count + total coins) before the click.
- Mana potion lore shows the actual restore amount (`st.maxMana - st.mana`, line 108) rather than a fixed number.

## 5. Weaknesses / UX issues
- **Repair cost formula is triplicated** and can drift. The same "repairAmt/10 * REPAIR_COST_PER_10 * costMult with round-up" math appears in `makeRepairAllButton` (lines 197–201), `makeRepairItemButton` (lines 250–254), `handlePersistentShopClick` "repair" (lines 494–499) and "repair_all" (lines 546–551). Four copies. A tweak to pricing must be replicated in four places or the displayed cost diverges from the charged cost.
- **Magic-number prices for repair-broken.** "Repair Broken Item" lore and handler hardcode `150` coins and `100` shards (lines 173, 580–592) while every other price is a named constant. `REPAIR_COST_PER_10` exists but 150/100 are inline.
- **No confirmation for spending persistent currency.** Weapon/armor/shield purchases (lines 441–479) and upgrades spend persistent coins/shards with a single click and no confirm. Persistent coins are the scarce meta-currency — one misclick costs real progress with no undo. (In-run shop is lower-stakes but same pattern.)
- **Unaffordable buttons still look enabled.** All shop icons are rendered identically regardless of affordability; the only feedback is a red chat line *after* the click. No gray-out, no "locked" lore, no count-of-affordable affordance.
- **Upgrades icon layout is ragged.** Tracks occupy slots 0–6 (a partial row) and the Back button sits at slot 22 (row 2, col 4) — inconsistent with the "gear in a row, back button bottom-center" mental model; a player scanning rows may miss the Back button. Also the maxed-state icon stays clickable and merely prints a gray "already maxed" chat line (line 611) — no visual MAXED lock.
- **`effectDesc` crit formula may disagree with the actual upgrade.** `effectDesc` line 671 returns `+level*0.5%` for crit, but `Upgrades.delta("crit")` returns 0 (Upgrades.java line 42–43, default branch) and the applied effect is elsewhere. The description shows "+1.5% crit" at level 3 while the actual per-level delta isn't clearly +0.5 — displayed effect vs. applied effect can diverge. All other tracks use `level * Upgrades.delta(t)` (lines 667–673), so crit is inconsistent with the rest.
- **GUI title encodes the balance in a hex color trick** (`"§x§b§8§8§6§0§b"` for coins, lines 85/146) — the balance is buried in the title and re-fetched only on re-open; after a purchase the shop reopens so it refreshes, good, but a player must re-scan the title to see the new balance (no persistent balance line).
- **Run-shop "shield" lore** (line 98) says "Auto-slots into hotbar slot 9" and "Sneak to charge" — but the actual equip mechanism (DungeonInstance `syncShieldSlot`, SHIELD_SLOT=8) requires the player to **manually place** the shield in slot 9 and never auto-slots it in (the sync only shows a green "Equip Shield" pane). The wording "Auto-slots into hotbar slot 9" is misleading — it is NOT auto-slotted.

## 6. Bugs / risks
- **`NamespacedKey.minecraft("dung_gui")`** (line 343) and the matching PDC read (line 342) use the reserved `minecraft:` namespace for plugin data. This risks collisions with vanilla/other-plugin keys and is a Paper anti-pattern; should be `new NamespacedKey(plugin, "dung_gui")`. Same in WorkstationUI.
- **`handleUpgradesClick` silently returns on an unknown action** (line 607 `if (t == null) return;`) with no user message — a broken/foreign item with a `dung_gui` tag yields no feedback.
- **Repair All "total" is computed at render time but re-computed at click.** If the inventory changed between render and click, the charged total differs from the shown total. In practice clicks are cancelled while the GUI is open so the inventory can't change, so low risk — but the button's displayed cost and the charged cost are two independent computations (another symptom of the duplication).
- **`makeRepairItemButton` calls `held.getItemMeta().getDisplayName()`** (line 256) with no null guard on the display name — a persistent item without a display name prints `null` in the lore line.
- **Persistent gear is always purchased at floor 0**: `ItemPool.randomWeapon(0)` / `randomArmor(0, ...)` (lines 448, 461, 474) — the persistent shop never scales with player progress; not a bug per se, but the lore says "(persists through death)" and nothing communicates that persistent gear is intentionally floor-0 quality, which can confuse a player comparing run-shop (floor-scaled) vs persistent gear.

## 7. Concrete suggestions (priority order)
1. **Extract ONE repair-cost helper** and have the three repair paths (item button, all button, click handlers) call it — removes the highest-value-of-consistency risk (small, high impact).
2. **Replace inline 150/100 with named constants** for repair-broken (small).
3. **Use a plugin-owned NamespacedKey, not `minecraft:`**, for `dung_gui` and the PDC reads/writes (small, robustness).
4. **Gray-out / "locked" lore on unaffordable buttons** and a MAXED lock icon for maxed upgrades, instead of chat-only failure (medium).
5. **Add a confirmation step for persistent-coin purchases** (reuse the WorkstationUI 2-click confirm pattern) (medium).
6. **Fix the misleading "Auto-slots into hotbar slot 9" shield lore** to match the manual-equip behavior (small, immediate).
7. **Reconcile `effectDesc` crit with `Upgrades.delta`** so the shown effect matches the applied effect (medium, correctness).

## 8. Shared-flaw note
- Uses `minecraft:` PDC keys — same anti-pattern as WorkstationUI.
- Single-click spend without confirmation for scarce currency — same class of issue as WorkstationUI's (which at least adds 2-click confirm for destructive ops).
- Cost/lore string building with hand-`§` codes duplicates the "cost in lore" convention used by workstation name tags and the run-shop; see `workstation.md`.
