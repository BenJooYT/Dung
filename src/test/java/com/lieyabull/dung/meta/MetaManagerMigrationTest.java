package com.lieyabull.dung.meta;

import com.lieyabull.dung.meta.MetaManager.MetaProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests for {@link MetaManager#migrateOfflineLogin} — no Bukkit server dependencies. */
public class MetaManagerMigrationTest {

    private static final String CRACKED_YML =
            "# cracked (offline, name-based v3 UUID) BSmerrf\n"
            + "a7032eb3-41d3-3d6c-a5a8-6bd5c5bb9922:\n"
            + "  name: BSmerrf\n"
            + "  coins: 90\n"
            + "  deaths: 2\n"
            + "  clears: 5\n"
            + "  class: warrior\n"
            + "  kills: 14\n"
            + "  bestsFloor: 4\n"
            + "  shards: 3\n"
            + "  tutorial: true\n"
            + "  lobbyEditNotified: false\n"
            + "  language: en\n"
            + "  upgrades:\n"
            + "    damage: 1\n";

    private static final String PREMIUM_YML =
            "# premium (real Mojang, v4 UUID) BSmerrf\n"
            + "5315907f-8d73-468d-94bf-0de754474bac:\n"
            + "  coins: 403\n"
            + "  deaths: 65\n"
            + "  clears: 206\n"
            + "  class: ranger\n"
            + "  kills: 3033\n"
            + "  bestsFloor: 10\n"
            + "  shards: 70\n"
            + "  tutorial: true\n"
            + "  upgrades:\n"
            + "    hearts: 15\n"
            + "    defense: 12\n"
            + "    mana: 12\n"
            + "    speed: 9\n"
            + "    crit: 10\n"
            + "    damage: 15\n"
            + "    magic_damage: 10\n"
            + "  lastWorld: dung_plots\n"
            + "  lastX: 8.83\n"
            + "  lastY: 52.0\n"
            + "  lastZ: 15.77\n"
            + "  name: BSmerrf\n"
            + "  lobbyEditNotified: true\n"
            + "  language: en\n";

    private static final UUID PREMIUM = UUID.fromString("5315907f-8d73-468d-94bf-0de754474bac");
    private static final UUID OFFLINE = UUID.fromString("a7032eb3-41d3-3d6c-a5a8-6bd5c5bb9922");

    private File writeSave(@TempDir File dir, String content) throws Exception {
        File f = new File(dir, "saves.yml");
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    void migratesRicherPremiumProfileOntoOfflineLogin(@TempDir File dir) throws Exception {
        File f = writeSave(dir, PREMIUM_YML + CRACKED_YML);
        MetaManager mm = new MetaManager(f);
        mm.load();

        assertEquals(PREMIUM, mm.migrateOfflineLogin(OFFLINE, "BSmerrf"));

        // The offline login now sees the premium progression.
        MetaProfile p = mm.profile(OFFLINE);
        assertEquals(403, p.persistentCoins);
        assertEquals(206, p.clears);
        assertEquals(65, p.deaths);
        assertEquals("ranger", p.classId);
        assertEquals(70, p.shards);
        assertEquals(15, p.upgrades.get("damage"));
        assertEquals(9, p.upgrades.get("speed"));
        assertEquals("dung_plots", p.lastWorld);
        // The stale premium entry is gone.
        assertFalse(mm.allProfiles().containsKey(PREMIUM));
    }

    @Test
    void migrationPersistsToDisk(@TempDir File dir) throws Exception {
        File f = writeSave(dir, PREMIUM_YML + CRACKED_YML);
        MetaManager mm = new MetaManager(f);
        mm.load();
        mm.migrateOfflineLogin(OFFLINE, "BSmerrf");

        // A fresh instance starting from the same file must see the migrated data.
        MetaManager reloaded = new MetaManager(f);
        reloaded.load();
        MetaProfile p = reloaded.profile(OFFLINE);
        assertEquals(403, p.persistentCoins);
        assertEquals("ranger", p.classId);
        assertEquals(70, p.shards);
        assertFalse(reloaded.allProfiles().containsKey(PREMIUM));
    }

    @Test
    void idempotentAfterFirstMigration(@TempDir File dir) throws Exception {
        File f = writeSave(dir, PREMIUM_YML + CRACKED_YML);
        MetaManager mm = new MetaManager(f);
        mm.load();
        assertEquals(PREMIUM, mm.migrateOfflineLogin(OFFLINE, "BSmerrf"));
        assertNull(mm.migrateOfflineLogin(OFFLINE, "BSmerrf"));
    }

    @Test
    void noMigrationForFreshOfflinePlayerWithoutPremiumTwin(@TempDir File dir) throws Exception {
        File f = writeSave(dir, CRACKED_YML);
        MetaManager mm = new MetaManager(f);
        mm.load();
        assertNull(mm.migrateOfflineLogin(OFFLINE, "BSmerrf"));
        // Unrelated higher-scored profile under a different name is not a migration source.
        assertEquals(90, mm.profile(OFFLINE).persistentCoins);
    }

    @Test
    void noMigrationWhenOfflineProfileIsRicher(@TempDir File dir) throws Exception {
        // The offline account already out-progressed the premium twin — keep the offline account.
        File f = writeSave(dir,
                "a7032eb3-41d3-3d6c-a5a8-6bd5c5bb9922:\n"
                + "  name: BSmerrf\n"
                + "  coins: 9000\n"
                + "  clears: 500\n"
                + "  kills: 10000\n"
                + "  shards: 500\n"
                + "  class: warrior\n"
                + "  language: en\n"
                + PREMIUM_YML);
        MetaManager mm = new MetaManager(f);
        mm.load();
        assertNull(mm.migrateOfflineLogin(OFFLINE, "BSmerrf"));
    }

    @Test
    void vanillaUuidLoginDoesNotMigrate(@TempDir File dir) throws Exception {
        File f = writeSave(dir, PREMIUM_YML + CRACKED_YML);
        MetaManager mm = new MetaManager(f);
        mm.load();
        // A premium (v4) login — online-mode server or a non-offline identity — must not migrate.
        assertNull(mm.migrateOfflineLogin(PREMIUM, "BSmerrf"));
    }
}