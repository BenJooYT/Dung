package com.lieyabull.dung.compost;

import com.lieyabull.dung.Dung;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom composter mechanic: dropping compostable material into a composter consumes it with the
 * same per-item chance-based RNG as vanilla (each item rolls its own compost chance), stashing any
 * leftovers inside. When the player takes the bone meal from a full composter, it keeps filling
 * from the leftovers.
 */
public final class CompostManager {

    /** Composter fill stages (COMPOST_LEVEL) run 0-7; level 8 is the "content ready" finished
     *  state where the bone meal is collectable. */
    public static final int MAX_LEVEL = 8;

    private final Dung plugin;
    private final File file;
    private final YamlConfiguration data = new YamlConfiguration();
    private final Map<String, State> states = new HashMap<>();

    public CompostManager(Dung plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "compost.yml");
        load();
    }

    /** Per-composter state: leftover material still inside, and whether a bone meal is ready. */
    public static final class State {
        public final List<ItemStack> buffer = new ArrayList<>();
        public boolean pendingBoneMeal;
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    /** Look up (creating if needed) the state for a composter block. */
    public State getState(Block b) {
        return states.computeIfAbsent(key(b), k -> new State());
    }

    public boolean isCompostable(ItemStack stack) {
        return stack != null && stack.getType().isCompostable();
    }

    private int level(Block b) {
        return b.getBlockData() instanceof Levelled l ? l.getLevel() : 0;
    }

    private void setLevel(Block b, int level) {
        if (b.getBlockData() instanceof Levelled l) {
            l.setLevel(level);
            b.setBlockData(l, false);
        }
    }

    /** Drop a stack of compostable material into the composter: stash it in the buffer, then fill
     *  the composter from the buffer up to full. */
    public void addAndFill(Block b, ItemStack stack) {
        State st = getState(b);
        for (ItemStack s : st.buffer) {
            if (s.isSimilar(stack)) {
                s.setAmount(s.getAmount() + stack.getAmount());
                fill(b, st);
                return;
            }
        }
        st.buffer.add(stack.clone());
        fill(b, st);
    }

    /** Consume buffer items, each rolling its vanilla compost chance to raise the level, up to
     *  the {@link #MAX_LEVEL} "content ready" stage. Once finished a bone meal is made ready and
     *  filling pauses until the player takes it. */
    private void fill(Block b, State st) {
        if (st.pendingBoneMeal) return; // finished, waiting to be emptied
        int lvl = level(b);
        while (lvl < MAX_LEVEL && !st.buffer.isEmpty()) {
            ItemStack head = st.buffer.get(0);
            float chance = head.getType().getCompostChance();
            head.setAmount(head.getAmount() - 1);
            if (head.getAmount() <= 0) st.buffer.remove(0);
            // Vanilla-style roll: the item may or may not raise the level.
            if (chance >= ThreadLocalRandom.current().nextDouble()) {
                lvl++;
            }
        }
        setLevel(b, lvl);
        if (lvl == MAX_LEVEL) st.pendingBoneMeal = true;
    }

    /** Give the player a bone meal from a full composter, then keep filling it from the buffer.
     *  @return true if a bone meal was collected. */
    public boolean collectBoneMeal(Player p, Block b) {
        State st = getState(b);
        if (!st.pendingBoneMeal) return false;
        st.pendingBoneMeal = false;
        setLevel(b, 0);
        ItemStack bone = new ItemStack(Material.BONE_MEAL, 1);
        Map<Integer, ItemStack> left = p.getInventory().addItem(bone);
        for (ItemStack r : left.values()) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 1.0, 0.5), r);
        }
        fill(b, st); // continue filling from whatever remains
        save();
        return true;
    }

    /** Drop any buffered material back out when a composter is destroyed so nothing is lost. */
    public void removeState(Block b) {
        State st = states.remove(key(b));
        if (st == null) return;
        for (ItemStack s : st.buffer) {
            if (s == null || s.getType() == Material.AIR) continue;
            int remaining = s.getAmount();
            while (remaining > 0) {
                int chunk = Math.min(remaining, s.getMaxStackSize());
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5),
                        new ItemStack(s.getType(), chunk));
                remaining -= chunk;
            }
        }
    }

    // ==================== PERSISTENCE ====================

    private void load() {
        if (!file.exists()) return;
        try {
            data.load(file);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        for (String key : data.getKeys(false)) {
            State st = new State();
            st.pendingBoneMeal = data.getBoolean(key + ".ready", false);
            List<?> items = data.getList(key + ".buffer");
            if (items != null) {
                for (Object o : items) {
                    if (o instanceof Map<?, ?> m) {
                        Object typeObj = m.get("type");
                        Object amtObj = m.get("amount");
                        if (typeObj == null || amtObj == null) continue;
                        Material mat = Material.matchMaterial(typeObj.toString());
                        if (mat == null) continue;
                        int amt = ((Number) amtObj).intValue();
                        if (amt > 0) st.buffer.add(new ItemStack(mat, amt));
                    } else if (o instanceof ItemStack s) { // legacy ItemStack format
                        st.buffer.add(s);
                    }
                }
            }
            states.put(key, st);
        }
    }

    public void save() {
        try {
            for (String k : data.getKeys(false)) data.set(k, null); // clear old keys
            for (Map.Entry<String, State> e : states.entrySet()) {
                String key = e.getKey();
                State st = e.getValue();
                data.set(key + ".ready", st.pendingBoneMeal);
                // Persist as {type, amount} pairs — Bukkit's ItemStack serialization rejects counts
                // above 99, but a composter buffer can hold arbitrarily many items.
                List<Map<String, Object>> buf = new ArrayList<>();
                for (ItemStack s : st.buffer) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("type", s.getType().name());
                    m.put("amount", s.getAmount());
                    buf.add(m);
                }
                data.set(key + ".buffer", buf);
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
}