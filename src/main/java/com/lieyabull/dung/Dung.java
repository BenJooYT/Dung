package com.lieyabull.dung;

import com.lieyabull.dung.command.DummyCommand;
import com.lieyabull.dung.command.DungCommand;
import com.lieyabull.dung.command.PlotCommand;
import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.listener.GameListener;
import com.lieyabull.dung.listener.PlotListener;
import com.lieyabull.dung.plot.PlotManager;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.compost.CompostManager;
import com.lieyabull.dung.dummy.DummyManager;
import com.lieyabull.dung.listener.CompostListener;
import com.lieyabull.dung.structure.StructureManager;
import com.lieyabull.dung.ui.ShopUI;
import com.lieyabull.dung.ui.StashUI;
import com.lieyabull.dung.ui.WorkstationUI;
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
    private StashUI stashUI;
    private WorkstationUI workstationUI;
    private PlotManager plotManager;
    private StructureManager structureManager;
    private CompostManager compost;
    private DummyManager dummyManager;
    private World world;
    private com.lieyabull.dung.world.WorldManager worldManager;

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
        stashUI = new StashUI(this);
        workstationUI = new WorkstationUI(this);
        plotManager = new PlotManager(this);
        structureManager = new StructureManager(this);
        compost = new CompostManager(this);
        dummyManager = new DummyManager(this);
        Bukkit.getPluginManager().registerEvents(dummyManager, this);
        Bukkit.getPluginManager().registerEvents(new com.lieyabull.dung.listener.LobbyListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GameListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlotListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CompostListener(this), this);
        Bukkit.getPluginManager().registerEvents(shopUI, this);
        Bukkit.getPluginManager().registerEvents(stashUI, this);
        Bukkit.getPluginManager().registerEvents(workstationUI, this);
        getCommand("dung").setExecutor(new DungCommand(this));
        getCommand("dungeon").setExecutor(new DungCommand(this));
        getCommand("shop").setExecutor(new DungCommand(this));
        getCommand("upgrades").setExecutor(new DungCommand(this));
        getCommand("salvage").setExecutor(new DungCommand(this));
        getCommand("stash").setExecutor(new DungCommand(this));
        getCommand("party").setExecutor(new DungCommand(this));
        getCommand("balance").setExecutor(new DungCommand(this));
        getCommand("leaderboard").setExecutor(new DungCommand(this));
        // Autofill for all DungCommand-backed commands (dung, dungeon, shop, upgrades, salvage,
        // party, balance, leaderboard) so players can see the available arguments.
        DungCommand dungCmd = new DungCommand(this);
        for (String name : new String[]{"dung", "dungeon", "shop", "upgrades", "salvage", "stash", "party", "balance", "leaderboard"}) {
            getCommand(name).setExecutor(dungCmd);
            getCommand(name).setTabCompleter(dungCmd);
        }
        PlotCommand plotCmd = new PlotCommand(this);
        getCommand("plots").setExecutor(plotCmd);
        getCommand("plots").setTabCompleter(plotCmd);
        getCommand("plot").setExecutor(plotCmd);
        getCommand("plot").setTabCompleter(plotCmd);
        DummyCommand dummyCmd = new DummyCommand(this);
        getCommand("dummy").setExecutor(dummyCmd);
        getCommand("dummy").setTabCompleter(dummyCmd);
        getCommand("setlobby").setExecutor(new com.lieyabull.dung.command.SetLobbyCommand(this));
        // Dummies need loaded worlds to respawn — load + spawn 1 tick after enable.
        Bukkit.getScheduler().runTask(this, () -> dummyManager.loadAll());
        // Migrate existing persistent items to have UUIDs (for pre-UUID items)
        migratePersistentItemUuids();
        getLogger().info("Dung enabled. World resolved lazily.");
    }

    @Override
    public void onDisable() {
        if (game != null) game.shutdown();
        if (meta != null) meta.save();
        if (stashUI != null) stashUI.save();
        if (plotManager != null) plotManager.save();
        if (compost != null) compost.save();
        if (dummyManager != null) dummyManager.shutdown();
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

    public StashUI stashUI() {
        return stashUI;
    }

    public WorkstationUI workstationUI() {
        return workstationUI;
    }

    public PlotManager plotManager() {
        return plotManager;
    }

    public StructureManager structures() {
        return structureManager;
    }

    public CompostManager compost() {
        return compost;
    }

    public DummyManager dummyManager() {
        return dummyManager;
    }

    public com.lieyabull.dung.world.WorldManager worldManager() {
        if (worldManager == null) worldManager = new com.lieyabull.dung.world.WorldManager(this);
        return worldManager;
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