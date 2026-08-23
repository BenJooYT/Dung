package com.lieyabull.dung.game;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.boss.BossController;
import com.lieyabull.dung.dungeon.Floor;
import com.lieyabull.dung.dungeon.FloorGenerator;
import com.lieyabull.dung.dungeon.RoomGen;
import com.lieyabull.dung.dungeon.RoomType;
import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.SpawnFloor;
import com.lieyabull.dung.structure.StructureDefinition;
import com.lieyabull.dung.structure.StructureRegistry;
import com.lieyabull.dung.structure.StructureTransform;
import com.lieyabull.dung.structure.StructureWorldEdit;
import com.lieyabull.dung.entity.Enemy;
import com.lieyabull.dung.entity.MobType;
import com.lieyabull.dung.items.Affix;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.ItemPool;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.items.Rarity;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.party.Party;
import com.lieyabull.dung.pickup.Pickup;
import com.lieyabull.dung.ui.HUD;
import com.lieyabull.dung.ui.TabUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A single dungeon instance shared by a party of players. Each instance has its own floor,
 * room state, enemies, boss, and per-player state. Multiple instances can run concurrently.
 */
public final class DungeonInstance {
    public static final int BASE_Y = 130;
    public static final int MIN_SPACING = 22;
    public static final int MAX_SPACING = 28;
    private int spacing = 25;

    private final Dung plugin;
    private final UUID instanceId;
    private final Party party;
    private final int offsetX;
    private final int offsetZ;
    private final World runWorld;
    private Run run;
    private World world;
    private Floor.RoomNode curRoom;
    // Per-player current room, so party members can be in different rooms at once without the
    // single global curRoom clobbering combat (fixes split-room softlock + enemy duplication).
    private final Map<UUID, Floor.RoomNode> playerRoom = new HashMap<>();
    // Rooms that already spawned enemies this floor, so re-entry never re-spawns mobs.
    private final Set<Long> spawnedRooms = new HashSet<>();
    /** Per-player scoreboards so each player's HUD is independent. */
    private final Map<UUID, org.bukkit.scoreboard.Scoreboard> playerBoards = new HashMap<>();
    private final Map<Long, List<Enemy>> roomEnemies = new HashMap<>();
    private final Map<Long, Boolean> roomLocked = new HashMap<>();
    private int barTick = 0;
    private int tabTickCounter = 0;
    private final Map<UUID, Double> lastBarHearts = new HashMap<>();
    private final Map<UUID, Double> lastBarMana = new HashMap<>();
    private final Map<UUID, String> lastHeadHp = new HashMap<>();
    /** Per-player overhead HP bar: a non-persistent TextDisplay riding the player's head. */
    private final Map<UUID, org.bukkit.entity.TextDisplay> hpTags = new HashMap<>();
    private final Map<UUID, ItemStack[]> lastGear = new HashMap<>();
    private BossController boss;
    private Floor.RoomNode bossRoom;
    private final Map<UUID, HUD> huds = new HashMap<>();
    private final Map<UUID, TabUI> tabs = new HashMap<>();
    private final Map<UUID, Location> returnLocs = new HashMap<>();
    private final Set<UUID> deadPlayers = new HashSet<>();
    private final Set<Location> pedestals = new HashSet<>();
    private final Map<Location, ItemStack> pedestalItems = new HashMap<>();
    // Track destructible wall blocks (CRACKED_STONE_BRICKS) for bomb interaction
    private final Set<Location> destructibleWalls = new HashSet<>();
    // Track which secret rooms have been revealed (by their grid key)
    private final Set<Long> revealedSecrets = new HashSet<>();
    // Shopkeepers (per SHOP room) + which shop rooms have been spawned this floor
    private final List<org.bukkit.entity.Villager> shopkeepers = new ArrayList<>();
    private final Set<Long> shopSpawned = new HashSet<>();
    // Workstations (per UPGRADE room) + which upgrade rooms have been spawned this floor
    // A workstation is a physical block location -> its registered function type. Name-tag armor
    // stands float above each so the function reads at a glance without knowing the block.
    private final Map<Location, WorkstationType> workstations = new HashMap<>();
    private final List<org.bukkit.entity.ArmorStand> workstationTags = new ArrayList<>();
    private final Set<Long> persistSpawned = new HashSet<>();
    // Items "tried to persist" successfully during the run — delivered as persistent gear after the run ends
    private final Map<UUID, List<ItemStack>> pendingPersists = new HashMap<>();
    // Consecutive failed PRESERVE attempts per player — bad-luck protection (reset on success)
    private final Map<UUID, Integer> preserveFails = new HashMap<>();
    // Saved pre-run inventory snapshots (non-dungeon items) to restore after the run ends
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private boolean running;
    // Tick counter for timed effects (e.g. shield mana bleed every 0.5s = every 10 ticks)
    private int tickCounter;
    // Per-player transient status messages shown in the action bar (with expiry timestamps)
    private static final long STATUS_DURATION_MS = 3000;
    private final Map<UUID, StatusMessage> statusMessages = new HashMap<>();

    private static final class StatusMessage {
        final String text;
        final long expiresAt;
        StatusMessage(String text, long expiresAt) {
            this.text = text;
            this.expiresAt = expiresAt;
        }
    }

    public DungeonInstance(Dung plugin, Party party, int offsetX, int offsetZ) {
        this(plugin, party, offsetX, offsetZ, null);
    }

    /** Full constructor: {@code runWorld} is the dedicated void world for this run (null falls
     *  back to the legacy shared plugin world). */
    public DungeonInstance(Dung plugin, Party party, int offsetX, int offsetZ, World runWorld) {
        this.plugin = plugin;
        this.instanceId = UUID.randomUUID();
        this.party = party;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
        this.runWorld = runWorld;
    }

    public UUID instanceId() { return instanceId; }
    public Party party() { return party; }
    public Run run() { return run; }
    public World world() { return world; }
    public int offsetX() { return offsetX; }
    public int offsetZ() { return offsetZ; }

    /** Room base X coordinate (grid position + instance offset). */
    private int baseX(Floor.RoomNode n) { return n.x * spacing + offsetX; }
    /** Room base Z coordinate (grid position + instance offset). */
    private int baseZ(Floor.RoomNode n) { return n.z * spacing + offsetZ; }
    public org.bukkit.scoreboard.Scoreboard boardOf(Player p) { return playerBoards.get(p.getUniqueId()); }
    public Floor.RoomNode curRoom() { return curRoom; }
    /** Get the room a specific player is currently in (per-player tracking for party support). */
    public Floor.RoomNode playerRoomOf(UUID playerId) { return playerRoom.get(playerId); }

    /** Find which room (if any) the given location is inside. Returns null if the location is in a
     *  corridor or outside the floor. */
    public Floor.RoomNode roomAt(Location loc) {
        if (run == null || run.floor == null) return null;
        for (Floor.RoomNode rn : run.floor.rooms()) {
            if (insideRoom(loc, rn)) return rn;
        }
        return null;
    }
    public BossController boss() { return boss; }
    public Map<Long, List<Enemy>> roomEnemies() { return roomEnemies; }
    public boolean isRunning() { return running; }
    public Map<UUID, Integer> preserveFails() { return preserveFails; }

    /** Check if a player is currently dead (in the deadPlayers set). Used by GameListener
     *  to decide whether to set a respawning player to SPECTATOR or SURVIVAL mode. */
    public boolean isDead(UUID playerId) { return deadPlayers.contains(playerId); }

    /** Get the PlayerState for a specific player in this instance. */
    public PlayerState playerStateOf(Player p) {
        return run == null ? null : run.playerStateOf(p.getUniqueId());
    }

    /** Check if a player is part of this dungeon instance. */
    public boolean hasPlayer(Player p) {
        return party.isMember(p.getUniqueId());
    }

    /** Check if any party member is online. */
    private boolean anyOnline() {
        return !party.onlineMembers().isEmpty();
    }

    // ---------- lifecycle ----------

    public void startRun(long seed) {
        if (running) return;
        lastBarHearts.clear();
        lastBarMana.clear();
        lastGear.clear();
        for (org.bukkit.entity.TextDisplay t : hpTags.values()) t.remove();
        hpTags.clear();
        lastHeadHp.clear();
        barTick = 0;
        tabTickCounter = 0;
        curRoom = null;
        // Use the dedicated run world when one was created for this instance; the legacy shared
        // plugin world remains the fallback. Offsets stay 0 so all geometry is unchanged.
        if (runWorld != null) world = runWorld; else world = plugin.world();
        run = new Run(seed);

        // Create PlayerState for each party member; remember the LOBBY spawn as each member's
        // return point so leaving or dying always sends them back there (never into this run
        // world, which gets deleted when the run ends).
        for (Player p : party.onlineMembers()) {
            returnLocs.put(p.getUniqueId(), plugin.worldManager().lobbySpawn().clone());
            PlayerState ps = new PlayerState(p);
            ps.classId = plugin.meta().profile(p.getUniqueId()).classId;
            ps.upgrades.putAll(plugin.meta().profile(p.getUniqueId()).upgrades);
            ps.recomputeStats();
            run.addPlayerState(p.getUniqueId(), ps);
        }

        playerBoards.clear();
        for (Player p : party.onlineMembers()) {
            org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            playerBoards.put(p.getUniqueId(), sb);
            p.setScoreboard(sb);
            HUD hud = new HUD();
            hud.reset(p, sb);
            huds.put(p.getUniqueId(), hud);
            TabUI tab = new TabUI();
            tab.reset(sb);
            tabs.put(p.getUniqueId(), tab);
            p.setGameMode(org.bukkit.GameMode.SURVIVAL);
            p.setHealth(20);
            p.setFoodLevel(20);
        }

        // Save each player's non-dungeon inventory before granting run gear, so we can restore
        // it when the run ends (leave/death/endRun).
        saveNonDungeonInventories();

        // Clear non-dungeon items from each player's inventory so they don't carry plot-world
        // items (logs, pickaxes, etc.) into the dungeon. Persistent dung gear is kept.
        for (Player p : party.onlineMembers()) {
            PlayerInventory inv = p.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack s = inv.getItem(slot);
                if (s != null && !isPersistentGear(s)) inv.setItem(slot, null);
            }
            for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
                if (slot == org.bukkit.inventory.EquipmentSlot.HAND) continue;
                ItemStack s = inv.getItem(slot);
                if (s != null && !isPersistentGear(s)) inv.setItem(slot, null);
            }
            // Move persistent gear out of hotbar slots 7-8 (indices 6-7) so they don't get
            // overwritten by key/bomb items. Find a free slot for each displaced item.
            for (int hotSlot : new int[]{KEY_SLOT, BOMB_SLOT}) {
                ItemStack s = inv.getItem(hotSlot);
                if (s != null && isPersistentGear(s)) {
                    int free = firstFreeSlot(inv, hotSlot);
                    if (free >= 0) {
                        inv.setItem(free, s);
                        inv.setItem(hotSlot, null);
                    }
                }
            }
        }

        running = true;
        grantStarters();
        enterFloor(0);
    }

    private void grantStarters() {
        for (Player p : party.onlineMembers()) {
            if (!hasDungGear(p)) {
                ItemStack[] kit = com.lieyabull.dung.items.GearFactory.starter();
                PlayerInventory inv = p.getInventory();
                inv.addItem(kit[0]);
                org.bukkit.inventory.EquipmentSlot[] slots = {
                        org.bukkit.inventory.EquipmentSlot.HEAD,
                        org.bukkit.inventory.EquipmentSlot.CHEST,
                        org.bukkit.inventory.EquipmentSlot.LEGS,
                        org.bukkit.inventory.EquipmentSlot.FEET
                };
                for (int i = 0; i < 4; i++) {
                    ItemStack cur = inv.getItem(slots[i]);
                    if (cur == null || cur.getType().isAir()) inv.setItem(slots[i], kit[i + 1]);
                }
                p.sendMessage("§7You were given a starter kit: §fFrayed Blade§7 + cloth armor.");
            }
            MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
            if (!prof.hasSeenTutorial) {
                prof.hasSeenTutorial = true;
                p.sendTitle("§cDUNGEON", "§7Clear every room. Find the Warden. Descend deeper.", 10, 70, 10);
                p.sendMessage("§6Clear rooms to earn coins and gear. Find the boss room to go deeper.");
                p.sendMessage("§7Attack: §fLeft-Click    §7Weapon Ability: §fSneak + Right-Click");
                p.sendMessage("§7Class Ability: §fSneak + Drop (Q)    §7Heal: pick up §c♥§7 hearts");
                p.sendMessage("§7Keys & Bombs appear in hotbar slots 7-8. Right-click locked doors with a key, cracked walls with a bomb.");
                p.sendMessage("§7Equip a Mana Shield in slot 9 — hold it and sneak to charge it with mana.");
                p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§7Salvage spare armor: §f/salvage§7. Exit: §f/dung leave"));
            }
        }
        plugin.meta().save();
    }

    /** Whether the player already has run (non-persistent) gear. Persistent gear carried in from
     *  outside doesn't count — they still get a starter kit so they aren't left weapon-less. */
    private boolean hasDungGear(Player p) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack s : inv.getContents()) if (isRunDungGear(s)) return true;
        for (ItemStack s : inv.getArmorContents()) if (isRunDungGear(s)) return true;
        return isRunDungGear(inv.getItemInOffHand());
    }

    private static boolean isRunDungGear(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        boolean isGear = pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.GEAR),
                org.bukkit.persistence.PersistentDataType.STRING);
        boolean persistent = pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.PERSISTENT),
                org.bukkit.persistence.PersistentDataType.STRING);
        return isGear && !persistent;
    }

    /** Snapshot each player's non-dungeon inventory before the run starts, so we can restore
     *  it when the run ends (leave/death/endRun). Excludes persistent dung gear which stays. */
    private void saveNonDungeonInventories() {
        for (Player p : party.onlineMembers()) {
            PlayerInventory inv = p.getInventory();
            // Clone the full contents array (size 41 = 36 main + 5 armor+offhand)
            ItemStack[] snapshot = new ItemStack[inv.getSize() + 5]; // extra for armor+offhand
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack s = inv.getItem(i);
                snapshot[i] = (s == null) ? null : s.clone();
            }
            // Armor contents (boots, legs, chest, head)
            ItemStack[] armor = inv.getArmorContents();
            for (int i = 0; i < armor.length; i++) {
                snapshot[inv.getSize() + i] = (armor[i] == null) ? null : armor[i].clone();
            }
            // Off hand
            ItemStack off = inv.getItemInOffHand();
            snapshot[inv.getSize() + 4] = (off == null) ? null : off.clone();
            savedInventories.put(p.getUniqueId(), snapshot);
        }
    }

    /** Restore a player's pre-run inventory snapshot. Called after stripRunGear so the player
     *  gets back their original items (minus any persistent dung gear that was already there).
     *  Persistent items the player acquired during the run (from shop, pedestals, etc.) are
     *  preserved; persistent items that were dropped or salvaged during the run are not restored.
     *  Mid-run durability loss (e.g. from weapon abilities) is preserved: if a persistent item
     *  in the current inventory has lower durability than the snapshot version, the damaged
     *  version is kept instead of the undamaged snapshot. */
    private void restoreSavedInventory(Player p) {
        ItemStack[] snapshot = savedInventories.remove(p.getUniqueId());
        if (snapshot == null) return;
        PlayerInventory inv = p.getInventory();
        // Collect persistent items the player currently has (after stripRunGear) — these are the
        // ones they still own at run end. Items dropped, exchanged, or preserved during the run
        // won't be here.
        java.util.List<ItemStack> currentPersistent = new java.util.ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && isPersistentGear(s)) currentPersistent.add(s.clone());
        }
        for (ItemStack s : inv.getArmorContents()) {
            if (s != null && isPersistentGear(s)) currentPersistent.add(s.clone());
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && isPersistentGear(off)) currentPersistent.add(off.clone());

        // Build a set of UUIDs for persistent items the player still owns. Used to skip
        // restoring snapshot persistent items that were dropped, exchanged, or preserved —
        // giving them back would duplicate them since the original still exists elsewhere.
        java.util.Set<String> ownedPersistUuids = new java.util.HashSet<>();
        for (ItemStack ps : currentPersistent) {
            String uuid = GearFactory.getUuid(ps);
            if (uuid != null) ownedPersistUuids.add(uuid);
        }

        // Clear the entire inventory first so no run leftovers remain
        inv.clear();
        // Restore snapshot, but skip persistent items the player no longer owns.
        for (int i = 0; i < inv.getSize(); i++) {
            if (i < snapshot.length && snapshot[i] != null) {
                ItemStack snapItem = snapshot[i];
                if (isPersistentGear(snapItem)) {
                    String uuid = GearFactory.getUuid(snapItem);
                    // Skip if the player no longer has this item (dropped/exchanged/preserved).
                    // Pre-UUID items (uuid == null) are still restored to avoid data loss.
                    if (uuid != null && !ownedPersistUuids.contains(uuid)) continue;
                }
                inv.setItem(i, snapItem.clone());
            }
        }
        // Restore armor
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            int idx = inv.getSize() + i;
            if (idx < snapshot.length && snapshot[idx] != null) {
                ItemStack snapItem = snapshot[idx];
                if (isPersistentGear(snapItem)) {
                    String uuid = GearFactory.getUuid(snapItem);
                    if (uuid != null && !ownedPersistUuids.contains(uuid)) {
                        armor[i] = null;
                        continue;
                    }
                }
                armor[i] = snapItem.clone();
            } else {
                armor[i] = null;
            }
        }
        inv.setArmorContents(armor);
        // Restore off hand
        int offIdx = inv.getSize() + 4;
        if (offIdx < snapshot.length && snapshot[offIdx] != null) {
            ItemStack snapItem = snapshot[offIdx];
            if (isPersistentGear(snapItem)) {
                String uuid = GearFactory.getUuid(snapItem);
                if (uuid == null || ownedPersistUuids.contains(uuid)) {
                    inv.setItemInOffHand(snapItem.clone());
                }
            } else {
                inv.setItemInOffHand(snapItem.clone());
            }
        }

        // Now add back any persistent items the player still had at run end that are NOT already
        // in the restored inventory. This preserves new persistent items picked up during the run
        // (shop, pedestals) and naturally excludes dropped/exchanged/preserved ones.
        // For items that DO match a snapshot item, preserve mid-run durability loss: if the
        // current version has lower durability, replace the snapshot version with the damaged one.
        for (ItemStack ps : currentPersistent) {
            int[] matchSlot = findPersistentSlot(inv, ps);
            if (matchSlot == null) {
                // No match in restored inventory — this is a new persistent item acquired mid-run
                inv.addItem(ps).values().forEach(drop ->
                    p.getWorld().dropItemNaturally(p.getLocation(), drop));
            } else {
                // Match found — check durability. If the current version has lower durability
                // (damaged mid-run by ability use), keep the damaged version.
                int curDur = GearFactory.getDurability(ps);
                int snapDur;
                if (matchSlot[0] >= 0) {
                    ItemStack restored = inv.getItem(matchSlot[0]);
                    snapDur = restored != null ? GearFactory.getDurability(restored) : -1;
                } else if (matchSlot[1] >= 0) {
                    ItemStack[] armorContents = inv.getArmorContents();
                    snapDur = armorContents[matchSlot[1]] != null ? GearFactory.getDurability(armorContents[matchSlot[1]]) : -1;
                } else {
                    // Offhand
                    snapDur = GearFactory.getDurability(inv.getItemInOffHand());
                }
                if (curDur >= 0 && snapDur >= 0 && curDur < snapDur) {
                    // Replace the snapshot version with the damaged mid-run version
                    if (matchSlot[0] >= 0) {
                        inv.setItem(matchSlot[0], ps.clone());
                    } else if (matchSlot[1] >= 0) {
                        ItemStack[] armorContents = inv.getArmorContents();
                        armorContents[matchSlot[1]] = ps.clone();
                        inv.setArmorContents(armorContents);
                    } else {
                        inv.setItemInOffHand(ps.clone());
                    }
                }
            }
        }
    }

    /** Find the slot of a matching persistent item in the inventory.
     *  Returns int[2] where [0]=inventory slot index or -1, [1]=armor slot index or -1.
     *  Returns null if no match is found. */
    private static int[] findPersistentSlot(PlayerInventory inv, ItemStack item) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && itemsMatchPersistent(s, item)) return new int[]{i, -1};
        }
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && itemsMatchPersistent(armor[i], item)) return new int[]{-1, i};
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && itemsMatchPersistent(off, item)) return new int[]{-1, -2};
        return null;
    }

    /** Compare two persistent items by their UUID. If both items have UUIDs, they match
     *  only if the UUIDs are equal. If either item lacks a UUID (pre-migration), falls back
     *  to comparing by type and dung PDC tags (kind, rarity, defense, damage). */
    private static boolean itemsMatchPersistent(ItemStack a, ItemStack b) {
        if (a.getType() != b.getType()) return false;
        // UUID-based matching: if both items have UUIDs, compare those directly
        String uuidA = GearFactory.getUuid(a);
        String uuidB = GearFactory.getUuid(b);
        if (uuidA != null && uuidB != null) {
            return uuidA.equals(uuidB);
        }
        // Fallback for pre-UUID items: compare by type + PDC tags
        var pdcA = a.getItemMeta() == null ? null : a.getItemMeta().getPersistentDataContainer();
        var pdcB = b.getItemMeta() == null ? null : b.getItemMeta().getPersistentDataContainer();
        if (pdcA == null || pdcB == null) return false;
        org.bukkit.NamespacedKey kindKey = org.bukkit.NamespacedKey.minecraft(ItemTags.KIND);
        org.bukkit.NamespacedKey rarityKey = org.bukkit.NamespacedKey.minecraft(ItemTags.RARITY);
        org.bukkit.NamespacedKey defKey = org.bukkit.NamespacedKey.minecraft(ItemTags.DEFENSE);
        org.bukkit.NamespacedKey dmgKey = org.bukkit.NamespacedKey.minecraft(ItemTags.DAMAGE);
        String kA = pdcA.get(kindKey, org.bukkit.persistence.PersistentDataType.STRING);
        String kB = pdcB.get(kindKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (kA == null || kB == null || !kA.equals(kB)) return false;
        String rA = pdcA.get(rarityKey, org.bukkit.persistence.PersistentDataType.STRING);
        String rB = pdcB.get(rarityKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (rA == null || rB == null || !rA.equals(rB)) return false;
        Integer dA = pdcA.get(defKey, org.bukkit.persistence.PersistentDataType.INTEGER);
        Integer dB = pdcB.get(defKey, org.bukkit.persistence.PersistentDataType.INTEGER);
        if (dA != null && dB != null && !dA.equals(dB)) return false;
        Integer dmA = pdcA.get(dmgKey, org.bukkit.persistence.PersistentDataType.INTEGER);
        Integer dmB = pdcB.get(dmgKey, org.bukkit.persistence.PersistentDataType.INTEGER);
        if (dmA != null && dmB != null && !dmA.equals(dmB)) return false;
        return true;
    }

    public void enterFloor(int floorIndex) {
        if (run == null) return;
        run.floorIndex = floorIndex;
        descendVotes.clear();
        clearRoomEntities();
        spacing = ThreadLocalRandom.current().nextInt(MIN_SPACING, MAX_SPACING + 1);
        FloorGenerator gen = new FloorGenerator(new java.util.Random(run.rng.nextLong()), 9, 9,
                plugin.getConfig().getInt("rooms-per-floor", 7), floorIndex);
        run.floor = gen.generate();
        // Mixed template/procedural mode: assign templates to rooms where a validated template
        // exists for their type; rooms without a matching template are built procedurally.
        // SECRET and LOCKED rooms always use procedural mechanics (secret passage carving,
        // locked barriers) and never get templates.
        // Custom rooms can be disabled via config (custom-rooms: false) or /room toggle.
        if (plugin.getConfig().getBoolean("custom-rooms", true)) {
            resolveStructures(run.floor);
        }
        for (Floor.RoomNode n : run.floor.rooms()) {
            if (n.structure != null) {
                StructureDefinition s = n.structure;
                RoomBounds tot = s.total();
                int ox = baseX(n) - tot.minX;
                int oy = BASE_Y - tot.minY;
                int oz = baseZ(n) - tot.minZ;
                StructureWorldEdit.paste(world, n.clipboard, ox, oy, oz, n.rotationSteps);
            } else {
                RoomGen.build(world, n, BASE_Y, spacing, offsetX, offsetZ);
            }
        }
        // Structure rooms: carve doorway openings + corridors procedurally on the shared corridor line
        // (PERP_CENTER), so each structure room opens only the door directions the floor graph needs
        // and connects to its neighbours without leaving a hole between the room wall and the corridor.
        carveStructureDoors(run.floor);
        carveStructureCorridors(run.floor);
        // Register destructible wall locations for SECRET rooms and carve passages
        destructibleWalls.clear();
        revealedSecrets.clear();
        for (Floor.RoomNode n : run.floor.rooms()) {
            if (n.type == RoomType.SECRET && n.destructibleWallLoc != null) {
                carveSecretPassage(n);
                registerDestructibleWall(n);
            }
        }
        // Post-pass LOCKED doors: place every barrier AFTER all rooms are built so a neighbour
        // room's corridor carve can never overwrite the locked door away.
        for (Floor.RoomNode n : run.floor.rooms()) {
            if (n.type == RoomType.LOCKED) placeLockedDoorBarrier(n);
        }
        curRoom = run.floor.start;
        enterRoom(curRoom);
        run.floor.visited.clear();

        // Reset HUD lastText arrays so all sidebar rows are re-painted on the new floor
        for (HUD hud : huds.values()) hud.resetLastText();

        // Teleport all party members, scattered randomly across the starting room and facing
        // its center (both on run start and every descend)
        for (Player p : party.onlineMembers()) {
            p.teleport(scatterSpawn(run.floor.start));
            playerRoom.put(p.getUniqueId(), run.floor.start);
        }
        // Equipped persistent armor loses 1-4 durability on descend
        for (Player p : party.onlineMembers()) {
            org.bukkit.inventory.PlayerInventory inv = p.getInventory();
            int[] armorSlots = {36, 37, 38, 39};
            for (int slot : armorSlots) {
                ItemStack s = inv.getItem(slot);
                if (s == null || s.getType() == Material.AIR) continue;
                if (!isPersistentGear(s)) continue;
                int dmg = ThreadLocalRandom.current().nextInt(1, 5); // 1-4 durability loss
                boolean broken = GearFactory.damageItem(s, dmg);
                if (broken) {
                    handleBrokenArmor(p, s, " from the descent");
                }
            }
        }
        refreshUI();
    }

    /** Assign validated templates to rooms where a matching template exists. Rooms without a
     *  registered template for their type, as well as SECRET and LOCKED rooms (which use special
     *  procedural mechanics), are left null and built procedurally. This allows template and
     *  procedural rooms to coexist on the same floor.
     *
     *  <p>Templates are rotated by a random allowed rotation for visual variety. Doorways/corridors
     *  are NOT stored in the template — they are carved procedurally at build time on the shared
     *  corridor line ({@link #carveStructureDoors}), so any rotation is valid for any door graph.
     *  A rotated copy is stored on the room node; the original template is unchanged. */
    private void resolveStructures(Floor floor) {
        java.util.Random rnd = new java.util.Random(run.rng.nextLong());
        for (Floor.RoomNode n : floor.rooms()) {
            // SECRET and LOCKED rooms always use procedural mechanics
            if (n.type == RoomType.SECRET || n.type == RoomType.LOCKED) continue;
            StructureDefinition s = registry().pick(n.type, rnd);
            if (s == null) continue;
            // Pick a random allowed rotation (doors are carved procedurally, so nothing to align).
            StructureTransform.Rotation rot = StructureTransform.pickRandom(s, rnd);
            // Rotate a copy of the metadata to match; the (unrotated) WorldEdit clipboard is pasted
            // with the same clockwise rotation so schematic and metadata stay in sync.
            StructureDefinition def = StructureTransform.rotate(s, rot);
            n.structure = def;
            n.structureId = s.id;
            n.rotationSteps = rot.steps;
            StructureRegistry.Registered reg = registry().byId(s.id);
            n.clipboard = reg == null ? null : reg.clipboard();
        }
    }

    /** Carve a 3-wide doorway through each structure room's own wall, at the fixed corridor line
     *  ({@link RoomGen#PERP_CENTER}), for exactly the door directions the floor graph opens. The
     *  perpendicular is anchored to the grid line (the same one procedural rooms use), so a structure
     *  room's opening always lines up with the corridor — it never leaves a hole between the room's
     *  outer wall and the corridor wall — and stays well away from the corners. */
    private void carveStructureDoors(Floor floor) {
        for (Floor.RoomNode n : floor.rooms()) {
            if (n.structure == null) continue;
            RoomBounds t = n.structure.total();
            for (int d = 0; d < 4; d++) {
                if (!n.doors[d]) continue;
                boolean horiz = d == 1 || d == 3;
                int wallAlong = facingWallAlong(n, d, t);
                int perpC = horiz ? (baseZ(n) + RoomGen.PERP_CENTER) : (baseX(n) + RoomGen.PERP_CENTER);
                for (int off = -1; off <= 1; off++) {
                    for (int y = BASE_Y + 1; y <= BASE_Y + RoomGen.ROOM_HEIGHT; y++) {
                        int px = horiz ? wallAlong : (perpC + off);
                        int pz = horiz ? (perpC + off) : wallAlong;
                        world.getBlockAt(px, y, pz).setType(Material.AIR);
                    }
                    int fx = horiz ? wallAlong : perpC;
                    int fz = horiz ? perpC : wallAlong;
                    world.getBlockAt(fx, BASE_Y, fz).setType(Material.POLISHED_ANDESITE);
                    world.getBlockAt(fx, BASE_Y + RoomGen.ROOM_HEIGHT + 1, fz).setType(Material.STONE_BRICKS);
                }
            }
        }
    }

    /** Carve connecting corridors between every pair of adjacent rooms (at least one side a structure
     *  room) that share an open door. The tube runs at the fixed {@link RoomGen#PERP_CENTER} line,
     *  spanning the gap between the two rooms' facing walls, so it merges cleanly with a procedural
     *  neighbour's own corridor (also carved on that line) and the two structure doorways connect
     *  without a gap. Procedural↔procedural pairs are already handled by {@link RoomGen#build}. */
    private void carveStructureCorridors(Floor floor) {
        int[] DX = {0, 1, 0, -1};
        int[] DZ = {-1, 0, 1, 0};
        Set<Long> carved = new HashSet<>();
        for (Floor.RoomNode n : floor.rooms()) {
            if (n.structure == null) continue;
            for (int d = 0; d < 4; d++) {
                if (!n.doors[d]) continue;
                Floor.RoomNode m = floor.at(n.x + DX[d], n.z + DZ[d]);
                if (m == null) continue;
                long e = floor.key(Math.min(n.x, m.x), Math.min(n.z, m.z));
                if (!carved.add(e)) continue;
                boolean horiz = d == 1 || d == 3;
                RoomBounds ta = n.structure.total();
                RoomBounds tb = m.structure == null ? null : m.structure.total();
                int aAlong = facingWallAlong(n, d, ta);
                int bAlong = m.structure != null ? facingWallAlong(m, d ^ 2, tb)
                        : facingWallAlongProcedural(m, d ^ 2);
                int lo = Math.min(aAlong, bAlong) + 1;
                int hi = Math.max(aAlong, bAlong) - 1;
                int perpC = horiz ? (baseZ(n) + RoomGen.PERP_CENTER) : (baseX(n) + RoomGen.PERP_CENTER);
                for (int t = lo; t <= hi; t++) {
                    for (int off = -1; off <= 1; off++) {
                        int px = horiz ? t : (perpC + off);
                        int pz = horiz ? (perpC + off) : t;
                        world.getBlockAt(px, BASE_Y, pz).setType(Material.POLISHED_ANDESITE);
                        for (int y = BASE_Y + 1; y <= BASE_Y + RoomGen.ROOM_HEIGHT; y++) {
                            world.getBlockAt(px, y, pz).setType(Material.AIR);
                        }
                        world.getBlockAt(px, BASE_Y + RoomGen.ROOM_HEIGHT + 1, pz).setType(Material.STONE_BRICKS);
                    }
                }
            }
        }
    }

    /** World coordinate of a room's outer wall on side {@code d} (0=N,1=E,2=S,3=W), for a structure room. */
    private int facingWallAlong(Floor.RoomNode n, int d, RoomBounds t) {
        boolean horiz = d == 1 || d == 3;
        int half = horiz ? t.width() : t.depth();
        int along = horiz ? baseX(n) : baseZ(n);
        return along + (d == 1 || d == 2 ? half : 0);
    }

    /** World coordinate of a procedural room's outer wall on side {@code d}. */
    private int facingWallAlongProcedural(Floor.RoomNode n, int d) {
        boolean horiz = d == 1 || d == 3;
        int mw = n.sizeW + 2 * RoomGen.WALL;
        int mh = n.sizeH + 2 * RoomGen.WALL;
        int along = horiz ? baseX(n) : baseZ(n);
        return along + (d == 1 ? mw : (d == 2 ? mh : 0));
    }

    private StructureRegistry registry() {
        return plugin.structures().registry();
    }

    /** World spawn location for a room: the template PLAYER_SPAWN marker if present, else the
     *  procedural room centre. */
    private Location roomSpawn(Floor.RoomNode n) {
        if (n.structure != null) {
            List<RoomMarker> ms = n.structure.markersOf(RoomMarkerType.PLAYER_SPAWN);
            if (!ms.isEmpty()) {
                RoomMarker m = ms.get(0);
                return new Location(world,
                        baseX(n) - n.structure.total().minX + m.x + 0.5,
                        BASE_Y - n.structure.total().minY + m.y,
                        baseZ(n) - n.structure.total().minZ + m.z + 0.5);
            }
        }
        return RoomGen.center(world, n, BASE_Y, spacing, offsetX, offsetZ);
    }

    /** World location of the SHOPKEEPER marker in a structure shop room, or null. */
    private Location shopkeeperLoc(Floor.RoomNode n) {
        if (n.structure == null) return null;
        List<RoomMarker> ms = n.structure.markersOf(RoomMarkerType.SHOPKEEPER);
        if (ms.isEmpty()) return null;
        RoomMarker m = ms.get(0);
        return new Location(world,
                baseX(n) - n.structure.total().minX + m.x + 0.5,
                BASE_Y - n.structure.total().minY + m.y,
                baseZ(n) - n.structure.total().minZ + m.z + 0.5);
    }

    /** Enemy spawn tiles from the structure's SPAWN_FLOOR markers, cycled, or null if none. */
    private List<Location> templateEnemySpawns(Floor.RoomNode n, int count) {
        if (n.structure == null) return null;
        List<SpawnFloor> floors = n.structure.spawnFloors;
        if (floors.isEmpty()) return null;
        List<Location> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SpawnFloor sf = floors.get(i % floors.size());
            out.add(new Location(world,
                    baseX(n) - n.structure.total().minX + (sf.minX + sf.maxX) / 2.0 + 0.5,
                    BASE_Y - n.structure.total().minY + sf.minY,
                    baseZ(n) - n.structure.total().minZ + (sf.minZ + sf.maxZ) / 2.0 + 0.5));
        }
        return out;
    }

    public void recomputeStats() {
        if (run == null) return;
        for (Player p : party.onlineMembers()) {
            PlayerState ps = run.playerStateOf(p.getUniqueId());
            if (ps != null) ps.recomputeStats();
        }
    }

    public void enterRoom(Floor.RoomNode n) {
        enterRoom(n, null);
    }

    public void enterRoom(Floor.RoomNode n, Player entering) {
        if (curRoom != null) curRoom.visited = true;
        curRoom = n;
        n.visited = true;
        run.floor.visited.add(n);
        long rk = run.floor.key(n.x, n.z);
        // Preserve the sealed state for a room that already spawned enemies: re-entering it
        // mid-fight (stepping out and back in) must not silently unlock it, or its doors would
        // stay open while enemies remain and the room's lock/unlock bookkeeping would drift.
        if (!spawnedRooms.contains(rk)) {
            roomLocked.put(rk, false);
        }

        long invulnMs = (n == run.floor.start ? 2500 : 1000);
        if (entering != null) {
            PlayerState ps = run.playerStateOf(entering.getUniqueId());
            if (ps != null) {
                ps.invulnUntil = System.currentTimeMillis() + invulnMs;
            }
        } else {
            for (Player p : party.onlineMembers()) {
                PlayerState ps = run.playerStateOf(p.getUniqueId());
                if (ps != null) {
                    ps.invulnUntil = System.currentTimeMillis() + invulnMs;
                }
            }
        }

        // Spawn + lock only once per room per floor: the spawnedRooms guard prevents duplicate mobs
        // if members re-enter a room, and the room is only locked if enemies actually spawned (so a
        // moment with no online reference player can never softlock an empty room). The room only
        // seals when EVERY party member is inside it, so fights start together.
        if (!n.cleared && (n.type == RoomType.COMBAT || n.type == RoomType.ELITE) && !spawnedRooms.contains(rk)) {
            if (allMembersInRoom(n)) {
                if (spawnEnemies(n)) {
                    spawnedRooms.add(rk);
                    lockDoors(n);
                }
            } else {
                for (Player p : party.onlineMembers()) {
                    setStatus(p, "§cThe room won't seal until everyone is inside.");
                }
            }
        }
        spawnRoomPickups(n);
        if (n.type == RoomType.SHOP) {
            setupShopRoom(n);
        }
        if (n.type == RoomType.UPGRADE) {
            setupUpgradeRoom(n);
        }
        if (n.type == RoomType.BOSS && !n.cleared && boss == null) {
            onRoomEnterBossCheck();
        }
    }

    /** Detect room crossings from any party member's movement. */
    public void onPlayerMoved(Player p, Location loc) {
        if (run == null || run.floor == null) return;
        Floor.RoomNode target = null;
        for (Floor.RoomNode rn : run.floor.rooms()) {
            if (insideRoom(loc, rn)) { target = rn; break; }
        }
        Floor.RoomNode prev = playerRoom.get(p.getUniqueId());
        if (target == null || target == prev) return;
        int dir = dirTo(prev != null ? prev : curRoom, target);
        if (!run.floor.visited.contains(target)) {
            if (dir < 0 || !(prev != null ? prev : curRoom).doors[dir]) {
                // Allow entry into revealed SECRET rooms even without a door connection
                if (target.type != RoomType.SECRET || !revealedSecrets.contains(run.floor.key(target.x, target.z))) {
                    return;
                }
            }
        }
        // LOCKED room check: if the target is a LOCKED room that hasn't been cleared,
        // block entry. The player must right-click the IRON_BLOCK barrier with a key item to unlock it.
        if (target.type == RoomType.LOCKED && !target.cleared) {
            p.sendMessage("§cThis room is locked — right-click the iron door with a key to unlock it!");
            // Teleport the player back to the center of the current room
            Location back = RoomGen.center(world, prev != null ? prev : curRoom, BASE_Y, spacing, offsetX, offsetZ);
            p.teleport(back);
            return;
        }
        playerRoom.put(p.getUniqueId(), target);
        enterRoom(target, p);
    }

    /** Remove the IRON_BLOCK door barrier for a LOCKED room. */
    private void removeLockedDoorBarrier(Floor.RoomNode n) {
        setLockedBarrier(n, Material.AIR);
    }

    /** Place the IRON_BLOCK door barrier for a LOCKED room. Runs as a post-pass after every room is
     *  built, so a neighbour room's corridor carve can never overwrite the locked door away. */
    private void placeLockedDoorBarrier(Floor.RoomNode n) {
        setLockedBarrier(n, Material.IRON_BLOCK);
    }

    /** Fill or clear the 3-wide, full-height doorway of a LOCKED room. Shared geometry with
     *  {@code sealDoors} so the barrier lines up with the carved door and its unlock click. */
    private void setLockedBarrier(Floor.RoomNode n, Material mat) {
        int[] DX = {0, 1, 0, -1};
        int[] DZ = {-1, 0, 1, 0};
        Location c = RoomGen.center(world, n, BASE_Y, spacing, offsetX, offsetZ);
        int bx = baseX(n), bz = baseZ(n);
        for (int d = 0; d < 4; d++) {
            if (!n.doors[d]) continue;
            boolean horiz = d == 1 || d == 3;
            int half = horiz ? n.sizeW / 2 : n.sizeH / 2;
            int wallX = c.getBlockX() + DX[d] * (half + RoomGen.WALL);
            int wallZ = c.getBlockZ() + DZ[d] * (half + RoomGen.WALL);
            int perpC = horiz ? (bz + RoomGen.PERP_CENTER) : (bx + RoomGen.PERP_CENTER);
            for (int off = -1; off <= 1; off++) {
                for (int y = BASE_Y + 1; y <= BASE_Y + RoomGen.ROOM_HEIGHT; y++) {
                    int px = horiz ? wallX : (perpC + off);
                    int pz = horiz ? (perpC + off) : wallZ;
                    world.getBlockAt(px, y, pz).setType(mat);
                }
            }
        }
    }

    private int dirTo(Floor.RoomNode a, Floor.RoomNode b) {
        int dx = b.x - a.x, dz = b.z - a.z;
        if (dx == 1) return 1;
        if (dx == -1) return 3;
        if (dz == 1) return 2;
        if (dz == -1) return 0;
        return -1;
    }

    private boolean insideRoom(Location loc, Floor.RoomNode rn) {
        return insideRoom(loc, rn, 0);
    }

    /** Like {@link #insideRoom(Location, Floor.RoomNode)} but allows a {@code margin} of extra blocks
     *  beyond the room's buildable footprint (used to give enemies a little slack around doorways
     *  without letting them block a room clear once they've strayed outside). */
    private boolean insideRoom(Location loc, Floor.RoomNode rn, double margin) {
        if (rn.structure != null) {
            // Structure room footprint (may differ from the procedural grid shell)
            RoomBounds t = rn.structure.total();
            double minX = baseX(rn) - t.minX - margin;
            double minZ = baseZ(rn) - t.minZ - margin;
            double maxX = minX + t.width() + margin * 2;
            double maxZ = minZ + t.depth() + margin * 2;
            return loc.getX() >= minX && loc.getX() < maxX
                    && loc.getZ() >= minZ && loc.getZ() < maxZ;
        }
        double ox = loc.getX() - (baseX(rn) + RoomGen.WALL);
        double oz = loc.getZ() - (baseZ(rn) + RoomGen.WALL);
        return ox >= -margin && ox < rn.sizeW + margin && oz >= -margin && oz < rn.sizeH + margin;
    }

    /** Check whether every online, alive party member is physically inside the given room (using their
     *  actual location, not the stale playerRoom map). Dead (spectator) players are skipped so they
     *  don't block combat start or room locking for the rest of the party. */
    private boolean allMembersInRoom(Floor.RoomNode n) {
        List<Player> members = party.onlineMembers();
        if (members.isEmpty()) return false;
        for (Player m : members) {
            if (deadPlayers.contains(m.getUniqueId())) continue;
            if (!insideRoom(m.getLocation(), n)) return false;
        }
        return true;
    }

    private boolean spawnEnemies(Floor.RoomNode n) {
        // A new combat/elite room triggering is the only gameplay event that clears lingering
        // elite hearts (floor change/teardown also clean up via clearRoomEntities).
        for (org.bukkit.entity.Item heart : eliteHearts) if (heart.isValid()) heart.remove();
        eliteHearts.clear();
        List<Enemy> list = new ArrayList<>();
        long k = run.floor.key(n.x, n.z);
        boolean elite = n.type == RoomType.ELITE;
        // Larger parties face more, tougher enemies so a group run isn't trivially easier than solo.
        int partySize = Math.max(1, party.onlineMembers().size());
        int baseCount = elite ? 3 : 2 + Math.min(run.floorIndex, 2);
        int count = baseCount * partySize;
        double hpMult = 1 + 0.3 * (partySize - 1);
        MobType[] comp = composeMobs(elite, count);

        // Use the first online player as reference for spawn placement
        Player refPlayer = party.onlineMembers().stream().findFirst().orElse(null);
        if (refPlayer == null) return false;

        List<Location> templateSpawns = templateEnemySpawns(n, count);
        for (int i = 0; i < count; i++) {
            Location l = templateSpawns != null ? templateSpawns.get(i) : placeRandomlyInRoom(n);
            MobType mt = comp[i];
            list.add(new Enemy(world, l, mt, run.floorIndex, n.x * 100 + n.z, refPlayer, hpMult));
        }
        roomEnemies.put(k, list);
        if (elite) {
            Enemy top = list.get(0);
            top.hp = top.maxHp * 1.6;
            top.maxHp = top.hp;
            top.entity.setCustomName(top.type.name + " §c" + (int) top.hp + "/" + (int) top.maxHp);
            if (top.entity instanceof org.bukkit.entity.LivingEntity le) {
                le.setMaxHealth(top.maxHp);
                le.setHealth(top.maxHp);
            }
        }
        return true;
    }

    /** Make a SHOP room read as a shop: spawn a named, no-AI Villager shopkeeper that
     *  looks at nearby players. Only spawned once per room per floor. */
    private void setupShopRoom(Floor.RoomNode n) {
        long k = run.floor.key(n.x, n.z);
        Location shopkeeper = shopkeeperLoc(n);
        if (shopkeeper != null) {
            if (shopSpawned.add(k)) spawnShopkeeper(shopkeeper);
            return;
        }
        Location c = RoomGen.center(world, n, BASE_Y, spacing, offsetX, offsetZ);
        if (shopSpawned.add(k)) spawnShopkeeper(c);
    }

    /** Spawn a passive, named Villager shopkeeper that looks at nearby players. */
    private void spawnShopkeeper(Location c) {
        Villager v = world.spawn(c.clone().add(2, 0, 1), Villager.class);
        v.setCustomName("§6Shopkeeper");
        v.setCustomNameVisible(true);
        v.setAI(false);
        v.setSilent(true);
        v.setInvulnerable(true);
        v.setCollidable(false);
        v.addScoreboardTag("dung.shopkeeper");
        shopkeepers.add(v);
    }

    /** Despawn all shopkeepers and forget this floor's spawned shop rooms. */
    private void clearShopkeepers() {
        for (Villager v : shopkeepers) {
            if (v.isValid()) v.remove();
        }
        shopkeepers.clear();
        shopSpawned.clear();
    }

    /** Make an UPGRADE room read as the unified progression workstation room: place the five physical
     *  workstation blocks around the room center, each with a floating name tag above it. The block
     *  material is decorative only — right-clicking routes by the registered {@link WorkstationType}.
     *  Spawned once per room per floor; cleaned up on floor change / teardown. */
    private void setupUpgradeRoom(Floor.RoomNode n) {
        long k = run.floor.key(n.x, n.z);
        if (!persistSpawned.add(k)) return;
        Location c = RoomGen.center(world, n, BASE_Y, spacing, offsetX, offsetZ);
        int floor = currentFloorNumber();
        WorkstationType[] types = WorkstationType.values();
        // Spread the five workstations in a row across the room with 1-block gaps between them.
        // Each workstation is placed at BASE_Y + 1 (one block above the floor) so they sit on a
        // raised platform. t = -4, -2, 0, 2, 4 gives 2 apart (1-block gap between each).
        double spread = (types.length - 1) * 2;
        for (int i = 0; i < types.length; i++) {
            WorkstationType wt = types[i];
            double t = spread == 0 ? 0 : ((double) i * 2 - spread / 2.0);
            // Place the workstation block one block above the floor (BASE_Y + 1), offset toward the
            // back wall so the row doesn't block the spawn point or the doorway lanes.
            // center() returns feet level (BASE_Y+1).
            Location blockLoc = new Location(world,
                    c.getBlockX() + t, BASE_Y + 1, c.getBlockZ() - 2);
            placeWorkstation(blockLoc, wt, floor);
        }
    }

    /** Place a single workstation block + its floating name tag, and register it for right-click.
     *  The name tag includes the relevant costs for that workstation type, scaled by floor tier. */
    private void placeWorkstation(Location blockLoc, WorkstationType wt, int floor) {
        world.getBlockAt(blockLoc).setType(wt.block);
        workstations.put(blockLoc.getBlock().getLocation(), wt);
        // Build a cost-annotated name tag so players can see prices at a glance.
        String costLine = costAnnotation(wt, floor);
        String name = wt.color + wt.label + (costLine.isEmpty() ? "" : "\n§7" + costLine);
        // Floating name tag: a small, invisible, non-interactive armor stand hovering above the block.
        org.bukkit.entity.ArmorStand stand = world.spawn(
                blockLoc.clone().add(0.5, 1.6, 0.5), org.bukkit.entity.ArmorStand.class);
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setGravity(false);
        stand.setAI(false);
        stand.setCollidable(false);
        stand.addScoreboardTag(wt.marker);
        workstationTags.add(stand);
    }

    /** Build a one-line cost annotation for a workstation type, scaled by the current floor. */
    private static String costAnnotation(WorkstationType wt, int floor) {
        return switch (wt) {
            case UPGRADE -> {
                int coin = WorkstationRules.scaledCost(WorkstationRules.UPGRADE_COIN_BASE, floor);
                int shard = WorkstationRules.scaledCost(WorkstationRules.UPGRADE_SHARD_BASE, floor);
                yield "§e" + coin + " coins §3" + shard + " shards";
            }
            case REFORGE -> {
                int shard = WorkstationRules.scaledCost(WorkstationRules.REFORGE_SHARD_COST, floor);
                yield "§3" + shard + " shards";
            }
            case PRESERVE -> {
                int coin = WorkstationRules.scaledCost(WorkstationRules.PRESERVE_COIN_COST, floor);
                int pcoin = WorkstationRules.scaledCost(WorkstationRules.PRESERVE_PERSISTENT_COIN_COST, floor);
                int shard = WorkstationRules.scaledCost(WorkstationRules.PRESERVE_SHARD_COST, floor);
                yield "§e" + coin + " coins §6" + pcoin + " pcoins §3" + shard + " shards";
            }
            case SALVAGE -> "§7Gives run coins";
            case STORAGE -> "";
        };
    }

    /** The workstation registered at a block location, or null if none. */
    public WorkstationType workstationAt(Location blockLoc) {
        return workstations.get(blockLoc.getBlock().getLocation());
    }

    /** Despawn all workstation name tags and forget this floor's spawned upgrade rooms. */
    private void clearWorkstations() {
        for (org.bukkit.entity.ArmorStand s : workstationTags) {
            if (s.isValid()) s.remove();
        }
        workstationTags.clear();
        workstations.clear();
        persistSpawned.clear();
    }

    private static final MobType[] WEAK = {MobType.GAPER, MobType.FLY, MobType.SPIDER};
    private static final MobType[] STRONG = {MobType.MULLIBOOM, MobType.CHARGER, MobType.MAW};
    private static final MobType[] ELITES = {MobType.ELITE_GAPER, MobType.ELITE_CHARGER};
    private static final MobType[] RANGED = {MobType.MAW, MobType.GAPER};

    private MobType[] composeMobs(boolean elite, int count) {
        MobType[] out = new MobType[count];
        if (elite) {
            out[0] = ELITES[ThreadLocalRandom.current().nextInt(ELITES.length)];
            for (int i = 1; i < count; i++) out[i] = pickWeighted(STRONG);
            return out;
        }
        out[0] = pickWeighted(WEAK);
        int chargers = 0, maws = 0;
        for (int i = 1; i < count; i++) {
            MobType m;
            double r = ThreadLocalRandom.current().nextDouble();
            if (r < 0.35) {
                m = pickWeighted(WEAK);
            } else if (r < 0.55) {
                m = MobType.MULLIBOOM;
            } else if (r < 0.70 && chargers == 0) {
                m = MobType.CHARGER; chargers++;
            } else if (r < 0.85 && maws == 0) {
                m = MobType.MAW; maws++;
            } else {
                m = pickWeighted(RANGED);
            }
            out[i] = m;
        }
        return out;
    }

    private MobType pickWeighted(MobType[] pool) {
        return pool[ThreadLocalRandom.current().nextInt(pool.length)];
    }

    /** Place an enemy at a random walkable position inside the room, avoiding walls. */
    private Location placeRandomlyInRoom(Floor.RoomNode n) {
        int minX = baseX(n) + RoomGen.WALL;
        int minZ = baseZ(n) + RoomGen.WALL;
        int maxX = minX + n.sizeW - 1;
        int maxZ = minZ + n.sizeH - 1;
        int y = BASE_Y + 1;
        // Try up to 20 random positions to find a walkable spot (not inside a wall)
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = minX + ThreadLocalRandom.current().nextInt(n.sizeW);
            int z = minZ + ThreadLocalRandom.current().nextInt(n.sizeH);
            Location l = new Location(world, x + 0.5, y, z + 0.5);
            Material block = world.getBlockAt(x, y, z).getType();
            Material above = world.getBlockAt(x, y + 1, z).getType();
            // Must be air (or floor material) at foot level and air at head level
            if (block != Material.AIR && block != Material.POLISHED_ANDESITE
                    && block != Material.GOLD_BLOCK && block != Material.QUARTZ_BLOCK
                    && block != Material.OAK_PLANKS && block != Material.SPRUCE_PLANKS
                    && block != Material.NETHER_BRICKS && block != Material.RED_NETHER_BRICKS
                    && block != Material.DEEPSLATE_BRICKS && block != Material.POLISHED_BLACKSTONE_BRICKS
                    && block != Material.SMOOTH_STONE && block != Material.POLISHED_DIORITE
                    && block != Material.MOSSY_STONE_BRICKS && block != Material.MOSSY_COBBLESTONE) continue;
            if (above != Material.AIR) continue;
            return l;
        }
        // Fallback: center of the room
        return new Location(world, minX + n.sizeW / 2.0 + 0.5, y, minZ + n.sizeH / 2.0 + 0.5);
    }

    private void lockDoors(Floor.RoomNode n) {
        long k = run.floor.key(n.x, n.z);
        roomLocked.put(k, true);
        sealDoors(n, true);
        Location c = RoomGen.center(world, n, BASE_Y, spacing, offsetX, offsetZ);
        world.playSound(c, org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.9f);
        world.spawnParticle(org.bukkit.Particle.CRIT, c.clone().add(0, 1.5, 0), 24, 1.5, 1.5, 8);
        for (Player p : party.onlineMembers()) {
            setStatus(p, "§cRoom locked — defeat all enemies!");
        }
    }

    private void sealDoors(Floor.RoomNode n, boolean close) {
        int[] DX = {0, 1, 0, -1};
        int[] DZ = {-1, 0, 1, 0};
        if (n.structure != null) {
            // Structure room: seal the doorway opening itself, on the shared corridor line (same
            // position carveStructureDoors carved it), so the bars line up with the opening.
            RoomBounds t = n.structure.total();
            for (int d = 0; d < 4; d++) {
                if (!n.doors[d]) continue;
                boolean horiz = d == 1 || d == 3;
                int wallAlong = facingWallAlong(n, d, t);
                int perpC = horiz ? (baseZ(n) + RoomGen.PERP_CENTER) : (baseX(n) + RoomGen.PERP_CENTER);
                for (int off = -1; off <= 1; off++) {
                    for (int y = BASE_Y + 1; y <= BASE_Y + RoomGen.ROOM_HEIGHT; y++) {
                        int px = horiz ? wallAlong : (perpC + off);
                        int pz = horiz ? (perpC + off) : wallAlong;
                        if (close) {
                            world.getBlockAt(px, y, pz).setType(Material.IRON_BARS);
                        } else if (world.getBlockAt(px, y, pz).getType() == Material.IRON_BARS) {
                            world.getBlockAt(px, y, pz).setType(Material.AIR);
                        }
                    }
                }
            }
            return;
        }
        Location c = RoomGen.center(world, n, BASE_Y, spacing, offsetX, offsetZ);
        int bx = baseX(n), bz = baseZ(n);
        for (int d = 0; d < 4; d++) {
            if (!n.doors[d]) continue;
            boolean horiz = d == 1 || d == 3;
            int half = horiz ? n.sizeW / 2 : n.sizeH / 2;
            int wallX = c.getBlockX() + DX[d] * (half + RoomGen.WALL);
            int wallZ = c.getBlockZ() + DZ[d] * (half + RoomGen.WALL);
            int perpC = horiz ? (bz + RoomGen.PERP_CENTER) : (bx + RoomGen.PERP_CENTER);
            for (int off = -1; off <= 1; off++) {
                for (int y = BASE_Y + 1; y <= BASE_Y + RoomGen.ROOM_HEIGHT; y++) {
                    int px = horiz ? wallX : (perpC + off);
                    int pz = horiz ? (perpC + off) : wallZ;
                    if (close) {
                        world.getBlockAt(px, y, pz).setType(Material.IRON_BARS);
                    } else if (world.getBlockAt(px, y, pz).getType() == Material.IRON_BARS) {
                        world.getBlockAt(px, y, pz).setType(Material.AIR);
                    }
                }
            }
        }
    }

    private static ItemStack[] gearSnapshot(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] s = new ItemStack[5];
        s[0] = inv.getItemInMainHand();
        System.arraycopy(inv.getArmorContents(), 0, s, 1, 4);
        return s;
    }

    private void spawnRoomPickups(Floor.RoomNode n) {
        if (!n.looted && n.type == RoomType.TREASURE) {
            n.looted = true;
            Location c = roomSpawn(n);
            List<ItemStack> loot = ItemPool.roomReward(run.floorIndex, 2);
            for (int i = 0; i < 3; i++) {
                ItemStack s = (i < loot.size()) ? loot.get(i) : null;
                if (s == null) s = ItemPool.randomArmor(run.floorIndex, i % 4);
                spawnPedestal(c.clone().add((i - 1) * 2, 0, 0), s);
            }
        }
        // LOCKED rooms: bonus loot like TREASURE but requires a key to enter.
        // Loot is spawned when the key is used (in onPlayerMoved), but also handle
        // the case where enterRoom is called directly (e.g., first entry after unlock).
        if (!n.looted && n.type == RoomType.LOCKED) {
            n.looted = true;
            Location c = roomSpawn(n);
            List<ItemStack> loot = ItemPool.roomReward(run.floorIndex, 2);
            for (int i = 0; i < 3; i++) {
                ItemStack s = (i < loot.size()) ? loot.get(i) : null;
                if (s == null) s = ItemPool.randomArmor(run.floorIndex, i % 4);
                spawnPedestal(c.clone().add((i - 1) * 2, 0, 0), s);
            }
        }
        // SECRET rooms: loot is spawned when the wall is bombed, not on room entry
        // (handled in revealSecretRoom)
    }

    private void dropGear(Location c, int count, int roomKind) {
        List<ItemStack> loot = ItemPool.roomReward(run.floorIndex, roomKind);
        for (int i = 0; i < count; i++) {
            ItemStack s = (i < loot.size()) ? loot.get(i) : null;
            if (s == null) s = ItemPool.randomArmor(run.floorIndex, i % 4);
            spawnPedestal(c.clone().add((i - (count - 1) / 2.0) * 2, 0, 0), s);
        }
    }

    // ---------- elite heart drops ----------

    /** Ground hearts dropped by dying elites; picked up freely once their pickup delay expires,
     *  and cleared only when a new combat/elite room triggers (plus floor change/teardown). */
    private final List<org.bukkit.entity.Item> eliteHearts = new ArrayList<>();
    /** Hearts can't be picked up for the first 1.75 seconds after landing. */
    private static final int ELITE_HEART_PICKUP_DELAY_TICKS = 35;

    /** An exploding elite bursts into hearts that scatter around the room: 3 solo, then
     *  5/6/7 for party sizes 2/3/4+. They linger until a new combat room is triggered. */
    private void scatterEliteHearts(Location deathLoc, Floor.RoomNode rn) {
        if (world == null || run == null) return;
        int members = Math.max(1, party.onlineMembers().size());
        int count = switch (Math.min(members, 4)) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 6;
            default -> 7;
        };
        Location base = roomSpawn(rn);
        for (int i = 0; i < count; i++) {
            Location at = randomRoomSpot(base, deathLoc, rn);
            org.bukkit.entity.Item drop = world.dropItem(at, Pickup.stack(Material.RED_DYE));
            drop.setPickupDelay(ELITE_HEART_PICKUP_DELAY_TICKS); // 3s before they become grabbable
            drop.setUnlimitedLifetime(false); // never persist beyond our own cleanup
            eliteHearts.add(drop);
        }
    }

    /** A random spot inside the room (falls back to the death location after a few tries). */
    private Location randomRoomSpot(Location center, Location fallback, Floor.RoomNode rn) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double dx = (ThreadLocalRandom.current().nextDouble() - 0.5) * 6.0;
            double dz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 6.0;
            Location at = center.clone().add(dx, 0.5, dz);
            if (insideRoom(at, rn, 1.0)) return at;
        }
        return fallback.add(0, 0.5, 0);
    }

    /** Floor-entry spawn: a random spot inside the room, looking toward the room's center. */
    private Location scatterSpawn(Floor.RoomNode room) {
        Location center = roomSpawn(room).add(0, 1, 0); // eye height target
        Location at = randomRoomSpot(center.clone(), center.clone(), room);
        at.setDirection(center.toVector().subtract(at.toVector()).setY(0));
        return at;
    }

    // ---------- combat tick ----------

    public void tick() {
        if (!running || !anyOnline()) return;
        tickCounter++;

        // Check death for each player
        for (Player p : party.onlineMembers()) {
            PlayerState st = run.playerStateOf(p.getUniqueId());
            if (st == null) continue;
            if (st.dead) {
                onPlayerDeath(p);
                continue;
            }
            if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL && p.isDead()) {
                onPlayerDeath(p);
                if (p.isDead()) p.spigot().respawn();
                continue;
            }

            // Sync gear
            ItemStack[] gearNow = gearSnapshot(p);
            ItemStack[] last = lastGear.get(p.getUniqueId());
            if (!java.util.Arrays.equals(gearNow, last)) {
                lastGear.put(p.getUniqueId(), gearNow);
                st.recomputeStats();
            }

            // Apply speed
            float ws = (float) Math.min(0.3, 0.2 * st.speedMult);
            p.setWalkSpeed(ws);
        }

        // fireCd for melee — decrement per-player cooldown
        for (Player p : party.onlineMembers()) {
            PlayerState ps = run.playerStateOf(p.getUniqueId());
            if (ps != null) ps.fireCd = Math.max(0, ps.fireCd - 1);
        }

        // Room clear + enemy ticking for EVERY room a party member currently occupies, so members
        // split across rooms each progress correctly (no global curRoom clobbering softlock).
        // Also check rooms that no player is in (e.g. a player died in the room and was removed
        // from playerRoom) so they still clear when all enemies are dead. This applies to ALL
        // room types, not just locked rooms — a Mulliboom explosion can kill the last player in
        // a combat room, and without this fallback the dead enemies would never be cleaned up.
        java.util.Set<Floor.RoomNode> activeNodes = new java.util.LinkedHashSet<>();
        for (Player m : party.onlineMembers()) {
            Floor.RoomNode rn = playerRoom.get(m.getUniqueId());
            if (rn != null) activeNodes.add(rn);
        }
        // Also include any rooms that still have enemies (orphaned by player death/leave)
        for (java.util.Map.Entry<Long, List<Enemy>> entry : roomEnemies.entrySet()) {
            // Reconstruct x,z from the key (same formula as Floor.key)
            long k = entry.getKey();
            int rx = (int) (k / 4096);
            int rz = (int) (k % 4096);
            Floor.RoomNode rn = run.floor.at(rx, rz);
            if (rn != null) activeNodes.add(rn);
        }
        for (Floor.RoomNode rn : activeNodes) {
            long k = run.floor.key(rn.x, rn.z);
            List<Enemy> list = roomEnemies.get(k);
            // Activate combat/elite rooms from the tick as a fallback to the enterRoom event, so a
            // room can never stay dormant if everyone is already alive and inside it but the last
            // member didn't trigger an enterRoom transition (e.g. they were already tracked as in it).
            if (!rn.cleared && (rn.type == RoomType.COMBAT || rn.type == RoomType.ELITE)
                    && !spawnedRooms.contains(k) && list == null) {
                if (allMembersInRoom(rn) && spawnEnemies(rn)) {
                    spawnedRooms.add(k);
                    lockDoors(rn);
                }
            }
            list = roomEnemies.get(k);
            if (list == null) continue;
            // Room cleared check — runs for ANY room that has spawned enemies, whether or not its
            // doors are currently sealed, so a re-entered combat room still clears when the last
            // enemy dies. Gating the clear on roomLocked let a re-entered room (whose locked flag
            // was reset by enterRoom) sit forever with dead mobs never removed from the list.
            int before = list.size();
            // An enemy that has strayed well outside the room (escaped through a wall/door) is
            // despawned and dropped so it can't leave the room without ever dying and permanently
            // block the clear.
            java.util.Iterator<Enemy> it = list.iterator();
            while (it.hasNext()) {
                Enemy e = it.next();
                if (e.dead || !e.alive() || !insideRoom(e.entity.getLocation(), rn, 3.0)) {
                    boolean died = e.dead || !e.alive(); // stray escapes despawn without dying
                    if (!e.dead && e.alive()) e.despawn();
                    it.remove();
                    if (died && e.type.isElite()) scatterEliteHearts(e.entity.getLocation(), rn);
                }
            }
            if (list.size() < before && run != null) run.kills += (before - list.size());
            if (list.isEmpty()) {
                onRoomClear(rn, k);
                continue;
            }
            // Tick enemies against the nearest alive party member
            for (Enemy e : list) {
                Player nearest = nearestPlayer(e.entity.getLocation());
                if (nearest != null) e.tick(nearest, 50);
            }
        }

        if (boss != null) {
            Player nearest = nearestPlayer(boss.location());
            if (nearest != null) boss.tick(nearest);
        }

        // Make each shopkeeper look at the nearest party member so the villager faces players
        // on every client's side (no-AI villagers don't naturally track players).
        for (Villager v : shopkeepers) {
            if (!v.isValid()) continue;
            Player nearest = nearestPlayer(v.getLocation());
            if (nearest != null) {
                Location vl = v.getLocation().clone();
                vl.setDirection(nearest.getLocation().subtract(vl).toVector());
                v.teleport(vl);
            }
        }

        // Resources and HP sync for each player (skip dead players — they're spectators
        // and shouldn't have run items re-synced into their inventory).
        for (Player p : party.onlineMembers()) {
            PlayerState st = run.playerStateOf(p.getUniqueId());
            if (st == null || st.dead) continue;
            st.regenHearts();
            // Mana Shield: the active shield is the one placed in hotbar slot 9 (SHIELD_SLOT). Everything
            // shield-related (capacity, charging, absorption, durability) watches that slot only —
            // the shield no longer activates from the main hand or anywhere else in the inventory.
            PlayerInventory inv = p.getInventory();
            ItemStack shieldItem = inv.getItem(SHIELD_SLOT);
            boolean hasActiveShield = shieldItem != null && !shieldItem.getType().isAir()
                    && GearFactory.isShield(shieldItem);
            if (hasActiveShield) {
                st.shieldMax = GearFactory.getShieldMax(shieldItem);
                st.shieldActive = true;
            } else {
                st.shieldMax = 0;
                st.shieldActive = false;
            }
            if (hasActiveShield && inv.getHeldItemSlot() == SHIELD_SLOT && p.isSneaking()) {
                // Spend ~15 mana per second (0.75 per tick) to charge the shield. Once fully
                // charged, stop consuming mana (the shield sits at max instead of bleeding mana).
                // Charging only works while the shield is actively held (slot 9 selected); the
                // equipped shield still absorbs damage regardless of what's in the main hand.
                if (st.shield < st.shieldMax && st.mana >= 0.75) {
                    st.mana -= 0.75;
                    st.shield = Math.min(st.shieldMax, st.shield + 0.75);
                }
            } else {
                // Not actively charging: the shield's stored charge bleeds away every 0.5s (10 ticks).
                if (st.shield > 0 && tickCounter % 10 == 0) {
                    st.shield = Math.max(0, st.shield - 1.0);
                }
            }
            // Always sync shield durability on the slot-9 shield so the charge bar updates even
            // when it changed without the slot being re-synced.
            syncShieldDurability(p, shieldItem, st);
            // A fully charged active shield halts mana regeneration (the shield is the active drain)
            if (!(st.shieldActive && st.shield >= st.shieldMax)) {
                st.regenMana();
            }
            final double real = Math.min(20.0, Math.max(0.1, st.hearts / st.maxHearts * 20.0));
            if (Math.abs(p.getHealth() - real) > 1.0E-4) p.setHealth(real);
            p.setSaturation(0);
            p.setFoodLevel(10);
            // Sync key/bomb hotbar items
            syncHotbarItems(p);

            // Perfection particles: END_ROD sparkles around items at max upgrade level
            spawnPerfectionParticles(p);
        }

        // Spectator effect: white smoke sprinkles from the heads of dead (spectator) players
        if (world != null && tickCounter % 2 == 0) {
            for (UUID spectatorId : deadPlayers) {
                Player spec = Bukkit.getPlayer(spectatorId);
                if (spec == null || !spec.isOnline() || spec.getGameMode() != org.bukkit.GameMode.SPECTATOR) continue;
                world.spawnParticle(org.bukkit.Particle.WHITE_SMOKE,
                        spec.getLocation().add(0, 1.8, 0), 3, 0.25, 0.15, 0.25, 0.01);
            }
        }

        refreshUI();
    }

    /** Reflect the current shield charge on the active slot-9 shield's durability bar. The charge is a
     *  per-run transient value in PlayerState; the item's native durability is repurposed to display
     *  it (full charge = full bar, empty = empty). Only writes to the inventory when the displayed
     *  value actually changes, so idle ticks don't resync the slot. */
    private void syncShieldDurability(Player p, ItemStack shield, PlayerState st) {
        if (shield == null || shield.getType().isAir() || !GearFactory.isShield(shield)) return;
        if (!(shield.getItemMeta() instanceof Damageable dmg)) return;
        int nativeMax = shield.getType().getMaxDurability();
        double pct = st.shieldMax <= 0 ? 0 : Math.min(1.0, st.shield / st.shieldMax);
        int damage = (int) Math.round(nativeMax * (1.0 - pct));
        if (dmg.getDamage() != damage) {
            dmg.setDamage(damage);
            shield.setItemMeta((org.bukkit.inventory.meta.ItemMeta) dmg);
            p.getInventory().setItem(SHIELD_SLOT, shield);
        }
    }

    /** Spawn END_ROD perfection particles around items at max upgrade level.
     *  Particles are nearly stationary and linger for a long time (low speed, high count).
     *  Position depends on the item slot:
     *  - Held item (main hand): around the player's hand area
     *  - Helmet (slot 39): around the head
     *  - Chestplate (slot 38): around the chest
     *  - Leggings (slot 37): around the legs
     *  - Boots (slot 36): around the feet
     *  Runs every tick so particles are continuous. */
    private void spawnPerfectionParticles(Player p) {
        if (world == null) return;
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        // Check main hand
        ItemStack held = inv.getItemInMainHand();
        if (held != null && !held.getType().isAir()
                && GearFactory.getUpgradeLevel(held) >= WorkstationRules.UPGRADE_MAX) {
            // Around the hand — offset slightly forward and to the right
            Location handLoc = p.getLocation().add(p.getEyeLocation().getDirection().multiply(0.5))
                    .add(0, -0.3, 0);
            world.spawnParticle(org.bukkit.Particle.END_ROD, handLoc, 2, 0.15, 0.15, 0.15, 0.001);
        }
        // Check armor slots: 36=boots, 37=leggings, 38=chestplate, 39=helmet
        int[] armorSlots = {39, 38, 37, 36};
        double[] yOffsets = {1.7, 1.0, 0.5, 0.1}; // head, chest, legs, feet
        for (int i = 0; i < armorSlots.length; i++) {
            ItemStack armor = inv.getItem(armorSlots[i]);
            if (armor != null && !armor.getType().isAir()
                    && GearFactory.getUpgradeLevel(armor) >= WorkstationRules.UPGRADE_MAX) {
                Location armorLoc = p.getLocation().add(0, yOffsets[i], 0);
                world.spawnParticle(org.bukkit.Particle.END_ROD, armorLoc, 2, 0.2, 0.15, 0.2, 0.001);
            }
        }
    }

    /** Find the nearest online, alive (non-spectator) party member to a location. */
    private Player nearestPlayer(Location loc) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player p : party.onlineMembers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(loc);
            if (d < best) { best = d; nearest = p; }
        }
        return nearest;
    }

    public void registerAttack(Player p) {
        PlayerState ps = run.playerStateOf(p.getUniqueId());
        if (!running || ps == null || ps.fireCd > 0) return;

        // Apply damage boost (War Cry) and guaranteed crit (Shadow Step)
        double baseDmg = ps.damage;
        if (ps.hasDamageBoost()) baseDmg *= ps.damageBoostMult;
        boolean guaranteeCrit = ps.hasGuaranteedCrit();

        // Check if the held weapon is Life Drain (Soul Siphon) for stored health tracking
        ItemStack held = p.getInventory().getItemInMainHand();
        // A broken weapon can't be used to attack until it is repaired (but it stays in the inventory).
        if (GearFactory.isPersistent(held) && GearFactory.isBroken(held)) {
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cYour weapon is broken — repair it at §6/shop§7 before attacking."));
            return;
        }
        boolean isLifeDrain = held != null && !held.getType().isAir()
                && held.getItemMeta() != null
                && held.getItemMeta().getPersistentDataContainer()
                        .has(org.bukkit.NamespacedKey.minecraft(ItemTags.ABILITY),
                             org.bukkit.persistence.PersistentDataType.STRING)
                && "Life Drain".equals(held.getItemMeta().getPersistentDataContainer()
                        .get(org.bukkit.NamespacedKey.minecraft(ItemTags.ABILITY),
                             org.bukkit.persistence.PersistentDataType.STRING));

        // A full Soul Siphon is unusable — it deals no damage until its stored health is spent.
        if (isLifeDrain && GearFactory.getStoredHealth(held) >= GearFactory.getStoredHealthMax(held)) {
            p.sendMessage("§cSoul Siphon is full! Shift+left-click to heal yourself with its stored health.");
            return;
        }

        ps.fireCd = ps.fireRateTicks;

        org.bukkit.util.Vector dir = p.getEyeLocation().getDirection().normalize();
        Floor.RoomNode attackRoom = playerRoom.get(p.getUniqueId());
        if (attackRoom == null) attackRoom = curRoom;
        long k = run.floor.key(attackRoom.x, attackRoom.z);
        List<Enemy> roomList = roomEnemies.getOrDefault(k, List.of());
        Location eyeBase = p.getEyeLocation().clone();
        // A basic swing hits only the nearest few enemies within reach (not every mob in the room).
        java.util.List<Enemy> meleeCand = new java.util.ArrayList<>();
        for (Enemy e : roomList) {
            if (e.dead) continue;
            Location el = e.entity.getLocation().clone();
            double horiz = Math.hypot(el.getX() - eyeBase.getX(), el.getZ() - eyeBase.getZ());
            double vert = Math.abs(el.getY() - eyeBase.getY());
            if (horiz < ps.reach && vert < 2.0) meleeCand.add(e);
        }
        meleeCand.sort(java.util.Comparator.comparingDouble(
                e -> e.entity.getLocation().distanceSquared(eyeBase)));
        int meleeTargets = 3;
        int hitN = Math.min(meleeTargets, meleeCand.size());
        // Track per-enemy damage for Life Drain (each enemy contributes independently)
        double[] enemyDmg = new double[hitN];
        for (int i = 0; i < hitN; i++) {
            Enemy e = meleeCand.get(i);
            boolean crit = guaranteeCrit || Math.random() < ps.critChance;
            double dmg = baseDmg * (crit ? ps.critMult : 1.0);
            e.damage(dmg, p, dir.getX(), dir.getZ());
            enemyDmg[i] = dmg;
        }
        double bossDmg = 0;
        if (boss != null && boss.isActive()) {
            Location bl = boss.location();
            double horiz = Math.hypot(bl.getX() - eyeBase.getX(), bl.getZ() - eyeBase.getZ());
            double vert = Math.abs(bl.getY() - eyeBase.getY());
            if (horiz < ps.reach + 0.5 && vert < 3.0) {
                boolean crit = guaranteeCrit || Math.random() < ps.critChance;
                double dmg = baseDmg * (crit ? ps.critMult : 1.0);
                boss.damage(dmg, p);
                bossDmg = dmg;
            }
        }
        // Life Drain: add 50% of actual damage dealt per enemy to the weapon's stored health.
        // Each enemy contributes independently (not summed), so hitting 3 enemies stores 3x.
        if (isLifeDrain) {
            Location pLoc = p.getLocation().add(0, 1, 0);
            for (int i = 0; i < hitN; i++) {
                Enemy e = meleeCand.get(i);
                if (e.dead) continue;
                int stored = (int) Math.round(enemyDmg[i] * 0.5);
                if (stored > 0) {
                    int current = GearFactory.getStoredHealth(held);
                    GearFactory.setStoredHealth(held, current + stored);
                }
                // Spawn damage_indicator particles from this enemy to the player
                Location eLoc = e.entity.getLocation().add(0, 1, 0);
                org.bukkit.util.Vector step = eLoc.toVector().subtract(pLoc.toVector()).multiply(0.1);
                for (int t = 0; t < 10; t++) {
                    Location pt = pLoc.clone().add(step.clone().multiply(t));
                    world.spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, pt, 1, 0, 0, 0, 0);
                }
            }
            // Also from boss if hit
            if (bossDmg > 0) {
                Location bl = boss.location().add(0, 1, 0);
                int stored = (int) Math.round(bossDmg * 0.5);
                if (stored > 0) {
                    int current = GearFactory.getStoredHealth(held);
                    GearFactory.setStoredHealth(held, current + stored);
                }
                org.bukkit.util.Vector step = bl.toVector().subtract(pLoc.toVector()).multiply(0.1);
                for (int t = 0; t < 10; t++) {
                    Location pt = pLoc.clone().add(step.clone().multiply(t));
                    world.spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, pt, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private static final Map<String, long[]> ABILITY_COST_CD = Map.of(
        "Rush",           new long[]{ 5, 1000 },
        "Slash",          new long[]{ 12, 2500 },
        "Cleave",         new long[]{ 15, 3000 },
        "Smash",          new long[]{ 18, 3500 },
        "Blade Storm",    new long[]{ 25, 4500 },
        "Arcane Bolt",    new long[]{ 20, 3500 },
        "Ravage",         new long[]{ 40, 8000 },
        "Chain Lightning",new long[]{ 35, 5000 },
        "Fireball",       new long[]{ 25, 3000 },
        "Life Drain",     new long[]{ 20, 3000 }
    );
    private static final long[] DEFAULT_ABILITY_COST_CD = new long[]{ 15, 3500 };

    // Class-specific active abilities: [manaCost, cooldownMs]
    private static final Map<String, long[]> CLASS_ABILITY_COST_CD = Map.of(
        "warrior", new long[]{ 10, 8000 },
        "mage",    new long[]{ 25, 6000 },
        "ranger",  new long[]{ 15, 5000 }
    );

    public void tryCastAbility(Player p, ItemStack item) {
        if (!running || item == null) return;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) return;
        var pdc = item.getItemMeta() == null ? null : item.getItemMeta().getPersistentDataContainer();
        if (pdc == null || !pdc.has(org.bukkit.NamespacedKey.minecraft("dung.ability"),
                org.bukkit.persistence.PersistentDataType.STRING)) {
            p.sendMessage("§cYour hand item has no ability.");
            return;
        }
        String id = pdc.get(org.bukkit.NamespacedKey.minecraft("dung.ability"),
                org.bukkit.persistence.PersistentDataType.STRING);
        Integer costI = pdc.get(org.bukkit.NamespacedKey.minecraft("dung.cost"),
                org.bukkit.persistence.PersistentDataType.INTEGER);
        long[] cfg = ABILITY_COST_CD.get(id);
        if (cfg == null) cfg = DEFAULT_ABILITY_COST_CD;
        double cost = costI != null ? costI : cfg[0];
        long cd = cfg[1];
        if (!st.canCast(id, cost, cd)) {
            p.sendMessage("§cNot enough mana or on cooldown.");
            return;
        }
        if (!st.canCast(PlayerState.GCD_KEY, 0, PlayerState.GCD_MS)) {
            p.sendMessage("§cToo fast!");
            return;
        }
        // A broken weapon cannot be used (its ability is unusable) until it is repaired.
        if (GearFactory.isPersistent(item) && GearFactory.isBroken(item)) {
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cThis item is broken — repair it at §6/shop§7 before using its ability."));
            return;
        }
        st.spendMana(cost);
        st.startCooldown(PlayerState.GCD_KEY, PlayerState.GCD_MS);
        st.startCooldown(id, cd);
        dispatchAbility(id, st, p);
        // Persistent weapons lose 1-2 durability when their ability is used
        if (item != null && GearFactory.isPersistent(item)) {
            int dmg = ThreadLocalRandom.current().nextInt(1, 3); // 1 or 2
            boolean broken = GearFactory.damageItem(item, dmg);
            if (broken) {
                // Keep the broken item in the inventory (it can be repaired at the shop); it is no
                // longer usable until repaired.
                p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cYour " + item.getItemMeta().getDisplayName() + " §cbroke and can no longer be used! §7Repair at §6/shop§7 (150 coins + 100 shards for 10 durability)."));
            }
        }
    }

    /**
     * Cast the player's class-specific active ability.
     * Triggered by sneak + drop (Q) while in a run.
     */
    public void tryCastClassAbility(Player p) {
        if (!running) return;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) return;

        String classId = st.classId;
        long[] cfg = CLASS_ABILITY_COST_CD.get(classId);
        if (cfg == null) {
            p.sendMessage("§cYour class has no active ability.");
            return;
        }
        double cost = cfg[0];
        long cd = cfg[1];
        String abilityKey = "class_" + classId;
        if (!st.canCast(abilityKey, cost, cd)) {
            p.sendMessage("§cNot enough mana or on cooldown.");
            return;
        }
        if (!st.canCast(PlayerState.GCD_KEY, 0, PlayerState.GCD_MS)) {
            p.sendMessage("§cToo fast!");
            return;
        }
        st.spendMana(cost);
        st.startCooldown(PlayerState.GCD_KEY, PlayerState.GCD_MS);
        st.startCooldown(abilityKey, cd);
        dispatchClassAbility(classId, st, p);
    }

    private void dispatchClassAbility(String classId, PlayerState st, Player caster) {
        Floor.RoomNode casterRoom = playerRoom.get(caster.getUniqueId());
        if (casterRoom == null) casterRoom = curRoom;
        long k = run.floor.key(casterRoom.x, casterRoom.z);
        List<Enemy> roomList = roomEnemies.getOrDefault(k, List.of());
        // Mage's Arcane Nova uses magic damage if available
        double baseDmg = "mage".equals(classId) && st.magicDamage > 0 ? st.magicDamage : st.damage;
        double dmg = baseDmg * (Math.random() < st.critChance ? st.critMult : 1.0);
        org.bukkit.util.Vector dir = caster.getEyeLocation().getDirection().normalize();

        switch (classId) {
            case "warrior":
                // War Cry: boost all party members' damage by 30% for 5 seconds, brief invuln
                long warCryUntil = System.currentTimeMillis() + 5000;
                for (Player pm : party.onlineMembers()) {
                    PlayerState pst = run.playerStateOf(pm.getUniqueId());
                    if (pst != null) {
                        pst.damageBoostUntil = warCryUntil;
                        pst.damageBoostMult = 1.3;
                        pm.sendMessage("§6War Cry! Damage boosted by 30% for 5s!");
                    }
                }
                st.invulnUntil = Math.max(st.invulnUntil, System.currentTimeMillis() + 1000);
                world.spawnParticle(org.bukkit.Particle.FLASH, caster.getLocation().add(0, 1, 0), 1, 0, 0, 0);
                world.spawnParticle(org.bukkit.Particle.CRIT, caster.getLocation().add(0, 1, 0), 20, 1.5, 1, 1.5);
                caster.sendMessage("§6§lWAR CRY!");
                break;

            case "mage":
                // Arcane Nova: AoE damage (2x) divided among all enemies within 5 blocks
                java.util.function.DoubleConsumer hitBossNova = (radius) -> {
                    if (boss != null && boss.isActive() && boss.location().distance(caster.getLocation()) < radius) {
                        boss.damage(dmg * 2.0, caster);
                    }
                };
                hitBossNova.accept(5.0);
                java.util.List<Enemy> novaTargets = new java.util.ArrayList<>();
                for (Enemy e : roomList) {
                    if (!e.dead && e.entity.getLocation().distance(caster.getLocation()) < 5) {
                        novaTargets.add(e);
                    }
                }
                double novaDmg = novaTargets.isEmpty() ? 0 : (dmg * 2.0) / novaTargets.size();
                for (Enemy e : novaTargets) {
                    e.damage(novaDmg, caster, 0, 0);
                }
                world.spawnParticle(org.bukkit.Particle.CRIT, caster.getLocation().add(0, 1, 0), 40, 2, 1, 2);
                world.spawnParticle(org.bukkit.Particle.PORTAL, caster.getLocation().add(0, 1, 0), 20, 2, 1, 2);
                world.playSound(caster.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.7f);
                caster.sendMessage("§d§lARCANE NOVA!");
                break;

            case "ranger":
                // Shadow Step: teleport behind the nearest enemy, guarantee next crit
                Enemy target = null;
                double bestDist = Double.MAX_VALUE;
                for (Enemy e : roomList) {
                    if (e.dead) continue;
                    double dist = e.entity.getLocation().distanceSquared(caster.getLocation());
                    if (dist < bestDist) { bestDist = dist; target = e; }
                }
                if (target != null) {
                    Location el = target.entity.getLocation();
                    org.bukkit.util.Vector away = el.toVector().subtract(caster.getLocation().toVector()).setY(0).normalize();
                    Location behind = el.clone().add(away.clone().multiply(-2));
                    behind.setY(el.getY());
                    behind.setYaw(caster.getLocation().getYaw());
                    behind.setPitch(caster.getLocation().getPitch());
                    caster.teleport(behind);
                    caster.sendMessage("§aShadow Stepped behind " + target.type.name + "!");
                } else if (boss != null && boss.isActive()) {
                    Location bl = boss.location();
                    org.bukkit.util.Vector away = bl.toVector().subtract(caster.getLocation().toVector()).setY(0).normalize();
                    Location behind = bl.clone().add(away.clone().multiply(-3));
                    behind.setY(bl.getY());
                    behind.setYaw(caster.getLocation().getYaw());
                    behind.setPitch(caster.getLocation().getPitch());
                    caster.teleport(behind);
                    caster.sendMessage("§aShadow Stepped behind the Warden!");
                } else {
                    // No enemies — short forward dash
                    caster.setVelocity(dir.clone().multiply(1.0).setY(0.3));
                    caster.sendMessage("§aShadow Step — no enemies nearby.");
                }
                // Guarantee next crit within 1.5 seconds
                st.guaranteedCritUntil = System.currentTimeMillis() + 1500;
                world.spawnParticle(org.bukkit.Particle.SMOKE, caster.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5);
                world.spawnParticle(org.bukkit.Particle.CRIT, caster.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
                world.playSound(caster.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                caster.sendMessage("§b§lSHADOW STEP!");
                break;
        }
    }

    private void dispatchAbility(String id, PlayerState st, Player caster) {
        Floor.RoomNode casterRoom = playerRoom.get(caster.getUniqueId());
        if (casterRoom == null) casterRoom = curRoom;
        long k = run.floor.key(casterRoom.x, casterRoom.z);
        List<Enemy> roomList = roomEnemies.getOrDefault(k, List.of());
        // Magic abilities use st.magicDamage instead of st.damage
        boolean isMagic = switch (id) {
            case "Arcane Bolt", "Chain Lightning", "Fireball", "Life Drain" -> true;
            default -> false;
        };
        double baseDmg = isMagic && st.magicDamage > 0 ? st.magicDamage : st.damage;
        double dmg = baseDmg * (Math.random() < st.critChance ? st.critMult : 1.0);
        org.bukkit.util.Vector dir = caster.getEyeLocation().getDirection().normalize();

        java.util.function.DoubleConsumer hitBoss = (radius) -> {
            if (boss != null && boss.isActive() && boss.location().distance(caster.getLocation()) < radius) {
                boss.damage(dmg, caster);
            }
        };

        switch (id) {
            case "Rush":
                caster.setVelocity(dir.clone().multiply(1.2).setY(0.4));
                st.invulnUntil = Math.max(st.invulnUntil, System.currentTimeMillis() + 600);
                caster.sendMessage("§6Rush!");
                break;
            case "Slash":
                // "a quick, heavy strike ahead" — single target
                hitBoss.accept(2.5);
                hitTargets(roomList, caster, 1, dmg * 2.0, dir.getX(), dir.getZ(),
                        e -> inCone(e, caster, dir, 2.5, 0.4));
                caster.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, caster.getEyeLocation().add(dir.clone().multiply(1.5)), 4, 0.5, 0, 0.5);
                caster.sendMessage("§6Slash!");
                break;
            case "Cleave":
                // "slash everything in a cone ahead" — up to a few in front
                hitBoss.accept(3.0);
                hitTargets(roomList, caster, 3, dmg * 1.5, dir.getX(), dir.getZ(),
                        e -> inCone(e, caster, dir, 3.0, 0.5));
                caster.sendMessage("§6Cleave!");
                break;
            case "Smash":
                // "blast all nearby enemies" — a few around you
                hitBoss.accept(4.0);
                hitTargets(roomList, caster, 3, dmg * 1.8, 0, 0,
                        e -> e.entity.getLocation().distance(caster.getLocation()) < 4);
                world.spawnParticle(org.bukkit.Particle.EXPLOSION, caster.getLocation().add(0, 1, 0), 1, 1, 0, 1);
                caster.sendMessage("§6Smash!");
                break;
            case "Blade Storm":
                // "spin, damaging around you" — hits more of the surrounding mobs
                hitBoss.accept(5.0);
                hitTargets(roomList, caster, 4, dmg * 1.2, 0, 0,
                        e -> e.entity.getLocation().distance(caster.getLocation()) < 5);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    caster.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                            caster.getLocation().clone().add(Math.cos(a) * 2, 1, Math.sin(a) * 2), 0);
                }
                caster.sendMessage("§6Blade Storm!");
                break;
            case "Arcane Bolt":
                // "mage strike in a line" — up to a few along the line
                hitBoss.accept(8.0);
                hitTargets(roomList, caster, 3, dmg * 2.2, dir.getX(), dir.getZ(),
                        e -> inCone(e, caster, dir, 8.0, 0.6));
                caster.getWorld().spawnParticle(org.bukkit.Particle.CRIT, caster.getEyeLocation().add(dir.clone().multiply(1.5)), 8, 0.2, 0.2, 0.2);
                caster.sendMessage("§6Arcane Bolt!");
                break;
            case "Ravage":
                // "devastate every enemy in the room" — truly every enemy
                hitBoss.accept(99.0);
                hitTargets(roomList, caster, Integer.MAX_VALUE, dmg * 1.5, dir.getX(), dir.getZ(), e -> true);
                caster.sendMessage("§6Ravage!");
                break;
            case "Chain Lightning": {
                // Single-enemy selection: raycast the caster's view direction, pick the nearest
                // non-dead enemy within range that falls in a narrow cone ahead.
                Enemy primary = null;
                double bestDist = Double.MAX_VALUE;
                for (Enemy e : roomList) {
                    if (e.dead) continue;
                    if (!inCone(e, caster, dir, 12.0, 0.7)) continue;
                    double dist = e.entity.getLocation().distanceSquared(caster.getLocation());
                    if (dist < bestDist) {
                        bestDist = dist;
                        primary = e;
                    }
                }
                // Also check boss as a valid primary target
                if (primary == null && boss != null && boss.isActive()) {
                    org.bukkit.util.Vector toBoss = boss.location().toVector().subtract(caster.getLocation().toVector());
                    toBoss.setY(0);
                    if (toBoss.length() < 12.0 && toBoss.clone().normalize().dot(dir) > 0.7) {
                        // Treat boss as primary — damage it directly
                        boss.damage(dmg * 2.0, caster);
                        // Draw the bolt from the caster's position to the boss
                        drawLightningArcLinger(world, caster.getLocation().clone().add(0, 1, 0),
                                boss.location().clone().add(0, 1, 0));
                        primary = null; // skip chain from boss
                    }
                }
                if (primary != null) {
                    // Primary target takes dmg * 2.0
                    final Enemy primaryTarget = primary; // effectively final for lambda
                    primary.damage(dmg * 2.0, caster, 0, 0);
                    // Draw the first bolt from the caster's position to the primary target, so the
                    // chain lightning visually originates from the player rather than appearing as a
                    // slash at the target cluster.
                    drawLightningArcLinger(world, caster.getLocation().clone().add(0, 1, 0),
                            primary.entity.getLocation().clone().add(0, 1, 0));
                    // Chain to up to 3 other nearest enemies (excluding primary)
                    double[] chainMults = {1.6, 1.2, 0.9};
                    List<Enemy> others = new java.util.ArrayList<>();
                    for (Enemy e : roomList) {
                        if (!e.dead && e != primary) others.add(e);
                    }
                    others.sort(java.util.Comparator.comparingDouble(
                            e -> e.entity.getLocation().distanceSquared(primaryTarget.entity.getLocation())));
                    int chainCount = Math.min(3, others.size());
                    for (int i = 0; i < chainCount; i++) {
                        Enemy target = others.get(i);
                        target.damage(dmg * chainMults[i], caster, 0, 0);
                        // Draw curved lightning arc from source to target
                        org.bukkit.Location src = (i == 0) ? primary.entity.getLocation() : others.get(i - 1).entity.getLocation();
                        org.bukkit.Location dst = target.entity.getLocation();
                        drawLightningArcLinger(world, src.clone().add(0, 1, 0), dst.clone().add(0, 1, 0));
                    }
                    world.playSound(caster.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.2f);
                    caster.sendMessage("§6Chain Lightning!");
                } else {
                    caster.sendMessage("§cNo target in range!");
                }
                break;
            }
            case "Fireball": {
                // Launch a real Fireball projectile from the caster toward their view direction
                org.bukkit.entity.Fireball fireball = caster.launchProjectile(org.bukkit.entity.Fireball.class, dir.clone().multiply(1.5));
                fireball.setYield(0f); // No block damage
                fireball.setIsIncendiary(false); // No fire
                fireball.addScoreboardTag("dung.fireball");
                fireball.setShooter(caster);
                // Store the damage value so the projectile hit handler can use it
                fireball.setMetadata("dung.damage", new org.bukkit.metadata.FixedMetadataValue(plugin, dmg * 2.0));
                world.playSound(caster.getLocation(), org.bukkit.Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
                caster.sendMessage("§6Fireball!");
                break;
            }
            case "Life Drain": {
                // Drain enemies within 8.5 blocks AND in line of sight (no more zero-aim full-room
                // nuke). Each enemy contributes 50% of its drain damage to stored health
                // independently (not summed).
                Location casterLoc = caster.getLocation().add(0, 1, 0);
                // Total health actually siphoned this cast (post-cap, so it never overstates).
                int totalSiphoned = 0;
                for (Enemy e : roomList) {
                    if (e.dead) continue;
                    // Range cap + line-of-sight gate: drain only what you can see and reach.
                    if (e.entity.getLocation().distance(caster.getLocation()) > 8.5) continue;
                    if (!caster.hasLineOfSight(e.entity)) continue;
                    double drainDmg = dmg * 0.5;
                    e.damage(drainDmg, caster, 0, 0);
                    int stored = (int) Math.round(drainDmg * 0.5);
                    if (stored > 0) {
                        ItemStack held = caster.getInventory().getItemInMainHand();
                        if (held != null && !held.getType().isAir()) {
                            int before = GearFactory.getStoredHealth(held);
                            GearFactory.setStoredHealth(held, before + stored);
                            totalSiphoned += GearFactory.getStoredHealth(held) - before;
                        }
                    }
                    // Spawn damage_indicator particles from each enemy to the caster
                    Location eLoc = e.entity.getLocation().add(0, 1, 0);
                    org.bukkit.util.Vector step = eLoc.toVector().subtract(casterLoc.toVector()).multiply(0.1);
                    for (int t = 0; t < 10; t++) {
                        Location pt = casterLoc.clone().add(step.clone().multiply(t));
                        world.spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, pt, 1, 0, 0, 0, 0);
                    }
                }
                // Also drain the boss (same 8.5-block range + line-of-sight rules)
                if (boss != null && boss.isActive()
                        && boss.location().distance(caster.getLocation()) <= 8.5
                        && caster.hasLineOfSight(boss.entity())) {
                    double bossDrain = dmg * 0.5;
                    boss.damage(bossDrain, caster);
                    int stored = (int) Math.round(bossDrain * 0.5);
                    if (stored > 0) {
                        ItemStack held = caster.getInventory().getItemInMainHand();
                        if (held != null && !held.getType().isAir()) {
                            int before = GearFactory.getStoredHealth(held);
                            GearFactory.setStoredHealth(held, before + stored);
                            totalSiphoned += GearFactory.getStoredHealth(held) - before;
                        }
                    }
                    Location bLoc = boss.location().add(0, 1, 0);
                    org.bukkit.util.Vector step = bLoc.toVector().subtract(casterLoc.toVector()).multiply(0.1);
                    for (int t = 0; t < 10; t++) {
                        Location pt = casterLoc.clone().add(step.clone().multiply(t));
                        world.spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, pt, 1, 0, 0, 0, 0);
                    }
                }
                world.spawnParticle(org.bukkit.Particle.WITCH, caster.getLocation().add(0, 1, 0), 20, 2, 1, 2, 0);
                world.playSound(caster.getLocation(), org.bukkit.Sound.ENTITY_WITCH_DRINK, 0.8f, 0.8f);
                ItemStack siphonHeld = caster.getInventory().getItemInMainHand();
                int newStored = siphonHeld != null && !siphonHeld.getType().isAir()
                        ? GearFactory.getStoredHealth(siphonHeld) : 0;
                int maxStored = siphonHeld != null && !siphonHeld.getType().isAir()
                        ? GearFactory.getStoredHealthMax(siphonHeld) : 0;
                caster.sendMessage("§6Life Drain! §7Siphoned §c" + totalSiphoned + "❤ §7→ Stored §c"
                        + newStored + "§7/§f" + maxStored + "§7❤");
                break;
            }
            default:
                hitTargets(roomList, caster, 3, dmg * 1.2, 0, 0,
                        e -> e.entity.getLocation().distance(caster.getLocation()) < 3.5);
                caster.sendMessage("§6Ability!");
                break;
        }
    }

    /** True if the enemy lies within a cone of the given radius opening from the caster. */
    private boolean inCone(Enemy e, Player caster, org.bukkit.util.Vector dir, double radius, double dotMin) {
        org.bukkit.util.Vector to = e.entity.getLocation().toVector().subtract(caster.getLocation().toVector());
        to.setY(0);
        return to.length() < radius && to.clone().normalize().dot(dir) > dotMin;
    }

    /** Damage all enemies from {@code roomList} matching {@code filter}, dividing the damage
     *  equally among them (nearest first for ordering). This replaces the old per-ability target
     *  limits — abilities now spread their damage across all valid targets instead of capping hits. */
    private void hitTargets(List<Enemy> roomList, Player caster, int limit, double dmg,
                            double kx, double kz, java.util.function.Predicate<Enemy> filter) {
        java.util.List<Enemy> cand = new java.util.ArrayList<>();
        for (Enemy e : roomList) if (!e.dead && filter.test(e)) cand.add(e);
        if (cand.isEmpty()) return;
        cand.sort(java.util.Comparator.comparingDouble(
                e -> e.entity.getLocation().distanceSquared(caster.getLocation())));
        double divided = dmg / cand.size();
        for (Enemy e : cand) e.damage(divided, caster, kx, kz);
    }

    /** In-room shop: open the chest GUI shop. Uses the player's individual room so that
     *  one party member in a SHOP room doesn't block another member from using it. */
    public void openShop(Player p) {
        if (!running) return;
        Floor.RoomNode room = playerRoom.get(p.getUniqueId());
        if (room == null || room.type != RoomType.SHOP) return;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) return;
        plugin.shopUI().openRunShop(p, this);
    }

    /** 1-based number of the floor the run is currently on (used to scale workstation costs). */
    public int currentFloorNumber() {
        return (run == null || run.floorIndex < 0) ? 1 : run.floorIndex + 1;
    }

    /** Open a workstation GUI (only valid inside an UPGRADE room, in the same run). */
    public void openWorkstation(Player p, WorkstationType type) {
        if (!running) return;
        Floor.RoomNode room = playerRoom.get(p.getUniqueId());
        if (room == null || room.type != RoomType.UPGRADE) return;
        if (run.playerStateOf(p.getUniqueId()) == null) return;
        plugin.workstationUI().openWorkstation(p, this, type);
    }

    /** Inventory slots holding workstation-eligible gear (weapon/armor/shield items that are
     *  not starter-kit gear). Includes both run gear and persistent gear. Used by UPGRADE / REFORGE /
     *  PRESERVE / SALVAGE. Equipped armor slots (36-39) are listed first so they appear in the first
     *  4 GUI slots. Hotbar slots (0-8) are listed last so they appear at the bottom of the GUI. */
    public List<Integer> workstationSlots(Player p) {
        List<Integer> out = new ArrayList<>();
        if (!running) return out;
        PlayerInventory inv = p.getInventory();
        // Armor slots first (36=boots, 37=leggings, 38=chestplate, 39=helmet)
        int[] armorSlots = {36, 37, 38, 39};
        for (int slot : armorSlots) {
            if (isWorkstationOrPersistentGear(inv.getItem(slot))) out.add(slot);
        }
        // Storage slots (9-35) — bag items before hotbar
        for (int slot = 9; slot < 36; slot++) {
            if (isWorkstationOrPersistentGear(inv.getItem(slot))) out.add(slot);
        }
        // Offhand (40)
        if (isWorkstationOrPersistentGear(inv.getItem(40))) out.add(40);
        // Hotbar slots last (0-8) so they appear at the bottom of the GUI
        for (int slot = 0; slot < 9; slot++) {
            if (isWorkstationOrPersistentGear(inv.getItem(slot))) out.add(slot);
        }
        return out;
    }

    /** Check if an item is eligible for workstation GUIs: must be gear (weapon/armor/shield),
     *  not starter-kit gear. Includes both run gear and persistent gear. */
    private static boolean isWorkstationOrPersistentGear(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        boolean gear = pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.GEAR),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (!gear) return false;
        if (pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.STARTER),
                org.bukkit.persistence.PersistentDataType.STRING)) return false;
        String kind = str(pdc, ItemTags.KIND);
        return "weapon".equals(kind) || "armor".equals(kind) || "shield".equals(kind);
    }

    private static String str(org.bukkit.persistence.PersistentDataContainer pdc, String key) {
        return pdc.get(org.bukkit.NamespacedKey.minecraft(key),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Inventory slots (incl. armor) holding persistent items, for the read-only STORAGE view.
     *  getSize() == 41 covers storage (0-35), armor (36-39) and offhand (40).
     *  Equipped armor slots (36-39) are listed first so they appear in the first 4 GUI slots.
     *  Hotbar slots (0-8) are listed last so they appear at the bottom of the GUI. */
    public List<Integer> persistentSlots(Player p) {
        List<Integer> out = new ArrayList<>();
        if (!running) return out;
        PlayerInventory inv = p.getInventory();
        // Armor slots first (36=boots, 37=leggings, 38=chestplate, 39=helmet)
        int[] armorSlots = {36, 37, 38, 39};
        for (int slot : armorSlots) {
            if (isPersistentGear(inv.getItem(slot))) out.add(slot);
        }
        // Storage slots (9-35) — bag items before hotbar
        for (int slot = 9; slot < 36; slot++) {
            if (isPersistentGear(inv.getItem(slot))) out.add(slot);
        }
        // Offhand (40)
        if (isPersistentGear(inv.getItem(40))) out.add(40);
        // Hotbar slots last (0-8) so they appear at the bottom of the GUI
        for (int slot = 0; slot < 9; slot++) {
            if (isPersistentGear(inv.getItem(slot))) out.add(slot);
        }
        return out;
    }

    /**
     * UPGRADE: raise a workstation-eligible item's upgrade level, boosting its core stat. Costs run
     * coins + shards (scaled by current level). Atomic: validates the item is still in the slot,
     * charges the currencies, then applies the upgrade in the same inventory write.
     */
    public boolean tryUpgrade(Player p, int slot) {
        if (!running) return false;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) return false;
        UUID pid = p.getUniqueId();
        MetaManager.MetaProfile prof = plugin.meta().profile(pid);

        ItemStack item = p.getInventory().getItem(slot);
        if (item == null || !isWorkstationOrPersistentGear(item)) {
            p.sendMessage("§cThat item can't be upgraded.");
            return false;
        }
        int level = GearFactory.getUpgradeLevel(item);
        if (!WorkstationRules.canUpgrade(level)) {
            p.sendMessage("§5This item is already at max upgrade level.");
            return false;
        }
        boolean isPersistent = GearFactory.isPersistent(item);
        int floor = currentFloorNumber();
        int coinCost = WorkstationRules.scaledCost(WorkstationRules.upgradeCoinCost(level), floor);
        int shardCost = WorkstationRules.scaledCost(WorkstationRules.upgradeShardCost(level), floor);
        if (isPersistent) {
            coinCost *= 2;
            shardCost *= 2;
        }
        if (st.coins < coinCost) {
            p.sendMessage("§cYou need §e" + coinCost + " run coins§c (have §e" + st.coins + "§c).");
            return false;
        }
        if (prof.shards < shardCost) {
            p.sendMessage("§cYou need §3" + shardCost + " shards§c (have §b" + prof.shards + "§c).");
            return false;
        }
        st.coins -= coinCost;
        prof.shards -= shardCost;
        plugin.meta().save();
        GearFactory.setUpgradeLevel(item, level + 1);
        recomputeStats(); // the equipped item's tags changed in place — refresh live combat stats
        world.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_SMITHING_TABLE_USE, 1.0f, 1.2f);
        String persistNote = isPersistent ? " §7(§6persistent§7, 2x cost)" : "";
        p.sendMessage("§aUpgraded to §5Lv " + (level + 1) + "§a! §7(-§e" + coinCost + " coins§7, §3-" + shardCost + " shards§7)" + persistNote);
        return true;
    }

    /** REFORGE: reroll an item's affix set for shards. Keeps base stats, rarity, ability, and upgrade
     *  level; only the affixes change. Returns a preview of the new affixes without applying them. */
    public List<Affix.AffixRoll> previewReforge(ItemStack item) {
        return Affix.roll(GearFactory.getRarity(item), kindOf(item), new java.util.Random());
    }

    public boolean tryReforge(Player p, int slot) {
        if (!running) return false;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) return false;
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());

        ItemStack item = p.getInventory().getItem(slot);
        if (item == null || !isWorkstationOrPersistentGear(item)) {
            p.sendMessage("§cThat item can't be reforged.");
            return false;
        }
        boolean isPersistent = GearFactory.isPersistent(item);
        int floor = currentFloorNumber();
        int reforgeCount = GearFactory.getReforgeCount(item);
        int shardCost = WorkstationRules.scaledCost(WorkstationRules.reforgeShardCost(reforgeCount), floor);
        if (isPersistent) shardCost *= 2;
        if (prof.shards < shardCost) {
            p.sendMessage("§cYou need §3" + shardCost + " shards§c (have §b" + prof.shards + "§c).");
            return false;
        }
        prof.shards -= shardCost;
        plugin.meta().save();
        List<Affix.AffixRoll> rolled = Affix.roll(GearFactory.getRarity(item), kindOf(item), new java.util.Random());
        GearFactory.setReforgeCount(item, reforgeCount + 1);
        GearFactory.reforge(item, rolled, GearFactory.getUpgradeLevel(item));
        recomputeStats(); // equipped item's affixes changed in place — refresh live combat stats
        world.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);
        String persistNote = isPersistent ? " §7(§6persistent§7, 2x cost)" : "";
        p.sendMessage("§bReforged! §7New affixes: "
                + affixSummary(rolled) + " §7(-§3" + shardCost + " shards§7)" + persistNote);
        return true;
    }

    private static String kindOf(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return null;
        return s.getItemMeta().getPersistentDataContainer().get(
                org.bukkit.NamespacedKey.minecraft(ItemTags.KIND),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    private static String affixSummary(List<Affix.AffixRoll> rolls) {
        if (rolls.isEmpty()) return "§8(none)";
        StringBuilder sb = new StringBuilder();
        for (Affix.AffixRoll r : rolls) {
            if (sb.length() > 0) sb.append("§7, ");
            sb.append(r.affix().stat.color).append("+").append(r.value());
        }
        return sb.toString();
    }

    /** PRESERVE: gamble to make a run item persistent. Paying run coins + persistent coins + shards
     *  (all AND), a preserve attempt has a {@code PRESERVE_SUCCESS_CHANCE} chance to queue the item
     *  for post-run delivery as persistent half-durability gear (via the existing pendingPersists
     *  delivery path); on failure the item is returned immediately, one rarity worse. */
    public boolean tryPreserve(Player p, int slot) {
        if (!running) return false;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) return false;
        UUID pid = p.getUniqueId();
        MetaManager.MetaProfile prof = plugin.meta().profile(pid);

        ItemStack item = p.getInventory().getItem(slot);
        if (item == null || !WorkstationRules.isWorkstationGear(item)) {
            p.sendMessage("§cThat item can't be preserved.");
            return false;
        }
        int coinCost = WorkstationRules.PRESERVE_COIN_COST;
        int pcCost = WorkstationRules.PRESERVE_PERSISTENT_COIN_COST;
        int shardCost = WorkstationRules.PRESERVE_SHARD_COST;
        if (st.coins < coinCost) {
            p.sendMessage("§cYou need §e" + coinCost + " run coins§c (have §e" + st.coins + "§c).");
            return false;
        }
        if (prof.persistentCoins < pcCost) {
            p.sendMessage("§dYou need §b" + pcCost + " persistent coins§d (have §b"
                    + prof.persistentCoins + "§d).");
            return false;
        }
        if (prof.shards < shardCost) {
            p.sendMessage("§cYou need §3" + shardCost + " shards§c (have §b" + prof.shards + "§c).");
            return false;
        }
        // Charge ALL THREE currencies (run coins + persistent coins + shards), not either/or.
        st.coins -= coinCost;
        prof.persistentCoins -= pcCost;
        prof.shards -= shardCost;
        plugin.meta().save();

        int fails = preserveFails.getOrDefault(pid, 0);
        boolean guaranteed = WorkstationRules.preserveGuaranteed(fails);
        boolean success = guaranteed || WorkstationRules.preserveSucceeds(new java.util.Random().nextDouble());
        if (success) {
            preserveFails.remove(pid); // reset on success
            p.getInventory().setItem(slot, null);
            pendingPersists.computeIfAbsent(pid, k -> new ArrayList<>()).add(GearFactory.persistize(item));
            world.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
            String pityMsg = guaranteed ? " §6§l✦ PITY! §7Guaranteed after " + WorkstationRules.PRESERVE_PITY + " fails!" : "";
            p.sendMessage("§d§l✦ PRESERVED! §dYour item will persist past this run (at half durability)." + pityMsg);
            p.sendMessage("§7  You'll receive it when the run ends.");
        } else {
            // Failed: increment consecutive fails for pity tracking
            preserveFails.put(pid, fails + 1);
            // Return the item, one rarity worse (stats scaled down, recolored).
            ItemStack downgraded = GearFactory.downgradeRarity(item);
            p.getInventory().setItem(slot, downgraded);
            world.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            int remaining = WorkstationRules.PRESERVE_PITY - (fails + 1);
            p.sendMessage("§cThe preserve failed. Your item was returned, §lone rarity worse§r§c."
                    + " §7(Pity: §e" + remaining + "§7 more fail" + (remaining == 1 ? "" : "s") + " → guaranteed)");
        }
        recomputeStats(); // equipped slot changed (removed or downgraded) — refresh live combat stats
        return true;
    }

    /** SALVAGE: destroy a workstation-eligible item for run coins (a per-run currency, lost on death).
     *  These coins do NOT count toward the boss persistent coin reward. Costs nothing. Caller is
     *  responsible for a confirmation step. Returns the coin value, or 0 if rejected. */
    public int trySalvage(Player p, int slot) {
        if (!running) return 0;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null) return 0;
        ItemStack item = p.getInventory().getItem(slot);
        if (item == null || !WorkstationRules.isWorkstationGear(item)) {
            p.sendMessage("§cThat item can't be salvaged.");
            return 0;
        }
        Rarity r = GearFactory.getRarity(item);
        int value = WorkstationRules.salvageValue(r, WorkstationRules.primaryStat(item));
        p.getInventory().setItem(slot, null);
        recomputeStats(); // item removed from (possibly) equipped slot — refresh live combat stats
        st.coins += value;
        world.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
        p.sendMessage("§aSalvaged the item §e→ +" + value + " run coins§7 (total §e" + st.coins + "§7).");
        return value;
    }

    private void onRoomClear(Floor.RoomNode n, long k) {
        if (n.cleared) return; // prevent double-processing
        n.cleared = true;
        roomLocked.put(k, false);
        openDoors(n);
        int coins = 2 + run.floorIndex;
        // Give coins to all party members and heal them by 15% HP
        for (Player p : party.onlineMembers()) {
            PlayerState st = run.playerStateOf(p.getUniqueId());
            if (st != null) {
                st.coins += coins;
                run.runCoinsEarned += coins;
                st.heal(st.maxHearts * 0.15);
                p.sendMessage("§aRoom cleared! §7(+§e" + coins + " coins§7)");
            }
        }
        List<ItemStack> loot = ItemPool.roomReward(run.floorIndex, n.type.kind);
        Location center = RoomGen.center(world, n, BASE_Y, spacing, offsetX, offsetZ);
        for (int i = 0; i < loot.size(); i++) {
            spawnPedestal(center.clone().add((i - (loot.size() - 1) / 2.0) * 2, 0, 0), loot.get(i));
        }
    }

    private void openDoors(Floor.RoomNode n) {
        sealDoors(n, false);
        Location c = RoomGen.center(world, n, BASE_Y, spacing, offsetX, offsetZ);
        world.playSound(c, org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.2f);
        world.spawnParticle(org.bukkit.Particle.CRIT, c.clone().add(0, 1.5, 0), 12, 8, 1.5, 8);
        for (Player p : party.onlineMembers()) {
            setStatus(p, "§aDoors opened!");
        }
    }

    // ---------- boss ----------

    public void onRoomEnterBossCheck() {
        if (curRoom != null && curRoom.type == RoomType.BOSS && !curRoom.cleared && boss == null) {
            if (!allMembersInRoom(curRoom)) {
                for (Player p : party.onlineMembers()) {
                    setStatus(p, "§cThe Warden awaits until everyone is inside.");
                }
                return;
            }
            Player leader = Bukkit.getPlayer(party.leader());
            if (leader == null) leader = party.onlineMembers().stream().findFirst().orElse(null);
            if (leader == null) return;
            // Scale boss HP by party size
            int partySize = Math.max(1, party.onlineMembers().size());
            bossRoom = curRoom;
            boss = new BossController(world, roomSpawn(curRoom),
                    run.floorIndex, leader, plugin, partySize, this::onBossDefeated);
            // Add all party members as boss bar viewers
            for (Player p : party.onlineMembers()) {
                if (!p.equals(leader)) boss.addViewer(p);
            }
            lockDoors(curRoom);
            for (Player p : party.onlineMembers()) {
                p.sendMessage("§4The Warden of Floor " + (run.floorIndex + 1) + " awakens!");
            }
        }
    }

    public void onBossDefeated() {
        Floor.RoomNode defeated = bossRoom != null ? bossRoom : curRoom;
        boss = null;
        bossRoom = null;
        defeated.cleared = true;
        openDoors(defeated);
        for (Player p : party.onlineMembers()) {
            p.sendMessage("§6Boss slain!");
        }
        int coins = 8 + run.floorIndex * 4;
        for (Player p : party.onlineMembers()) {
            PlayerState st = run.playerStateOf(p.getUniqueId());
            if (st != null) {
                st.coins += coins;
                run.runCoinsEarned += coins;
            }
        }
        dropGear(roomSpawn(defeated), 2, 6);
        // Bank coins for each player — calculate once, distribute to all
        int earned = run.runCoinsEarned - run.bankedCoins;
        int bank = Math.min(40, Math.max(0, earned)) / 2;
        run.bankedCoins += bank;
        for (Player p : party.onlineMembers()) {
            MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
            prof.clears++;
            prof.bestFloor = Math.max(prof.bestFloor, run.floorIndex + 1);
            plugin.meta().addPersistentCoins(p.getUniqueId(), bank);
            p.sendMessage("§dYou banked §6" + bank + "§d coins into your persistent coins.");
        }
        // Bank each player's salvage shards earned this floor into their persistent balance
        for (Player p : party.onlineMembers()) {
            Integer shardsEarned = run.salvageShards.remove(p.getUniqueId());
            if (shardsEarned != null && shardsEarned > 0) {
                MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
                prof.shards += shardsEarned;
                p.sendMessage("§dYou banked §b" + shardsEarned + "§d shards from salvaged gear.");
            }
        }
        plugin.meta().save();
        // Revive all dead party members BEFORE showing the descend button so revived players
        // can see the button and click it. They become spectators on death and get restored
        // when the boss is defeated so they can continue with the party.
        reviveDeadPlayers();
        // Show a clickable descend button. In a party, clicking starts a vote where >50% must agree.
        for (Player p : party.onlineMembers()) {
            var legacy = LegacyComponentSerializer.legacySection();
            net.kyori.adventure.text.Component msg = legacy.deserialize("§dA crack opens below... ");
            net.kyori.adventure.text.Component btn = net.kyori.adventure.text.Component.text("[Descend]", net.kyori.adventure.text.format.NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.BOLD)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Click to descend to the next floor", net.kyori.adventure.text.format.NamedTextColor.GRAY)))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/dung descend"));
            net.kyori.adventure.text.Component endBtn = net.kyori.adventure.text.Component.text(" [End Run]", net.kyori.adventure.text.format.NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Leave the run and return to the hub", net.kyori.adventure.text.format.NamedTextColor.GRAY)))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/dung leave"));
            p.sendMessage(msg.append(btn).append(endBtn));
        }
    }

    /** Revive all dead party members: restore them to SURVIVAL mode, heal them, re-create
     *  their HUD/TabUI/scoreboard (which were removed on death), restore their persistent gear
     *  (which was stripped on death), and teleport them to the boss room so they can continue
     *  with the party. */
    private void reviveDeadPlayers() {
        for (Player p : party.onlineMembers()) {
            UUID pid = p.getUniqueId();
            if (!deadPlayers.contains(pid)) continue;
            deadPlayers.remove(pid);
            // Reset PlayerState.dead so the tick loop doesn't re-trigger onPlayerDeath
            PlayerState st = run.playerStateOf(pid);
            if (st != null) st.dead = false;
            p.setGameMode(org.bukkit.GameMode.SURVIVAL);
            p.setHealth(20);
            p.setFoodLevel(20);
            // Restore persistent gear from the pre-run snapshot — stripRunGear removed it on death
            // along with non-persistent run gear, but persistent gear should come back on revive.
            restorePersistentGear(p);
            // Re-create HUD, TabUI, and scoreboard that were removed in onPlayerDeath
            if (!huds.containsKey(pid)) {
                org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
                playerBoards.put(pid, sb);
                p.setScoreboard(sb);
                HUD hud = new HUD();
                hud.reset(p, sb);
                huds.put(pid, hud);
                TabUI tab = new TabUI();
                tab.reset(sb);
                tabs.put(pid, tab);
            }
            // Teleport to the boss room center
            if (bossRoom != null) {
                Location spawn = roomSpawn(bossRoom);
                p.teleport(spawn);
            } else if (curRoom != null) {
                Location spawn = roomSpawn(curRoom);
                p.teleport(spawn);
            }
            p.sendMessage("§a§lYou have been revived by the boss's defeat!");
        }
    }

    /** Never re-insert persistent gear from the pre-run snapshot on revive. stripRunGear only removes
     *  non-persistent run gear, so every persistent item the player still owns is still in their
     *  inventory on death (carrying its mid-run durability / death penalty). Re-inserting an owned
     *  UUID'd item would duplicate the same UUID (undamaged snapshot copy + the damaged current copy)
     *  and undo the death durability penalty; re-inserting an un-owned UUID'd item would resurrect a
     *  piece the player deliberately destroyed during the run (salvaged or dropped). Only pre-UUID
     *  legacy items (uuid == null) are re-inserted, as a data-loss safety net. */
    private void restorePersistentGear(Player p) {
        ItemStack[] snapshot = savedInventories.get(p.getUniqueId());
        if (snapshot == null) return;
        PlayerInventory inv = p.getInventory();
        // Restore persistent gear into main inventory slots
        for (int i = 0; i < inv.getSize(); i++) {
            if (i < snapshot.length && snapshot[i] != null && isPersistentGear(snapshot[i])) {
                String uuid = GearFactory.getUuid(snapshot[i]);
                if (uuid != null) continue;
                // Only set if the slot is currently empty — don't overwrite existing items
                ItemStack cur = inv.getItem(i);
                if (cur == null || cur.getType() == Material.AIR) {
                    inv.setItem(i, snapshot[i].clone());
                }
            }
        }
        // Restore persistent gear into armor slots
        org.bukkit.inventory.EquipmentSlot[] slots = {
                org.bukkit.inventory.EquipmentSlot.FEET,
                org.bukkit.inventory.EquipmentSlot.LEGS,
                org.bukkit.inventory.EquipmentSlot.CHEST,
                org.bukkit.inventory.EquipmentSlot.HEAD
        };
        for (int i = 0; i < 4; i++) {
            int idx = inv.getSize() + i;
            if (idx < snapshot.length && snapshot[idx] != null && isPersistentGear(snapshot[idx])) {
                String uuid = GearFactory.getUuid(snapshot[idx]);
                if (uuid != null) continue;
                ItemStack cur = inv.getItem(slots[i]);
                if (cur == null || cur.getType() == Material.AIR) {
                    inv.setItem(slots[i], snapshot[idx].clone());
                }
            }
        }
        // Restore persistent gear into offhand
        int offIdx = inv.getSize() + 4;
        if (offIdx < snapshot.length && snapshot[offIdx] != null && isPersistentGear(snapshot[offIdx])) {
            String uuid = GearFactory.getUuid(snapshot[offIdx]);
            if (uuid == null) {
                ItemStack cur = inv.getItemInOffHand();
                if (cur == null || cur.getType() == Material.AIR) {
                    inv.setItemInOffHand(snapshot[offIdx].clone());
                }
            }
        }
    }

    // Tracks descend votes: player UUID -> voted yes
    private final Set<UUID> descendVotes = new HashSet<>();

    /** Called when a player uses /dung descend (or clicks the descend button).
     *  Solo players descend immediately. In a party, each member must vote and >50% majority
     *  is required. Dead (spectator) players are excluded from the vote count — they cannot
     *  vote and don't block the majority. Votes are tracked per floor — they reset when a new
     *  floor is entered. */
    public void descend(Player caller) {
        Floor.RoomNode bossNode = run != null && run.floor != null ? run.floor.boss : null;
        if (bossNode == null || !bossNode.cleared) {
            caller.sendMessage("§cDefeat the boss first!");
            return;
        }
        List<Player> online = party.onlineMembers();
        // Count only alive (non-spectator) players for the vote — dead players can't vote
        List<Player> alive = new java.util.ArrayList<>();
        for (Player p : online) {
            if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR) alive.add(p);
        }
        if (alive.size() <= 1) {
            // Solo or empty party — descend immediately
            // Revive any dead players before descending so they come along
            reviveDeadPlayers();
            enterFloor(run.floorIndex + 1);
            return;
        }
        // Party: record this player's vote
        if (!descendVotes.add(caller.getUniqueId())) {
            caller.sendMessage("§7You already voted to descend.");
            return;
        }
        // Count votes among alive players only
        int yes = descendVotes.size();
        int total = alive.size();
        int needed = total / 2 + 1; // >50% majority
        if (yes >= needed) {
            // Majority reached — descend!
            for (Player p : online) {
                p.sendMessage("§a§lDescend vote passed! (§e" + yes + "/" + needed + "§a)");
            }
            descendVotes.clear();
            // Revive any dead players before descending so they come along
            reviveDeadPlayers();
            enterFloor(run.floorIndex + 1);
        } else {
            // Not enough votes yet
            for (Player p : online) {
                p.sendMessage("§e" + caller.getName() + "§7 voted to descend (§e" + yes + "/" + needed + "§7 needed)");
            }
        }
    }

    // ---------- pedestal system ----------

    /**
     * Place a pedestal (slab) with an item frame displaying the given item.
     * The item frame is invulnerable and cannot be broken by players.
     */
    public ItemFrame spawnPedestal(Location loc, ItemStack item) {
        // Finalize armor trims here (world drops are openly displayed — no rarity to hide).
        GearFactory.finalizeRarityLook(item);
        Location blockLoc = new Location(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        // Place the slab pedestal
        world.getBlockAt(blockLoc).setType(Material.POLISHED_BLACKSTONE_SLAB);
        // Spawn item frame on top of the slab
        Location frameLoc = blockLoc.clone().add(0.5, 1, 0.5);
        ItemFrame frame = world.spawn(frameLoc, ItemFrame.class);
        // The floating armor-stand name tag already shows the item's name, so blank the
        // frame's display name (on a clone) to suppress the vanilla "looking at item
        // frame" tooltip that would otherwise duplicate it. The real item keeps its name.
        ItemStack display = item.clone();
        display.editMeta(meta -> meta.setDisplayName(" "));
        frame.setItem(display);
        frame.setInvulnerable(true);
        frame.setVisible(false);
        // Floating name tag: a small, invisible, non-interactive armor stand hovering above the item
        String itemName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : com.lieyabull.dung.util.TextUtil.capital(item.getType().name().toLowerCase().replace('_', ' '));
        org.bukkit.entity.ArmorStand stand = world.spawn(
                blockLoc.clone().add(0.5, 1.8, 0.5), org.bukkit.entity.ArmorStand.class);
        stand.setCustomName(itemName);
        stand.setCustomNameVisible(true);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setGravity(false);
        // Track the pedestal
        pedestals.add(blockLoc);
        pedestalItems.put(blockLoc, item);
        return frame;
    }

    /** Claim a pedestal: give the item to the player and remove the pedestal. */
    public boolean claimPedestal(Player p, Location blockLoc) {
        Location key = new Location(world, blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        ItemStack item = pedestalItems.remove(key);
        if (item == null) return false;
        pedestals.remove(key);
        // Remove item frame and armor stand entities at this location
        world.getNearbyEntities(key.clone().add(0.5, 1, 0.5), 0.5, 1.0, 0.5).stream()
                .filter(e -> e instanceof ItemFrame || e instanceof org.bukkit.entity.ArmorStand)
                .forEach(e -> e.remove());
        // Remove the slab block
        world.getBlockAt(key).setType(Material.AIR);
        // Give the item to the player
        p.getInventory().addItem(item).values().forEach(drop ->
                world.dropItem(p.getLocation(), drop));
        // Play effects
        world.playSound(key, org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        world.spawnParticle(org.bukkit.Particle.ENCHANTED_HIT, key.clone().add(0.5, 1.2, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
        return true;
    }

    /** True if the given block location is a tracked pedestal (slab + item frame). */
    public boolean isPedestal(Location blockLoc) {
        return pedestals.contains(new Location(world, blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ()));
    }

    /** True if the item is an armor piece (helmet, chestplate, leggings, boots). */
    private static boolean isArmorPiece(ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return false;
        String n = s.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    /** Remove any free starter-kit armor piece that has been displaced out of its armor slot (i.e. the
     *  player swapped in different armor). Starter armor is disposable: once replaced it is deleted
     *  rather than left to clutter the inventory. Only main-storage/hotbar slots are scanned (armor
     *  occupies slots 36-39), so equipped starter armor is untouched. */
    public void removeDisplacedStarterArmor(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && GearFactory.isStarter(s) && isArmorPiece(s)) {
                inv.setItem(i, null);
            }
        }
    }

    /** Remove all pedestal item frames, armor stands, and blocks. */
    private void clearPedestals() {
        for (Location loc : pedestals) {
            // Remove item frames and armor stands
            world.getNearbyEntities(loc.clone().add(0.5, 1, 0.5), 0.5, 1.0, 0.5).stream()
                    .filter(e -> e instanceof ItemFrame || e instanceof org.bukkit.entity.ArmorStand)
                    .forEach(e -> e.remove());
            // Remove the slab block
            world.getBlockAt(loc).setType(Material.AIR);
        }
        pedestals.clear();
        pedestalItems.clear();
    }

    // ---------- bomb-through-wall secrets ----------

    /**
     * Carve a short passage from the combat room's wall toward the secret room,
     * and place the destructible wall (CRACKED_STONE_BRICKS) at the secret room's
     * outer wall face. This creates a visible cracked wall segment that the player
     * can see from inside the combat room.
     */
    private void carveSecretPassage(Floor.RoomNode secret) {
        Location wallLoc = secret.destructibleWallLoc;
        Floor.RoomNode parent = secret.secretParent;
        if (wallLoc == null || parent == null) return;
        int wDir = secret.secretWallDir;
        boolean horiz = wDir == 1 || wDir == 3;
        // secret -> parent along-axis direction (+1 or -1 in grid units)
        int asg = horiz ? Integer.signum(parent.x - secret.x) : Integer.signum(parent.z - secret.z);
        int sbx = baseX(secret), sbz = baseZ(secret);
        int axC = horiz ? (sbx + RoomGen.WALL + secret.sizeW / 2) : (sbz + RoomGen.WALL + secret.sizeH / 2);
        int perpC = horiz ? (sbz + RoomGen.PERP_CENTER) : (sbx + RoomGen.PERP_CENTER);
        // wall faces along the corridor axis (same scheme as normal door carving)
        int sWallT = RoomGen.WALL + (horiz ? secret.sizeW / 2 : secret.sizeH / 2);
        int pWallT = spacing - (RoomGen.WALL + (horiz ? parent.sizeW / 2 : parent.sizeH / 2));
        if (pWallT <= sWallT) pWallT = sWallT + 1;
        // Build a proper walled corridor between the secret's wall face and the parent's wall face
        // (3-wide tunnel with solid side walls), instead of punching a bare tunnel through every
        // block for `spacing` tiles (which carved through floors and unrelated walls).
        int COW = RoomGen.LONG / 2;
        for (int t = sWallT; t <= pWallT; t++) {
            for (int off = -COW; off <= COW; off++) {
                boolean passage = Math.abs(off) <= 1;
                int ax = axC + asg * t;
                int px = horiz ? ax : (perpC + off);
                int pz = horiz ? (perpC + off) : ax;
                for (int y = BASE_Y; y <= BASE_Y + RoomGen.ROOM_HEIGHT + 1; y++) {
                    if (y == BASE_Y) {
                        world.getBlockAt(px, y, pz).setType(passage ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                    } else if (y == BASE_Y + RoomGen.ROOM_HEIGHT + 1) {
                        world.getBlockAt(px, y, pz).setType(Material.STONE_BRICKS);
                    } else {
                        world.getBlockAt(px, y, pz).setType(passage ? Material.AIR : Material.STONE_BRICKS);
                    }
                }
            }
        }
        // Place the destructible wall (CRACKED_STONE_BRICKS) sealing the secret's end of the corridor
        int cx = wallLoc.getBlockX(), cz = wallLoc.getBlockZ();
        for (int off = -1; off <= 1; off++) {
            for (int y = BASE_Y + 1; y <= BASE_Y + RoomGen.ROOM_HEIGHT; y++) {
                int px = horiz ? cx : (cx + off);
                int pz = horiz ? (cz + off) : cz;
                world.getBlockAt(px, y, pz).setType(Material.CRACKED_STONE_BRICKS);
            }
        }
    }

    /**
     * Register the destructible wall blocks for a SECRET room so they can be
     * looked up when a player right-clicks them.
     */
    private void registerDestructibleWall(Floor.RoomNode secret) {
        Location center = secret.destructibleWallLoc;
        int wallDir = secret.secretWallDir;
        boolean horiz = wallDir == 1 || wallDir == 3;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        // Register a 3-wide, 4-tall area of blocks
        for (int off = -1; off <= 1; off++) {
            for (int y = -2; y <= 1; y++) {
                int px = horiz ? cx : (cx + off);
                int pz = horiz ? (cz + off) : cz;
                Location bl = new Location(world, px, cy + y, pz);
                destructibleWalls.add(bl);
            }
        }
    }

    /**
     * Check if a block location is a registered destructible wall.
     */
    public boolean isDestructibleWall(Location loc) {
        Location key = new Location(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return destructibleWalls.contains(key);
    }

    /**
     * Attempt to unlock a locked room by right-clicking its IRON_BLOCK door barrier
     * with a key item in hand. Consumes 1 key from the player's inventory.
     */
    public void tryUnlockRoom(Player p, Location blockLocation) {
        if (!running || run == null || run.floor == null) return;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null || st.keys <= 0) {
            p.sendMessage("§cYou need a key to unlock this door!");
            return;
        }
        // Find which LOCKED room this barrier belongs to
        Floor.RoomNode lockedRoom = null;
        for (Floor.RoomNode n : run.floor.rooms()) {
            if (n.type == RoomType.LOCKED && !n.cleared) {
                // Check if the clicked block is within this room's door barrier area
                int[] DX = {0, 1, 0, -1};
                int[] DZ = {-1, 0, 1, 0};
                Location c = RoomGen.center(world, n, BASE_Y, spacing, offsetX, offsetZ);
                int bx = baseX(n), bz = baseZ(n);
                for (int d = 0; d < 4; d++) {
                    if (!n.doors[d]) continue;
                    boolean horiz = d == 1 || d == 3;
                    int half = horiz ? n.sizeW / 2 : n.sizeH / 2;
                    int wallX = c.getBlockX() + DX[d] * (half + RoomGen.WALL);
                    int wallZ = c.getBlockZ() + DZ[d] * (half + RoomGen.WALL);
                    int perpC = horiz ? (bz + RoomGen.PERP_CENTER) : (bx + RoomGen.PERP_CENTER);
                    for (int off = -1; off <= 1; off++) {
                        for (int y = BASE_Y + 1; y <= BASE_Y + RoomGen.ROOM_HEIGHT; y++) {
                            int px = horiz ? wallX : (perpC + off);
                            int pz = horiz ? (perpC + off) : wallZ;
                            if (blockLocation.getBlockX() == px && blockLocation.getBlockY() == y && blockLocation.getBlockZ() == pz) {
                                lockedRoom = n;
                                break;
                            }
                        }
                        if (lockedRoom != null) break;
                    }
                    if (lockedRoom != null) break;
                }
            }
            if (lockedRoom != null) break;
        }
        if (lockedRoom == null) return;

        Location doorLoc = RoomGen.center(world, lockedRoom, BASE_Y, spacing, offsetX, offsetZ);

        // Consume 1 key
        st.keys--;

        // Failure chance increases with floor depth, capped at 80%
        if (failureRoll()) {
            p.sendMessage("§cThe key snaps in the lock! §7(-1 key)");
            world.playSound(doorLoc, org.bukkit.Sound.BLOCK_CHEST_LOCKED, 1.0f, 0.5f);
            world.spawnParticle(org.bukkit.Particle.SMOKE, doorLoc.clone().add(0, 1.5, 0), 10, 0.3, 0.3, 0.3, 0.02);
            return;
        }
        p.sendMessage("§aYou unlock the door! §7(§e" + st.keys + " keys remaining§7)");

        // Play unlock effects
        world.playSound(doorLoc, org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        world.playSound(doorLoc, org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.2f);
        world.spawnParticle(org.bukkit.Particle.PORTAL, doorLoc.clone().add(0, 1.5, 0), 30, 1.5, 1.5, 8, 0.3);

        // Remove the IRON_BLOCK door barrier, bursting END_ROD particles at the freed blocks
        // so the opening is clearly visible where the door used to be.
        removeLockedDoorBarrier(lockedRoom);
        Location c = RoomGen.center(world, lockedRoom, BASE_Y, spacing, offsetX, offsetZ);
        int bx = baseX(lockedRoom), bz = baseZ(lockedRoom);
        for (int d = 0; d < 4; d++) {
            if (!lockedRoom.doors[d]) continue;
            boolean horiz = d == 1 || d == 3;
            int half = horiz ? lockedRoom.sizeW / 2 : lockedRoom.sizeH / 2;
            int wallX = c.getBlockX() + new int[]{0, 1, 0, -1}[d] * (half + RoomGen.WALL);
            int wallZ = c.getBlockZ() + new int[]{-1, 0, 1, 0}[d] * (half + RoomGen.WALL);
            int perpC = horiz ? (bz + RoomGen.PERP_CENTER) : (bx + RoomGen.PERP_CENTER);
            for (int off = -1; off <= 1; off++) {
                int px = horiz ? wallX : (perpC + off);
                int pz = horiz ? (perpC + off) : wallZ;
                world.spawnParticle(org.bukkit.Particle.END_ROD,
                        new org.bukkit.Location(world, px + 0.5, BASE_Y + RoomGen.ROOM_HEIGHT / 2.0 + 1, pz + 0.5),
                        6, 0.5, 1.5, 0.5, 0.01);
            }
        }

        // Mark the room as cleared so it doesn't re-lock, and spawn loot
        lockedRoom.cleared = true;
        spawnRoomPickups(lockedRoom);
    }

    /**
     * Attempt to bomb a destructible wall. Called when a player right-clicks a
     * CRACKED_STONE_BRICKS block that is registered as a destructible wall.
     */
    public void tryBombWall(Player p, Location blockLocation) {
        if (!running || run == null || run.floor == null) return;
        PlayerState st = run.playerStateOf(p.getUniqueId());
        if (st == null || st.bombs <= 0) {
            p.sendMessage("§cYou need a bomb to destroy this wall!");
            return;
        }

        Location key = new Location(world, blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ());
        if (!destructibleWalls.contains(key)) return;

        // Find which SECRET room this wall belongs to
        Floor.RoomNode secretRoom = null;
        for (Floor.RoomNode n : run.floor.rooms()) {
            if (n.type == RoomType.SECRET && n.destructibleWallLoc != null) {
                // Check if this block is within the wall area of this secret room
                Location wc = n.destructibleWallLoc;
                int wallDir = n.secretWallDir;
                boolean horiz = wallDir == 1 || wallDir == 3;
                int cx = wc.getBlockX();
                int cz = wc.getBlockZ();
                int bx = key.getBlockX();
                int bz = key.getBlockZ();
                int dx = Math.abs(bx - cx);
                int dz = Math.abs(bz - cz);
                if (horiz && dx == 0 && dz <= 1) { secretRoom = n; break; }
                if (!horiz && dz == 0 && dx <= 1) { secretRoom = n; break; }
            }
        }
        if (secretRoom == null) return;

        long secretKey = run.floor.key(secretRoom.x, secretRoom.z);
        if (revealedSecrets.contains(secretKey)) return;

        // Consume 1 bomb
        st.bombs--;

        // Failure chance increases with floor depth, capped at 80%
        if (failureRoll()) {
            p.sendMessage("§cThe bomb fizzles out! §7(-1 bomb)");
            world.playSound(blockLocation, org.bukkit.Sound.ENTITY_CREEPER_HURT, 0.5f, 0.5f);
            world.spawnParticle(org.bukkit.Particle.SMOKE, blockLocation.clone().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.02);
            return;
        }
        p.sendMessage("§aYou detonate a bomb! §7(-1 bomb)");

        // Destroy the wall blocks in a 3x3 area centered on the clicked block
        Location center = secretRoom.destructibleWallLoc;
        int wallDir = secretRoom.secretWallDir;
        boolean horiz = wallDir == 1 || wallDir == 3;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int off = -1; off <= 1; off++) {
            for (int y = -2; y <= 1; y++) {
                int px = horiz ? cx : (cx + off);
                int pz = horiz ? (cz + off) : cz;
                Location bl = new Location(world, px, cy + y, pz);
                world.getBlockAt(bl).setType(Material.AIR);
                destructibleWalls.remove(bl);
            }
        }

        // Explosion effects
        Location effectLoc = new Location(world, cx, cy, cz);
        world.playSound(effectLoc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
        world.spawnParticle(org.bukkit.Particle.EXPLOSION, effectLoc, 1, 0.5, 0.5, 0.5, 0);
        world.spawnParticle(org.bukkit.Particle.SMOKE, effectLoc, 20, 1, 1, 1, 0.05);
        world.spawnParticle(org.bukkit.Particle.FLAME, effectLoc, 10, 1, 1, 1, 0.02);

        // Reveal the secret room: spawn loot
        revealedSecrets.add(secretKey);
        if (!secretRoom.looted) {
            secretRoom.looted = true;
            for (Player pm : party.onlineMembers()) {
                pm.sendMessage("§dYou found a hidden room!");
            }
            Location c = RoomGen.center(world, secretRoom, BASE_Y, spacing, offsetX, offsetZ);
            spawnPedestal(c.clone().add(1, 0, 0), ItemPool.randomWeapon(run.floorIndex));
            spawnPedestal(c.clone().add(-1, 0, 0), ItemPool.randomArmor(run.floorIndex, 0));
        }
    }

    /**
     * Check if the current room has an adjacent SECRET room with a destructible wall.
     * Returns the secret room node if found, null otherwise.
     */
    private Floor.RoomNode adjacentSecretRoom() {
        if (curRoom == null || run == null || run.floor == null) return null;
        for (Floor.RoomNode n : run.floor.rooms()) {
            if (n.type == RoomType.SECRET && n.secretParent == curRoom && !revealedSecrets.contains(run.floor.key(n.x, n.z))) {
                return n;
            }
        }
        return null;
    }

    // ---------- key/bomb hotbar items ----------

    /** Slot index for the key item (7th hotbar slot, 0-indexed). */
    private static final int KEY_SLOT = 6;
    /** Slot index for the bomb item (8th hotbar slot, 0-indexed). */
    private static final int BOMB_SLOT = 7;
    /** Slot index for the active Mana Shield (9th hotbar slot, 0-indexed). A mana shield placed here
     *  is the active shield — all shield logic (capacity, charging, absorption, durability) watches
     *  this slot. */
    public static final int SHIELD_SLOT = 8;

    /** Create an enchanted key item for the hotbar. */
    private static ItemStack makeKeyItem() {
        ItemStack s = new ItemStack(Material.TRIPWIRE_HOOK);
        s.editMeta(meta -> {
            meta.setDisplayName("§9§lKey");
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(ItemTags.RUN_ITEM),
                    org.bukkit.persistence.PersistentDataType.STRING, "key");
        });
        return s;
    }

    /** Create an enchanted bomb item for the hotbar. */
    private static ItemStack makeBombItem() {
        ItemStack s = new ItemStack(Material.TNT);
        s.editMeta(meta -> {
            meta.setDisplayName("§4§lBomb");
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(ItemTags.RUN_ITEM),
                    org.bukkit.persistence.PersistentDataType.STRING, "bomb");
        });
        return s;
    }

    /** Check if an item is a Dung run item (key or bomb). */
    public static boolean isRunItem(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return false;
        return s.getItemMeta().getPersistentDataContainer().has(
                org.bukkit.NamespacedKey.minecraft(ItemTags.RUN_ITEM),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Check if an item is a Dung key item. */
    public static boolean isKeyItem(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        var key = org.bukkit.NamespacedKey.minecraft(ItemTags.RUN_ITEM);
        if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) return false;
        return "key".equals(pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING));
    }

    /** Check if an item is a Dung bomb item. */
    public static boolean isBombItem(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        var key = org.bukkit.NamespacedKey.minecraft(ItemTags.RUN_ITEM);
        if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) return false;
        return "bomb".equals(pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING));
    }

    /** Sync the key and bomb items into the player's hotbar slots 7 and 8.
     *  Called every tick to keep them locked in place. When a slot has no key/bomb,
     *  a black stained glass pane labelled "Empty" is shown instead. */
    private void syncHotbarItems(Player p) {
        PlayerState st = run == null ? null : run.playerStateOf(p.getUniqueId());
        if (st == null) return;
        PlayerInventory inv = p.getInventory();

        syncCountedSlot(inv, KEY_SLOT, st.keys, makeKeyItem());
        syncCountedSlot(inv, BOMB_SLOT, st.bombs, makeBombItem());

        // Slot 9 (index 8): Mana Shield item. A shield here is the active mana shield.
        syncShieldSlot(p);
    }

    /** Keep a counted run-item slot (key/bomb) in sync with its PlayerState count without re-sending
     *  the slot every tick. When the count is 0 a leftover real key/bomb is cleared so the empty
     *  placeholder can show, but the placeholder itself is never touched once it is in place — so it
     *  does not flicker or get duplicated tick to tick. */
    private void syncCountedSlot(PlayerInventory inv, int slot, int count, ItemStack item) {
        ItemStack cur = inv.getItem(slot);
        String kind = runItemKind(cur);
        if (count > 0) {
            ItemStack expected = item.clone();
            expected.setAmount(Math.min(count, 64));
            if (!itemsMatch(cur, expected)) inv.setItem(slot, expected);
            return;
        }
        // count == 0: a leftover real key/bomb with nothing remaining is cleared so the placeholder
        // can show. This only fires once (the placeholder itself is tagged "empty", not "key"/"bomb"),
        // so it does not re-send the slot every tick.
        if ("key".equals(kind) || "bomb".equals(kind)) {
            inv.setItem(slot, null);
            cur = inv.getItem(slot);
        }
        // Show the empty placeholder only if the slot isn't already holding one (no per-tick re-send).
        if (cur == null || cur.getType() == Material.AIR || !isRunItem(cur)) {
            inv.setItem(slot, makeEmptySlotItem());
        }
    }

    /** The RUN_ITEM tag value for an item ("key", "bomb" or "empty"), or null if it isn't a run item. */
    private static String runItemKind(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return null;
        return s.getItemMeta().getPersistentDataContainer().get(
                org.bukkit.NamespacedKey.minecraft(ItemTags.RUN_ITEM),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Keep the Mana Shield equip slot (slot 9, index 8) in sync. Slot 9 is a pure equip slot: a
     *  shield is only active when the player places one here, and it is never auto-moved in — the
     *  player chooses which shield to equip and puts it in the slot themselves. If the slot holds a
     *  shield it is left alone (never auto-overwritten). If the equipped shield is persistent and a
     *  strictly better shield is in the inventory, we ask in chat with a clickable Switch button
     *  instead of auto-replacing it. When the slot is empty of a shield it shows an indicator: a
     *  green "Equip Shield" pane when the player owns a shield elsewhere (so they know they can
     *  equip one), or the standard empty placeholder when they own none. All shield logic watches
     *  this slot. */
    private void syncShieldSlot(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack inSlot = inv.getItem(SHIELD_SLOT);
        if (inSlot != null && !inSlot.getType().isAir() && GearFactory.isShield(inSlot)) {
            // A shield is equipped in the slot. Never auto-overwrite it. If it is persistent and a
            // strictly better shield exists in the inventory, offer to swap via a chat prompt.
            if (GearFactory.isPersistent(inSlot)) {
                ItemStack better = bestBetterShield(inv, inSlot);
                if (better != null) promptShieldSwitch(p, inSlot, better);
            }
            // The green equip indicator should only exist while the slot is empty of a shield. Once a
            // shield is equipped, sweep up any panes left over from a hotbar swap so they don't linger.
            clearEquipIndicators(inv);
            return;
        }
        // The slot is empty of a shield. It is a manual equip slot, so we never pull a shield in
        // automatically — we only show an indicator: green when a shield is available to equip, the
        // standard empty placeholder when none is owned. First sweep away any stray panes so exactly
        // one indicator exists, then refresh the slot with the correct one. A shield being dragged on
        // the cursor (picked up from the inventory but not yet placed) still counts as "owned", so the
        // green swappable pane stays up while the player moves a shield toward this slot.
        clearEquipIndicators(inv);
        boolean hasShield = GearFactory.findShieldItem(inv) != null || shieldOnCursor(p);
        ItemStack indicator = hasShield ? makeEquipSlotItem() : makeEmptySlotItem();
        ItemStack cur = inv.getItem(SHIELD_SLOT);
        if (cur == null || cur.getType() != indicator.getType()) {
            inv.setItem(SHIELD_SLOT, indicator);
        }
    }

    /** True if the player currently holds a mana shield on their inventory cursor (mid-drag from a
     *  pickup). This is part of "owns a shield" so the slot-9 indicator stays a swappable green pane
     *  while the player is moving a shield from their inventory into the equip slot. */
    private static boolean shieldOnCursor(Player p) {
        return GearFactory.isShield(p.getItemOnCursor());
    }

    /** Find the shield with the highest capacity in the inventory (main hand, offhand, then storage),
     *  excluding the one currently in the shield slot, whose capacity is strictly higher than the
     *  given equipped shield. Returns null if no strictly better shield is owned. */
    private static ItemStack bestBetterShield(PlayerInventory inv, ItemStack current) {
        int curMax = GearFactory.getShieldMax(current);
        ItemStack best = null;
        int bestMax = curMax;
        if (GearFactory.isShield(inv.getItemInMainHand())) {
            int m = GearFactory.getShieldMax(inv.getItemInMainHand());
            if (m > bestMax) { bestMax = m; best = inv.getItemInMainHand(); }
        }
        if (GearFactory.isShield(inv.getItemInOffHand())) {
            int m = GearFactory.getShieldMax(inv.getItemInOffHand());
            if (m > bestMax) { bestMax = m; best = inv.getItemInOffHand(); }
        }
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot == SHIELD_SLOT) continue;
            ItemStack s = inv.getItem(slot);
            if (s == null || !GearFactory.isShield(s)) continue;
            int m = GearFactory.getShieldMax(s);
            if (m > bestMax) { bestMax = m; best = s; }
        }
        return best;
    }

    /** Cooldown between repeated "switch persistent shield" prompts so the per-tick sync doesn't spam. */
    private static final long SHIELD_SWITCH_COOLDOWN_MS = 8000;
    private final Map<UUID, Long> lastShieldSwitchPrompt = new HashMap<>();

    /** Ask the player to swap their equipped persistent shield for a better shield in their inventory,
     *  with a clickable Switch button. Gated by a cooldown so it appears at most once per interval. */
    private void promptShieldSwitch(Player p, ItemStack current, ItemStack better) {
        long now = System.currentTimeMillis();
        Long last = lastShieldSwitchPrompt.get(p.getUniqueId());
        if (last != null && now - last < SHIELD_SWITCH_COOLDOWN_MS) return;
        lastShieldSwitchPrompt.put(p.getUniqueId(), now);
        String curName = itemDisplayName(current);
        String betterName = itemDisplayName(better);
        net.kyori.adventure.text.Component msg = net.kyori.adventure.text.Component.text(
                "§7A better shield (§b" + betterName + "§7) is in your inventory. Your §e" + curName
                        + "§7 is persistent — keep it or switch? ")
                .append(com.lieyabull.dung.ui.ChatUI.command("§a[Switch]", "/dung shieldswitch",
                        "Swap in the better shield"));
        p.sendMessage(msg);
    }

    /** Perform the persistent-shield swap requested via /dung shieldswitch: place the better shield
     *  in slot 9 and move the previous persistent shield to where the better one was. */
    public void doShieldSwitch(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack current = inv.getItem(SHIELD_SLOT);
        if (current == null || current.getType().isAir()
                || !GearFactory.isShield(current) || !GearFactory.isPersistent(current)) {
            p.sendMessage("§7No persistent shield equipped to switch.");
            return;
        }
        ItemStack better = bestBetterShield(inv, current);
        if (better == null) {
            p.sendMessage("§7No better shield available.");
            return;
        }
        int curMax = GearFactory.getShieldMax(current);
        int bestLoc = Integer.MIN_VALUE;
        int bestMax = curMax;
        if (GearFactory.isShield(inv.getItemInMainHand())
                && GearFactory.getShieldMax(inv.getItemInMainHand()) > bestMax) {
            bestMax = GearFactory.getShieldMax(inv.getItemInMainHand());
            bestLoc = -1;
        }
        if (GearFactory.isShield(inv.getItemInOffHand())
                && GearFactory.getShieldMax(inv.getItemInOffHand()) > bestMax) {
            bestMax = GearFactory.getShieldMax(inv.getItemInOffHand());
            bestLoc = -2;
        }
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot == SHIELD_SLOT) continue;
            ItemStack s = inv.getItem(slot);
            if (s == null || !GearFactory.isShield(s)) continue;
            int m = GearFactory.getShieldMax(s);
            if (m > bestMax) { bestMax = m; bestLoc = slot; }
        }
        if (bestLoc == Integer.MIN_VALUE) {
            p.sendMessage("§7No better shield available.");
            return;
        }
        // Swap: slot 9 gets the better shield; the previous persistent shield moves to its old spot.
        inv.setItem(SHIELD_SLOT, better);
        if (bestLoc == -1) inv.setItemInMainHand(current);
        else if (bestLoc == -2) inv.setItemInOffHand(current);
        else inv.setItem(bestLoc, current);
        lastShieldSwitchPrompt.remove(p.getUniqueId());
        p.sendMessage("§aSwitched to §b" + itemDisplayName(better) + "§a.");
    }

    private static String itemDisplayName(ItemStack s) {
        if (s == null) return "?";
        if (s.getItemMeta() != null) {
            String n = s.getItemMeta().getDisplayName();
            if (n != null && !n.isEmpty()) return n;
        }
        return s.getType().name();
    }

    /** Compare two ItemStacks for Dung run-item equality (type + PDC tag). */
    private static boolean itemsMatch(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        if (a.getAmount() != b.getAmount()) return false;
        boolean aIsRun = isRunItem(a);
        boolean bIsRun = isRunItem(b);
        return aIsRun == bIsRun;
    }

    /** Remove key/bomb run items from a player's hotbar slots 7, 8 and 9. */
    private static void clearHotbarItems(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int slot : new int[]{KEY_SLOT, BOMB_SLOT, SHIELD_SLOT}) {
            ItemStack s = inv.getItem(slot);
            if (s != null && isRunItem(s)) inv.setItem(slot, null);
        }
    }

    /** Create a black stained glass pane labelled "Empty" for unused key/bomb slots.
     *  Marked as a run item so inventory click/drag handlers block moving it — without the
     *  tag, a player can pull it out of the hotbar and syncHotbarItems spawns a fresh copy
     *  each tick, duplicating it. */
    private static ItemStack makeEmptySlotItem() {
        ItemStack s = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        s.editMeta(meta -> {
            meta.setDisplayName("§8Empty");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(ItemTags.RUN_ITEM),
                    org.bukkit.persistence.PersistentDataType.STRING, "empty");
        });
        return s;
    }

    /** Create a green stained glass pane labelled "Equip Shield" shown in slot 9 when the player owns
     *  a shield elsewhere in their inventory. Unlike the key/bomb placeholders this is NOT tagged as a
     *  run item: slot 9 is a real equip slot, so the pane must stay swappable for the player to place
     *  a shield into the slot manually. It carries its own {@link ItemTags#EQUIP_INDICATOR} tag so the
     *  sync can recognise and sweep up any panes (so it only exists while the slot is empty of a shield).
     *  It holds no gameplay value, so it can't be exploited. */
    private static ItemStack makeEquipSlotItem() {
        ItemStack s = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        s.editMeta(meta -> {
            meta.setDisplayName("§aEquip Shield");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(
                    org.bukkit.NamespacedKey.minecraft(ItemTags.EQUIP_INDICATOR),
                    org.bukkit.persistence.PersistentDataType.STRING, "true");
        });
        return s;
    }

    /** True if the item is our green "Equip Shield" indicator pane. */
    private static boolean isEquipIndicator(ItemStack s) {
        if (s == null || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        return pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.EQUIP_INDICATOR),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Remove every green "Equip Shield" indicator pane from the inventory except the one in the shield
     *  slot (so a swap never leaves a duplicate pane behind). Slot 9 is handled separately by the caller. */
    private static void clearEquipIndicators(PlayerInventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == SHIELD_SLOT) continue;
            ItemStack s = inv.getItem(i);
            if (s != null && isEquipIndicator(s)) inv.setItem(i, null);
        }
        if (inv.getHeldItemSlot() != SHIELD_SLOT && isEquipIndicator(inv.getItemInMainHand())) {
            inv.setItemInMainHand(null);
        }
        if (isEquipIndicator(inv.getItemInOffHand())) inv.setItemInOffHand(null);
    }

    // ---------- utilities ----------

    public boolean playerHurt(Player p, double dmg) {
        PlayerState ps = run == null ? null : run.playerStateOf(p.getUniqueId());
        if (ps == null) return false;
        if (ps.isInvuln() || ps.dead) return false;
        ps.hurt(dmg);
        applyDamageKnockback(p, dmg);
        p.playHurtAnimation(0.0f);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 1.0f, 0.9f);
        return true;
    }

    /** Hurt a player, bypassing invulnerability frames. Used for Mulliboom explosions that should
     *  always deal damage regardless of i-frames. */
    public boolean playerHurtBypassInvuln(Player p, double dmg) {
        PlayerState ps = run == null ? null : run.playerStateOf(p.getUniqueId());
        if (ps == null || ps.dead) return false;
        ps.hurt(dmg);
        applyDamageKnockback(p, dmg);
        p.playHurtAnimation(0.0f);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 1.0f, 0.9f);
        return true;
    }

    /** Knock a hurt player AWAY from their attacker (nearest living enemy or the boss), scaled by
     *  the damage taken and capped at ~4.5 blocks of travel. */
    private void applyDamageKnockback(Player p, double dmg) {
        if (!running || run == null) return;
        // Direction: away from the nearest threat that could have hit them.
        org.bukkit.util.Vector away = null;
        double bestDist = Double.MAX_VALUE;
        for (List<Enemy> list : roomEnemies.values()) {
            for (Enemy e : list) {
                if (!e.alive()) continue;
                double d = e.entity.getLocation().distanceSquared(p.getLocation());
                if (d < bestDist && d <= 16 * 16) {
                    bestDist = d;
                    away = p.getLocation().toVector().subtract(e.entity.getLocation().toVector());
                }
            }
        }
        if (away == null && boss != null && boss.location() != null) {
            away = p.getLocation().toVector().subtract(boss.location().toVector());
        }
        if (away == null) return;
        double y = away.getY();
        away.setY(0);
        if (away.lengthSquared() < 0.001) {
            // Attacked from directly above/below — push along the player's facing instead
            away = p.getLocation().getDirection().setY(0);
        }
        if (away.lengthSquared() < 0.001) return;
        away.normalize();
        // Damage → impulse: light hits barely nudge, heavy hits launch up to ~4.5 blocks
        double strength = Math.min(1.1, 0.18 + dmg * 0.014);
        away.multiply(strength).setY(Math.max(0.15, Math.min(0.35, y * 0.2 + 0.2)));
        p.setVelocity(p.getVelocity().add(away));
    }

    public void onPlayerDeath(Player p) {
        if (!running) return;
        UUID pid = p.getUniqueId();
        if (deadPlayers.contains(pid)) return;
        deadPlayers.add(pid);
        PlayerState st = run.playerStateOf(pid);
        if (st == null) return;

        stripRunGear(p);
        // Damage persistent gear on death
        damagePersistentGear(p, DEATH_DURABILITY_DIVISOR);
        int floorReached = run.floorIndex + 1;
        int kills = run.kills;
        int runCoins = st.coins;

        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        prof.deaths++;
        prof.kills += kills;
        plugin.meta().save();

        String cls = prof.classId;
        if (cls != null && !cls.isEmpty() && Character.isLowerCase(cls.charAt(0))) {
            cls = Character.toUpperCase(cls.charAt(0)) + cls.substring(1);
        }
        p.sendMessage("§c§lYOU DIED §8— Floor " + floorReached);
        if (kills > 0) p.sendMessage("§7  Kills this run: §f" + kills);
        if (runCoins > 0) p.sendMessage("§7  Run coins: §e" + runCoins + " §7(gone unless revived by defeating the boss)");
        p.sendMessage("§7  Persistent gear durability reduced by 10%");
        p.sendMessage("");
        p.sendMessage("§7Unlocks you keep:");
        p.sendMessage("§7  Class: §f" + cls);
        p.sendMessage("§7  Persistent coins: §6" + prof.persistentCoins);
        p.sendMessage("§7  Progress: §f" + prof.clears + "§7 floors cleared, best §f" + prof.bestFloor + "§7, §f" + prof.kills + "§7 kills");
        if (prof.persistentCoins >= 20) {
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§a  You have enough for: §f/shop weapon"));
        } else {
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§8  Need §6" + (20 - prof.persistentCoins) + "§8 more coins for a weapon (/shop weapon)"));
        }
        p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§7  Try /shop, /upgrades, or /dung start to go again."));

        // Set player to spectator mode — they stay in the instance and can be revived
        // if the boss is defeated. They remain at their death location as a spectator.
        p.setHealth(20);
        p.setGameMode(org.bukkit.GameMode.SPECTATOR);
        p.setWalkSpeed(0.2f);
        removeHeadHp(p); // spectators don't carry an HP readout

        HUD hud = huds.get(p.getUniqueId());
        TabUI tab = tabs.get(p.getUniqueId());
        org.bukkit.scoreboard.Scoreboard sb = playerBoards.get(p.getUniqueId());
        if (hud != null && sb != null) hud.reset(p, sb);
        if (tab != null && sb != null) tab.reset(sb);

        // Clean up per-player state (keep the player in the party/instance for revival)
        lastGear.remove(pid);
        huds.remove(pid);
        tabs.remove(pid);
        playerRoom.remove(pid);
        if (boss != null) boss.removeViewer(p);

        // Check if all party members are now dead — if so, end the run
        if (allMembersDead()) {
            for (Player member : party.onlineMembers()) {
                member.sendMessage("§c§lAll party members have fallen! The run is over.");
            }
            endRun();
        }
    }

    /** Check if every online party member is in the deadPlayers set. */
    private boolean allMembersDead() {
        List<Player> online = party.onlineMembers();
        if (online.isEmpty()) return true;
        for (Player p : online) {
            if (!deadPlayers.contains(p.getUniqueId())) return false;
        }
        return true;
    }

    /** Send a player back where they were before the run started (fallback: dungeon world spawn). */
    private void teleportOut(Player p) {
        Location back = returnLocs.remove(p.getUniqueId());
        if (back != null && back.getWorld() != null) {
            p.teleport(back);
        } else if (world != null) {
            p.teleport(world.getSpawnLocation());
        }
    }

    /** Remove a single party member from this running instance (quit / /dung leave / party kick),
     *  without ending the run for the rest of the party. Party membership is handled by the caller
     *  (PartyManager/PartyCommand); this only cleans up instance state and ends the run if the party
     *  has become empty. */
    public void removePlayer(Player p) {
        if (run == null) return;
        UUID pid = p.getUniqueId();
        if (boss != null) boss.removeViewer(p);
        HUD hud = huds.remove(pid);
        TabUI tab = tabs.remove(pid);
        org.bukkit.scoreboard.Scoreboard sb = playerBoards.remove(pid);
        if (hud != null && sb != null) hud.reset(p, sb);
        if (tab != null && sb != null) tab.reset(sb);
        lastGear.remove(pid);
        lastBarHearts.remove(pid);
        lastBarMana.remove(pid);
        lastHeadHp.remove(pid);
        removeHeadHp(p);
        playerRoom.remove(pid);
        stripRunGear(p);
        restoreSavedInventory(p);
        deliverPendingPersists(p);
        // Leaving a run early damages persistent gear at half the rate of dying (5% of max
        // instead of 10%). Applied after restoreSavedInventory so the damage hits the final
        // inventory state — otherwise the undamaged pre-run snapshot would be restored.
        damagePersistentGear(p, LEAVE_DURABILITY_DIVISOR);
        p.sendMessage("§7  Left the run early: persistent gear durability reduced by 5%");
        p.setWalkSpeed(0.2f);
        p.setFoodLevel(20);
        teleportOut(p);
        GameManager.instance().removePlayerFromInstance(p);
        if (party.isEmpty() || allMembersDead()) {
            endRun();
        }
    }

    /** Deliver successfully-persisted items to a player's inventory after the run (or on leaving
     *  early). Items were stored in the pendingPersists queue by tryPersist during the run. */
    private void deliverPendingPersists(Player p) {
        UUID pid = p.getUniqueId();
        List<ItemStack> pending = pendingPersists.remove(pid);
        if (pending == null || pending.isEmpty()) return;
        for (ItemStack item : pending) {
            HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                for (ItemStack ls : leftover.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), ls);
                }
            }
            p.sendMessage("§d§l✦ §dPERSISTED! §7Your " + item.getItemMeta().getDisplayName()
                    + " §7arrived safe and sound.");
        }
        plugin.meta().save();
    }

    private static void stripRunGear(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack s = inv.getItem(slot);
            if (s != null && (isRunOnly(s) || isRunItem(s))) inv.setItem(slot, null);
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && (isRunOnly(off) || isRunItem(off))) inv.setItemInOffHand(null);
        org.bukkit.inventory.EquipmentSlot[] slots = {
                org.bukkit.inventory.EquipmentSlot.FEET,
                org.bukkit.inventory.EquipmentSlot.LEGS,
                org.bukkit.inventory.EquipmentSlot.CHEST,
                org.bukkit.inventory.EquipmentSlot.HEAD
        };
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && isRunOnly(armor[i])) inv.setItem(slots[i], null);
        }
        clearHotbarItems(p);
    }

    /** Check if an item is persistent dung gear (survives across runs). */
    private static boolean isPersistentGear(ItemStack s) {
        if (s == null || s.getType() == Material.AIR || s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        return pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.PERSISTENT),
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    /**
     * Roll for key/bomb failure. The deeper the floor, the higher the chance, capped at 80%.
     * Floor 0: 0%, Floor 1: ~10%, Floor 2: ~20%, ... Floor 8+: 80%.
     */
    private boolean failureRoll() {
        int floor = run != null ? run.floorIndex : 0;
        double chance = Math.min(0.80, floor * 0.10);
        return Math.random() < chance;
    }

    /** Find the first free (empty or air) slot in the inventory, skipping the given slot. */
    private static int firstFreeSlot(PlayerInventory inv, int skipSlot) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == skipSlot) continue;
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType() == Material.AIR) return i;
        }
        return -1;
    }

    private static boolean isRunOnly(ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return false;
        if (s.getType() == Material.GOLD_NUGGET) return true;
        if (s.getItemMeta() == null) return false;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        boolean isGear = pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.GEAR),
                org.bukkit.persistence.PersistentDataType.STRING);
        boolean persistent = pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.PERSISTENT),
                org.bukkit.persistence.PersistentDataType.STRING);
        return isGear && !persistent;
    }

    /** Death penalty: reduce persistent gear durability by 1/10 of max (10%). */
    private static final int DEATH_DURABILITY_DIVISOR = 10;
    /** Leaving-a-run penalty: half the death penalty (5% of max). */
    private static final int LEAVE_DURABILITY_DIVISOR = 20;

    /** Damage all persistent gear on death: reduce durability by 10 (or 10% of max). */
    private void damagePersistentGear(Player p, int divisor) {
        PlayerInventory inv = p.getInventory();
        // PlayerInventory.getSize() covers storage + hotbar (the main hand is one of the hotbar
        // slots), the 4 armor slots, and the offhand. Iterate every slot EXACTLY once so a piece is
        // never damaged twice (double-damage made each piece lose ~20% instead of 10%, and re-broke
        // an already-broken item which handleBrokenArmor then kept re-moving and duplicating).
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack s = inv.getItem(slot);
            if (s == null || s.getType() == Material.AIR) continue;
            damageIfPersistent(s, p, divisor);
        }
    }

    private void damageIfPersistent(ItemStack s, Player p, int divisor) {
        if (s.getItemMeta() == null) return;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        boolean persistent = pdc.has(org.bukkit.NamespacedKey.minecraft(ItemTags.PERSISTENT),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (!persistent) return;
        int max = GearFactory.getMaxDurability(s);
        if (max <= 0) return;
        // Reduce by 1/divisor of max (minimum 1)
        int dmg = Math.max(1, max / divisor);
        boolean broken = GearFactory.damageItem(s, dmg);
        if (broken) {
            handleBrokenArmor(p, s, "");
        }
    }

    /** Display a player's current/max health as a bar above their name while a run is active,
     *  updating only when the shown value changes (avoids re-sending entity metadata every tick).
     *  Implemented as a non-persistent TextDisplay riding the player — Player.setCustomName does
     *  not change a player's overhead nametag, so the classic approach doesn't render. */
    private void updateHeadHp(Player p, PlayerState st) {
        // Dead players (spectators waiting for revival) get no HP readout
        if (st.dead || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            removeHeadHp(p);
            return;
        }
        int cur = (int) Math.ceil(st.hearts);
        int max = st.maxHearts;
        int filled = Math.max(0, Math.min(10, (int) Math.round(10.0 * cur / max)));
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "§a█" : "§8█");
        }
        // Three rows above the head: player name / HP bar / HP as numbers
        String text = "§f" + p.getName() + "\n" + bar + "\n§c" + cur + " §7/ §f" + max;
        org.bukkit.entity.TextDisplay tag = hpTags.get(p.getUniqueId());
        if (tag == null || !tag.isValid()) {
            if (tag != null) tag.remove();
            final String initial = text;
            tag = p.getWorld().spawn(p.getLocation(), org.bukkit.entity.TextDisplay.class, td -> {
                td.text(legacy(initial));
                td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                td.setSeeThrough(false);
                // Lift the tag clear of the head: a passenger display renders at head height,
                // so translate it up (~0.75 blocks) via its transformation.
                td.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(0f, 0.25f, 0f), // translation
                        new org.joml.Quaternionf(),           // left rotation (none)
                        new org.joml.Vector3f(1f, 1f, 1f),    // scale
                        new org.joml.Quaternionf()));         // right rotation (none)
                td.setViewRange(0.6f);
                td.setPersistent(false);
                td.setViewRange(0.6f);
            });
            hpTags.put(p.getUniqueId(), tag);
            p.addPassenger(tag);
        } else {
            String last = lastHeadHp.get(p.getUniqueId());
            if (text.equals(last)) return;
            tag.text(legacy(text));
        }
        lastHeadHp.put(p.getUniqueId(), text);
    }

    private net.kyori.adventure.text.Component legacy(String s) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(s);
    }

    /** Remove a player's overhead HP bar (and any stale passenger), if present. Dismounts the
     *  display explicitly before removing it so the client never keeps rendering a ghost tag. */
    private void removeHeadHp(Player p) {
        org.bukkit.entity.TextDisplay tag = hpTags.remove(p.getUniqueId());
        lastHeadHp.remove(p.getUniqueId());
        if (tag != null) {
            p.removePassenger(tag);
            tag.remove();
        }
        for (org.bukkit.entity.Entity e : List.copyOf(p.getPassengers())) {
            if (e instanceof org.bukkit.entity.TextDisplay) e.remove();
        }
    }

    /** Handle a persistent gear item reaching 0 durability: if it is actually equipped (an armor
     *  slot or the offhand) unequip it and move it into a free main-inventory slot. If the bag is
     *  full it goes into the player's /stash (takeout-only container) instead of the ground. A
     *  broken item already sitting in the main inventory is left where it is (it isn't granting
     *  stats, so there's nothing to unequip). Always notifies the player. Never duplicates: the
     *  equipped slot is cleared BEFORE the item is placed, and items already in the inventory are
     *  not moved at all. */
    private void handleBrokenArmor(Player p, ItemStack item, String reason) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        // Unequip: clear whichever equip slot (armor or offhand) currently holds this item.
        boolean equipped = false;
        org.bukkit.inventory.EquipmentSlot[] armorSlots = {
                org.bukkit.inventory.EquipmentSlot.HEAD,
                org.bukkit.inventory.EquipmentSlot.CHEST,
                org.bukkit.inventory.EquipmentSlot.LEGS,
                org.bukkit.inventory.EquipmentSlot.FEET
        };
        for (org.bukkit.inventory.EquipmentSlot slot : armorSlots) {
            if (inv.getItem(slot) == item) {
                inv.setItem(slot, null);
                equipped = true;
                break;
            }
        }
        if (inv.getItemInOffHand() == item) {
            inv.setItemInOffHand(null);
            equipped = true;
        }
        if (inv.getItem(SHIELD_SLOT) == item) {
            inv.setItem(SHIELD_SLOT, null);
            equipped = true;
        }
        if (equipped) {
            // Move into a free main-inventory slot, or the stash if the bag is full.
            com.lieyabull.dung.ui.StashUI.placeOrStash(p, item);
        }
        String name = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : item.getType().name();
        p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cYour " + name + " §cbroke" + reason + "! §7Repair at §6/shop§7 (150 coins + 100 shards for 10 durability)."));
    }

    /** Set a transient status message for a player, shown in the action bar alongside HP/mana.
     *  The message automatically expires after STATUS_DURATION_MS milliseconds. */
    public void setStatus(Player p, String text) {
        StatusMessage cur = statusMessages.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (cur != null && cur.text.equals(text) && now < cur.expiresAt) return;
        statusMessages.put(p.getUniqueId(), new StatusMessage(text, now + STATUS_DURATION_MS));
    }

    /** Look up an Enemy by its entity UUID across all rooms. Used by GameListener.onProjectileHit
     *  to find the enemy that fired a projectile so it can apply the correct damage value. */
    public Enemy enemyByEntity(UUID entityId) {
        for (List<Enemy> enemies : roomEnemies.values()) {
            for (Enemy e : enemies) {
                if (e.entity.getUniqueId().equals(entityId)) return e;
            }
        }
        return null;
    }

    public void endRun() {
        running = false;
        for (Player p : party.onlineMembers()) {
            HUD hud = huds.get(p.getUniqueId());
            TabUI tab = tabs.get(p.getUniqueId());
            org.bukkit.scoreboard.Scoreboard sb = playerBoards.get(p.getUniqueId());
            if (hud != null && sb != null) hud.reset(p, sb);
            if (tab != null && sb != null) tab.reset(sb);
            stripRunGear(p);
            restoreSavedInventory(p);
            deliverPendingPersists(p);
            // Damage persistent gear after restoring the snapshot so the damage is applied
            // to the final inventory state — otherwise restoreSavedInventory would restore
            // the undamaged pre-run snapshot and negate the durability loss. Dead players have
            // already been charged this durability penalty in onPlayerDeath, so skip them here
            // to avoid a double loss when the whole party falls.
            if (!deadPlayers.contains(p.getUniqueId())) {
                damagePersistentGear(p, DEATH_DURABILITY_DIVISOR);
            }
            // Restore game mode, health, and hunger — players may be in SPECTATOR mode if they died
            p.setGameMode(org.bukkit.GameMode.SURVIVAL);
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            removeHeadHp(p);
            teleportOut(p);
        }
        clearRoomEntities();
        huds.clear();
        tabs.clear();
        playerBoards.clear();
        deadPlayers.clear();
        for (org.bukkit.entity.TextDisplay t : hpTags.values()) t.remove();
        hpTags.clear();
        lastHeadHp.clear();
        returnLocs.clear();
        savedInventories.clear();
        // Remove this instance from the GameManager registry
        GameManager.instance().removeInstance(this);
        // Delete the dedicated run world from disk once every player is out (deferred a couple of
        // ticks so pending teleports settle first). Only run worlds are ever deleted.
        if (runWorld != null
                && runWorld.getName().startsWith(com.lieyabull.dung.world.WorldManager.RUN_WORLD_PREFIX)) {
            final World rw = runWorld;
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.worldManager().deleteRunWorld(rw), 2L);
        }
    }

    private void clearRoomEntities() {
        for (List<Enemy> es : roomEnemies.values()) for (Enemy e : es) e.despawn();
        roomEnemies.clear();
        for (org.bukkit.entity.Item heart : eliteHearts) if (heart.isValid()) heart.remove();
        eliteHearts.clear();
        roomLocked.clear();
        spawnedRooms.clear();
        playerRoom.clear();
        clearShopkeepers();
        clearWorkstations();
        if (boss != null) { boss.despawn(); boss = null; }
        bossRoom = null;
        clearPedestals();
        if (world != null && run != null && run.floor != null) {
            for (Floor.RoomNode n : run.floor.rooms()) sealDoors(n, false);
        }
        tearDownDungeon();
    }

    private void tearDownDungeon() {
        if (world == null || run == null || run.floor == null) return;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Floor.RoomNode n : run.floor.rooms()) {
            minX = Math.min(minX, baseX(n));
            minZ = Math.min(minZ, baseZ(n));
            maxX = Math.max(maxX, baseX(n) + RoomGen.WALL + n.sizeW);
            maxZ = Math.max(maxZ, baseZ(n) + RoomGen.WALL + n.sizeH);
        }
        maxX += spacing; maxZ += spacing;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = BASE_Y; y <= BASE_Y + RoomGen.ROOM_HEIGHT + 1; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    /** Draw a curved lightning arc between two locations using CRIT particles. */
    private void drawLightningArc(org.bukkit.World w, org.bukkit.Location src, org.bukkit.Location dst) {
        org.bukkit.util.Vector delta = dst.toVector().subtract(src.toVector());
        double length = delta.length();
        if (length < 0.5) return;
        org.bukkit.util.Vector dir = delta.clone().normalize();
        // Perpendicular vector for the sine-wave offset
        org.bukkit.util.Vector perp;
        if (Math.abs(dir.getY()) < 0.9) {
            perp = dir.clone().crossProduct(new org.bukkit.util.Vector(0, 1, 0)).normalize();
        } else {
            perp = dir.clone().crossProduct(new org.bukkit.util.Vector(1, 0, 0)).normalize();
        }
        int steps = Math.max(8, (int) (length * 2));
        double amplitude = Math.min(1.0, length * 0.15);
        for (int i = 0; i <= steps; i++) {
            double progress = (double) i / steps;
            org.bukkit.util.Vector point = src.toVector().clone().add(dir.clone().multiply(length * progress));
            // Sine wave offset perpendicular to the direction
            double offset = Math.sin(progress * Math.PI) * amplitude;
            point.add(perp.clone().multiply(offset));
            w.spawnParticle(org.bukkit.Particle.CRIT, point.toLocation(w), 1, 0, 0, 0, 0);
        }
    }

    /** Draw a lightning arc that lingers ~4x longer than a single draw: spawns the arc once now,
     *  then re-spawns the same frozen path at +1, +2, +3 ticks so the bolt stays visible for ~4
     *  ticks instead of flickering out instantly. Coordinates are snapshots, so the lingering arc
     *  holds where the bolt originally struck even if the targets moved. */
    private void drawLightningArcLinger(org.bukkit.World w, org.bukkit.Location src, org.bukkit.Location dst) {
        org.bukkit.util.Vector s = src.toVector().clone();
        org.bukkit.util.Vector d = dst.toVector().clone();
        for (int delay = 0; delay < 4; delay++) {
            if (delay == 0) {
                drawLightningArc(w, src, dst);
            } else {
                final int ticks = delay;
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,
                        () -> drawLightningArc(w,
                                s.toLocation(w),
                                d.toLocation(w)),
                        ticks);
            }
        }
    }

    // ---------- UI ----------

    private void refreshUI() {
        tabTickCounter++;
        boolean refreshTab = tabTickCounter % 10 == 0;
        for (Player p : party.onlineMembers()) {
            HUD hud = huds.get(p.getUniqueId());
            TabUI tab = tabs.get(p.getUniqueId());
            org.bukkit.scoreboard.Scoreboard sb = playerBoards.get(p.getUniqueId());
            if (hud != null && sb != null) hud.update(p, this, sb);
            if (tab != null && sb != null && refreshTab) tab.refresh(p, this, sb);

            // Throttled action bar — ONE writer (HUD.sendBar) owns the action bar, so all status
            // texts (secret hint, room events, etc.) are folded in as a suffix here instead of
            // competing sendActionBar calls (which flickered/overwrote each other).
            PlayerState st = run.playerStateOf(p.getUniqueId());
            if (st == null) continue;
            updateHeadHp(p, st);
            barTick++;
            // Collect status messages: secret hint + transient status (room locked, doors opened, etc.)
            String hint = "";
            if (st.bombs > 0 && adjacentSecretRoom() != null) {
                hint = "§7You sense a hidden room nearby... §4[Use a bomb on the cracked wall]";
            }
            // Check for a transient status message (expires after STATUS_DURATION_MS)
            StatusMessage sm = statusMessages.get(p.getUniqueId());
            if (sm != null) {
                if (System.currentTimeMillis() < sm.expiresAt) {
                    String sep = hint.isEmpty() ? "" : "   ";
                    hint = hint + sep + sm.text;
                } else {
                    statusMessages.remove(p.getUniqueId());
                }
            }
            Double lastH = lastBarHearts.get(p.getUniqueId());
            Double lastM = lastBarMana.get(p.getUniqueId());
            if (hud != null && (barTick % 5 == 0 || st.hearts != (lastH != null ? lastH : -1) || st.mana != (lastM != null ? lastM : -1))) {
                hud.sendBar(p, st, hint);
                lastBarHearts.put(p.getUniqueId(), st.hearts);
                lastBarMana.put(p.getUniqueId(), st.mana);
            }
        }
    }
}