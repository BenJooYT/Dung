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
import com.lieyabull.dung.listener.PotionListener;
import com.lieyabull.dung.plot.ProvenanceManager;
import com.lieyabull.dung.structure.StructureManager;
import com.lieyabull.dung.ui.ShopUI;
import com.lieyabull.dung.ui.StashUI;
import com.lieyabull.dung.ui.TrollUI;
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
    private ProvenanceManager provenanceManager;
    private PotionListener potionListener;
    private DummyManager dummyManager;
    private TrollUI trollUI;
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
        provenanceManager = new ProvenanceManager(this);
        potionListener = new PotionListener(this);
        dummyManager = new DummyManager(this);
        trollUI = new TrollUI();
        Bukkit.getPluginManager().registerEvents(dummyManager, this);
        Bukkit.getPluginManager().registerEvents(new com.lieyabull.dung.listener.TrollWandListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.lieyabull.dung.listener.LobbyListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GameListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlotListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CompostListener(this), this);
        Bukkit.getPluginManager().registerEvents(provenanceManager, this);
        Bukkit.getPluginManager().registerEvents(potionListener, this);
        Bukkit.getPluginManager().registerEvents(new com.lieyabull.dung.listener.ChatFormatListener(), this);
        Bukkit.getPluginManager().registerEvents(new com.lieyabull.dung.listener.GearLoreListener(), this);
        Bukkit.getPluginManager().registerEvents(shopUI, this);
        Bukkit.getPluginManager().registerEvents(stashUI, this);
        Bukkit.getPluginManager().registerEvents(workstationUI, this);
        Bukkit.getPluginManager().registerEvents(trollUI, this);
        com.lieyabull.dung.command.MetaCommand metaCmd = new com.lieyabull.dung.command.MetaCommand(this);
        DungCommand dungCmd = new DungCommand(this, metaCmd);
        // Core run commands
        getCommand("dung").setExecutor(dungCmd);
        getCommand("dungeon").setExecutor(dungCmd);
        getCommand("dung").setTabCompleter(dungCmd);
        getCommand("dungeon").setTabCompleter(dungCmd);
        // Non-dung commands (shop/upgrades/salvage/stash/party/balance/leaderboard)
        for (String name : new String[]{"shop", "upgrades", "salvage", "stash", "party", "balance", "leaderboard"}) {
            getCommand(name).setExecutor(metaCmd);
            getCommand(name).setTabCompleter(metaCmd);
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
        getCommand("lobby").setExecutor(new com.lieyabull.dung.command.LobbyCommand(this));
        getCommand("convert").setExecutor(plotCmd);
        getCommand("flyspeed").setExecutor(new com.lieyabull.dung.command.FlySpeedCommand(this));
        com.lieyabull.dung.command.LanguageCommand languageCmd =
                new com.lieyabull.dung.command.LanguageCommand(this);
        getCommand("language").setExecutor(languageCmd);
        getCommand("language").setTabCompleter(languageCmd);
        getCommand("troll").setExecutor(new com.lieyabull.dung.command.TrollCommand(this));
        // The lobby world is lazy-created — resolve it NOW so dummies living there exist when
        // loadAll runs (previously every restart silently skipped them as "unloaded world").
        worldManager().getLobby();
        // Dummies need loaded worlds to respawn — give the world a few ticks to finish init.
        Bukkit.getScheduler().runTaskLater(this, () -> dummyManager.loadAll(), 5L);
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

    public ProvenanceManager provenanceManager() {
        return provenanceManager;
    }

    public PotionListener potionListener() {
        return potionListener;
    }

    public DummyManager dummyManager() {
        return dummyManager;
    }

    public TrollUI trollUI() {
        return trollUI;
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