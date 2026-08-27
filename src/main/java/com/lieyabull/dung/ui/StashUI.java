package com.lieyabull.dung.ui;

import com.lieyabull.dung.Dung;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player stash: a takeout-only container used to hold broken persistent gear that couldn't be
 * moved into a free inventory slot. Players can retrieve items with /stash but can never deposit
 * items into it (the only writer is the broken-armour fallback in DungeonInstance/GameListener).
 * Stash contents persist across restarts (stashes.yml).
 */
public final class StashUI implements Listener {

    private static final int STASH_SIZE = 27;
    /** How often to remind players who have items waiting in their stash (5 minutes = 6000 ticks). */
    private static final long REMINDER_TICKS = 6000L;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Dung plugin;
    private final File file;
    private final YamlConfiguration data = new YamlConfiguration();
    private final Map<UUID, List<ItemStack>> stashes = new ConcurrentHashMap<>();
    /** Open stash GUI -> owning player, so clicks/drags can be routed to the right stash. */
    private final Map<Inventory, UUID> openStashes = new ConcurrentHashMap<>();

    public StashUI(Dung plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stashes.yml");
        load();
        startReminderTask();
    }

    // ---------- persistence ----------

    public void load() {
        if (!file.exists()) return;
        try {
            data.load(file);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                File corrupt = new File(file.getParentFile(),
                        file.getName() + ".corrupt-" + System.currentTimeMillis());
                if (file.renameTo(corrupt)) {
                    System.out.println("[Dung] Corrupt stash save backed up to " + corrupt.getName());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return;
        }
        stashes.clear();
        for (String key : data.getKeys(false)) {
            try {
                UUID uid = UUID.fromString(key);
                List<ItemStack> items = new ArrayList<>();
                for (String encoded : data.getStringList(key)) {
                    ItemStack s = decode(encoded);
                    if (s != null) items.add(s);
                }
                stashes.put(uid, items);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        try {
            for (Map.Entry<UUID, List<ItemStack>> e : stashes.entrySet()) {
                List<String> encoded = new ArrayList<>();
                for (ItemStack s : e.getValue()) encoded.add(encode(s));
                data.set(e.getKey().toString(), encoded);
            }
            file.getParentFile().mkdirs();
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.write(tmp.toPath(), data.saveToString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String encode(ItemStack s) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BukkitObjectOutputStream oos = new BukkitObjectOutputStream(out);
            oos.writeObject(s);
            oos.flush();
            oos.close();
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ItemStack decode(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(b64));
            BukkitObjectInputStream ois = new BukkitObjectInputStream(in);
            Object o = ois.readObject();
            ois.close();
            return o instanceof ItemStack s ? s : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ---------- stash access ----------

    public List<ItemStack> items(UUID id) {
        return stashes.computeIfAbsent(id, k -> new ArrayList<>());
    }

    public int count(Player p) {
        return items(p.getUniqueId()).size();
    }

    /** Store a broken armor piece in the player's stash. If the stash is full, drops it instead. */
    public boolean store(Player p, ItemStack item) {
        List<ItemStack> list = items(p.getUniqueId());
        if (list.size() >= STASH_SIZE) {
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands(
                    com.lieyabull.dung.lang.Lang.forPlayer(p, "stash.fullDropped", itemName(item))));
            p.getWorld().dropItemNaturally(p.getLocation().add(0, 0.5, 0), item);
            return false;
        }
        list.add(item.clone());
        save();
        p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands(
                com.lieyabull.dung.lang.Lang.forPlayer(p, "stash.stashed", itemName(item))));
        return true;
    }

    private static String itemName(ItemStack item) {
        return item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : item.getType().name();
    }

    /** Place an item into the first free main-inventory slot (0-35). If the bag is full, store it
     *  in the player's stash instead of dropping it on the ground. */
    public static void placeOrStash(Player p, ItemStack item) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) {
                inv.setItem(i, item);
                return;
            }
        }
        Dung.instance().stashUI().store(p, item);
    }

    // ---------- GUI ----------

    /** Open the takeout-only stash GUI. */
    public void open(Player p) {
        List<ItemStack> list = items(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, STASH_SIZE,
                LEGACY.deserialize(com.lieyabull.dung.lang.Lang.forPlayer(p, "stash.title", list.size(), STASH_SIZE)));
        for (int i = 0; i < Math.min(list.size(), STASH_SIZE); i++) {
            ItemStack shown = list.get(i).clone();
            // Stash is reachable outside dungeons (lobby/plots), so translate any gear to the
            // viewer's current language the moment it's displayed.
            if (com.lieyabull.dung.items.GearFactory.isGear(shown)) {
                com.lieyabull.dung.items.GearFactory.localizeFor(shown, p);
            }
            inv.setItem(i, shown);
        }
        openStashes.put(inv, p.getUniqueId());
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        UUID owner = openStashes.get(e.getInventory());
        if (owner == null) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // Clicking inside the stash: take the whole stack out (takeout-only), or cancel if the
        // click would deposit something (empty slot clicked with a cursor item).
        if (e.getClickedInventory() == e.getInventory()) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
                e.setCancelled(true); // deposit attempt
                return;
            }
            e.setCancelled(true);
            takeOut(p, owner, e.getSlot());
            return;
        }
        // Clicking in the player inventory while the stash is open: a shift-click would move the
        // item INTO the stash, which is forbidden — the stash is takeout-only.
        if (e.isShiftClick()) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (openStashes.containsKey(e.getInventory())) {
            // Cancel any drag that touches stash slots (raw slots 0..STASH_SIZE-1) so items can
            // never be deposited into the takeout-only container.
            if (e.getRawSlots().stream().anyMatch(rs -> rs < STASH_SIZE)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        openStashes.remove(e.getInventory());
    }

    /** Move a stash item into the player's inventory, removing it from the stash. */
    private void takeOut(Player p, UUID owner, int slot) {
        List<ItemStack> list = items(owner);
        if (slot < 0 || slot >= list.size()) return;
        ItemStack item = list.get(slot);
        java.util.HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(item);
        if (leftover.isEmpty()) {
            list.remove(slot);
            save();
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "stash.took", itemName(item)));
            // Refresh the open GUI in place so the taken slot visibly empties.
            Inventory inv = openStashes.entrySet().stream()
                    .filter(en -> en.getValue().equals(owner))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (inv != null && slot < inv.getSize()) {
                inv.setItem(slot, null);
            }
        } else {
            p.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(p, "stash.full"));
        }
    }

    // ---------- reminder ----------

    /** Remind players with a non-empty stash every 5 minutes: how many items are waiting, with a
     *  clickable prompt to open /stash. */
    private void startReminderTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                int c = count(p);
                if (c <= 0) continue;
                ChatUI.notify(p, com.lieyabull.dung.lang.Lang.forPlayer(p,
                        c == 1 ? "stash.reminder.one" : "stash.reminder.many", c));
            }
        }, REMINDER_TICKS, REMINDER_TICKS);
    }
}