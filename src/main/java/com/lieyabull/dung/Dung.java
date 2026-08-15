package com.lieyabull.dung;

import com.lieyabull.dung.command.DungCommand;
import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.listener.GameListener;
import com.lieyabull.dung.ui.ShopUI;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Dung extends JavaPlugin {
    private static Dung instance;
    private GameManager game;
    private MetaManager meta;
    private ShopUI shopUI;
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
        Bukkit.getPluginManager().registerEvents(new GameListener(this), this);
        Bukkit.getPluginManager().registerEvents(shopUI, this);
        getCommand("dung").setExecutor(new DungCommand(this));
        getCommand("dungeon").setExecutor(new DungCommand(this));
        getCommand("shop").setExecutor(new DungCommand(this));
        getCommand("upgrades").setExecutor(new DungCommand(this));
        getCommand("salvage").setExecutor(new DungCommand(this));
        getCommand("party").setExecutor(new DungCommand(this));
        getLogger().info("Dung enabled. World resolved lazily.");
    }

    @Override
    public void onDisable() {
        if (game != null) game.shutdown();
        if (meta != null) meta.save();
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
}