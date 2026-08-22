package com.lieyabull.dung.shop;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.WorkstationRules;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.ui.StashUI;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists pending persistent-shop roll results to disk so a paid-for item is never lost across a
 * disconnect or server restart. A result is written the moment the currency is charged and removed
 * only once the player resolves it via KEEP or SALVAGE — guaranteeing no loss and no duplication.
 */
public final class ShopPendingStore {
    private final Dung plugin;
    private final File file;
    private final YamlConfiguration data = new YamlConfiguration();
    private final Map<UUID, ServerSideRollResult> pending = new ConcurrentHashMap<>();

    public ShopPendingStore(Dung plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending_shop_results.yml");
        load();
    }

    public void put(UUID id, ServerSideRollResult result) {
        pending.put(id, result);
        save();
    }

    public ServerSideRollResult get(UUID id) {
        return pending.get(id);
    }

    public ServerSideRollResult remove(UUID id) {
        ServerSideRollResult r = pending.remove(id);
        if (r != null) save();
        return r;
    }

    private void load() {
        if (!file.exists()) return;
        try {
            data.load(file);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        pending.clear();
        for (String key : data.getKeys(false)) {
            try {
                UUID uid = UUID.fromString(key);
                Category category = Category.valueOf(data.getString(key + ".category", ""));
                String itemB64 = data.getString(key + ".item");
                var item = StashUI.decode(itemB64);
                if (item == null) continue;
                var rarity = GearFactory.getRarity(item);
                int salvage = ShopRules.salvageValue(rarity, WorkstationRules.primaryStat(item));
                pending.put(uid, new ServerSideRollResult(item, rarity, category, salvage));
            } catch (Exception ignored) {
            }
        }
    }

    private void save() {
        try {
            for (String k : data.getKeys(false)) data.set(k, null);
            for (Map.Entry<UUID, ServerSideRollResult> e : pending.entrySet()) {
                String key = e.getKey().toString();
                data.set(key + ".category", e.getValue().category.name());
                data.set(key + ".item", StashUI.encode(e.getValue().item));
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