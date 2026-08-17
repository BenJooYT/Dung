package com.lieyabull.dung;

import com.lieyabull.dung.command.DungCommand;
import com.lieyabull.dung.command.PlotCommand;
import com.lieyabull.dung.command.RoomCommand;
import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.listener.GameListener;
import com.lieyabull.dung.listener.PlotListener;
import com.lieyabull.dung.plot.PlotManager;
import com.lieyabull.dung.room.RoomEditor;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.room.RoomTutorial;
import com.lieyabull.dung.ui.ShopUI;
import com.lieyabull.dung.ui.PersistUI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Dung extends JavaPlugin {
    private static Dung instance;
    private GameManager game;
    private MetaManager meta;
    private ShopUI shopUI;
    private PersistUI persistUI;
    private PlotManager plotManager;
    private RoomEditor roomEditor;
    private RoomTutorial roomTutorial;
    private World world;

    public static Dung instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        meta = new MetaManager(new File(getDataFolder(), "saves.yml"));
        meta.load();
        game = new GameManager(this);
        shopUI = new ShopUI(this);
        persistUI = new PersistUI(this);
        plotManager = new PlotManager(this);
        roomEditor = new RoomEditor(this);
        roomTutorial = new RoomTutorial(this, roomEditor);
        Bukkit.getPluginManager().registerEvents(roomTutorial, this);
        Bukkit.getPluginManager().registerEvents(new GameListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlotListener(this), this);
        Bukkit.getPluginManager().registerEvents(shopUI, this);
        Bukkit.getPluginManager().registerEvents(persistUI, this);
        getCommand("dung").setExecutor(new DungCommand(this));
        getCommand("dungeon").setExecutor(new DungCommand(this));
        getCommand("shop").setExecutor(new DungCommand(this));
        getCommand("upgrades").setExecutor(new DungCommand(this));
        getCommand("salvage").setExecutor(new DungCommand(this));
        getCommand("party").setExecutor(new DungCommand(this));
        RoomCommand roomCmd = new RoomCommand(this, roomEditor);
        getCommand("room").setExecutor(roomCmd);
        getCommand("room").setTabCompleter(roomCmd);
        PlotCommand plotCmd = new PlotCommand(this);
        getCommand("plots").setExecutor(plotCmd);
        getCommand("plots").setTabCompleter(plotCmd);
        getCommand("plot").setExecutor(plotCmd);
        getCommand("plot").setTabCompleter(plotCmd);
        // Migrate existing persistent items to have UUIDs (for pre-UUID items)
        migratePersistentItemUuids();
        getLogger().info("Dung enabled. World resolved lazily.");
    }

    @Override
    public void onDisable() {
        if (game != null) game.shutdown();
        if (meta != null) meta.save();
        if (plotManager != null) plotManager.save();
        instance = null;
    }

    /** Lazy world resolution (avoids NPE during onEnable when worlds are not yet loaded). */
    public World world() {
        if (world == null || !world.isChunkLoaded(world.getSpawnLocation().getChunk())) {
            world = resolveWorld();
        }
        return world;
    }

    private World resolveWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (!w.getEnvironment().equals(World.Environment.THE_END)) return w;
        }
        return Bukkit.getWorlds().get(0);
    }

    public GameManager game() {
        return game;
    }

    public MetaManager meta() {
        return meta;
    }

    public ShopUI shopUI() {
        return shopUI;
    }

    public PersistUI persistUI() {
        return persistUI;
    }

    public PlotManager plotManager() {
        return plotManager;
    }

    public RoomEditor roomEditor() {
        return roomEditor;
    }

    public RoomTutorial roomTutorial() {
        return roomTutorial;
    }

    /** Scan all online players' inventories and assign UUIDs to persistent items
     *  that don't already have one. This migrates pre-UUID persistent items so the
     *  UUID-based matching system works for all existing gear. */
    public void migratePersistentItemUuids() {
        int migrated = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ItemStack s : p.getInventory().getContents()) {
                if (s != null && GearFactory.isPersistent(s)) {
                    GearFactory.assignUuidIfMissing(s);
                    migrated++;
                }
            }
            for (ItemStack s : p.getInventory().getArmorContents()) {
                if (s != null && GearFactory.isPersistent(s)) {
                    GearFactory.assignUuidIfMissing(s);
                    migrated++;
                }
            }
            ItemStack off = p.getInventory().getItemInOffHand();
            if (off != null && GearFactory.isPersistent(off)) {
                GearFactory.assignUuidIfMissing(off);
                migrated++;
            }
        }
        if (migrated > 0) {
            getLogger().info("Migrated " + migrated + " persistent item(s) with UUIDs.");
        }
    }
}