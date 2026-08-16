package com.lieyabull.dung.room;

import com.lieyabull.dung.Dung;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Walk-through tutorial for the room editor. Guides a player step by step through building,
 * capturing, validating, exporting, and testing a room template. Can be triggered as many times
 * as the player wants with {@code /room tutorial}.
 *
 * <p>Each step shows instructions and optionally auto-advances when the player performs the
 * expected action (e.g. running the right command, moving to the editor world). The player can
 * also advance manually with {@code /room tutorial next} or skip back with {@code /room tutorial back}.
 */
public final class RoomTutorial implements Listener {

    private static final String PREFIX = "§9[§fTutorial§9]§7 ";

    /** All tutorial steps in order. */
    private static final Step[] STEPS = {
        new Step(
            "Welcome",
            "Welcome to the §9room editor§7! This tutorial will walk you through creating a " +
            "room template from scratch — building it, capturing it, validating it, and exporting it.\n" +
            PREFIX + "Type §f/room tutorial next§7 to begin, or §f/room tutorial skip§7 to jump ahead."
        ),
        new Step(
            "Open the editor",
            "First, teleport to the editor world where you'll build your room.\n" +
            PREFIX + "Run: §f/room open"
        ),
        new Step(
            "Create a new room",
            "Now create a new room template. Give it an id (like §fmyroom§7) and at least one type " +
            "(like §fCOMBAT§7, §fTREASURE§7, §fELITE§7, etc.).\n" +
            PREFIX + "Run: §f/room new myroom COMBAT"
        ),
        new Step(
            "Build the room",
            "Use your building blocks to construct a room! Make walls, a floor, a ceiling, and a " +
            "doorway opening. You can use any blocks you like.\n" +
            PREFIX + "When you're done building, type §f/room tutorial next§7 to continue."
        ),
        new Step(
            "Set position 1",
            "Now we'll define the room's bounds. Stand at one corner of your room and mark it.\n" +
            PREFIX + "Run: §f/room pos1"
        ),
        new Step(
            "Set position 2",
            "Now stand at the opposite corner (diagonally across) and mark it.\n" +
            PREFIX + "Run: §f/room pos2"
        ),
        new Step(
            "Add the region",
            "Good! Now add the selection as a bound region. This tells the editor what volume " +
            "your room occupies.\n" +
            PREFIX + "Run: §f/room region"
        ),
        new Step(
            "Add a connection",
            "Rooms need doorways to connect to other rooms. Stand inside your doorway opening " +
            "and add a connection facing outward.\n" +
            PREFIX + "Stand in your doorway and run: §f/room conn n DOOR 3 3\n" +
            "§7(Use §fn§7/e§7/s§7/w§7 for the direction your doorway faces.)"
        ),
        new Step(
            "Set player spawn",
            "Players need a place to appear when they enter the room. Stand where you want " +
            "players to spawn and mark it.\n" +
            PREFIX + "Run: §f/room playerspawn"
        ),
        new Step(
            "Add a spawn floor",
            "Enemies need a place to spawn. Select the floor area where enemies should appear " +
            "(use §f/room pos1§7 and §f/room pos2§7 to select it), then add it as a spawn floor.\n" +
            PREFIX + "First: §f/room pos1§7 and §f/room pos2§7 to select the floor area\n" +
            PREFIX + "Then: §f/room spawnfloor"
        ),
        new Step(
            "Capture the blocks",
            "Now capture the block structure! This snapshots every block inside your bound " +
            "regions into the template.\n" +
            PREFIX + "Run: §f/room capture"
        ),
        new Step(
            "Validate the room",
            "Let's check that your room is valid — all blocks must be inside bounds, " +
            "connections must be open, and the player spawn must be safe.\n" +
            PREFIX + "Run: §f/room validate"
        ),
        new Step(
            "Export the room",
            "Your room is valid! Now export it as a JSON asset file. This writes the template " +
            "to the plugin's rooms folder so it can be integrated into the game.\n" +
            PREFIX + "Run: §f/room export myroom"
        ),
        new Step(
            "Test the room",
            "Finally, test your room! The tester will instantiate it in a test pad, verify " +
            "connections are open, check the spawn is safe, and spawn a test enemy.\n" +
            PREFIX + "Run: §f/room testlocal"
        ),
        new Step(
            "Congratulations!",
            "You've completed the room editor tutorial! Here's what you learned:\n" +
            "§71. §f/room open§7 — teleport to the editor world\n" +
            "§72. §f/room new <id> [types]§7 — create a new template\n" +
            "§73. §f/room pos1§7 / §f/room pos2§7 — select a region\n" +
            "§74. §f/room region§7 — add a bound cuboid\n" +
            "§75. §f/room conn <dir> [type] [w] [h]§7 — add a doorway\n" +
            "§76. §f/room playerspawn§7 — set the spawn point\n" +
            "§77. §f/room spawnfloor§7 — add an enemy spawn area\n" +
            "§78. §f/room capture§7 — snapshot blocks into the template\n" +
            "§79. §f/room validate§7 — check for errors\n" +
            "§710. §f/room export <id>§7 — write the asset file\n" +
            "§711. §f/room testlocal§7 — test the room in-game\n\n" +
            PREFIX + "You can run this tutorial again anytime with §f/room tutorial§7."
        )
    };

    private final Dung plugin;
    private final RoomEditor editor;
    private final Map<UUID, Integer> playerSteps = new HashMap<>();

    public RoomTutorial(Dung plugin, RoomEditor editor) {
        this.plugin = plugin;
        this.editor = editor;
    }

    // ---------- public API ----------

    /** Start or resume the tutorial for a player. */
    public void start(Player p) {
        int step = playerSteps.getOrDefault(p.getUniqueId(), 0);
        showStep(p, step);
    }

    /** Advance to the next step. */
    public void next(Player p) {
        int cur = playerSteps.getOrDefault(p.getUniqueId(), 0);
        if (cur >= STEPS.length - 1) {
            p.sendMessage(PREFIX + "You've already completed the tutorial! Run §f/room tutorial§7 to see it again.");
            return;
        }
        playerSteps.put(p.getUniqueId(), cur + 1);
        showStep(p, cur + 1);
    }

    /** Go back one step. */
    public void back(Player p) {
        int cur = playerSteps.getOrDefault(p.getUniqueId(), 0);
        if (cur <= 0) {
            p.sendMessage(PREFIX + "You're at the first step already.");
            return;
        }
        playerSteps.put(p.getUniqueId(), cur - 1);
        showStep(p, cur - 1);
    }

    /** Skip to a specific step by number (1-based). */
    public void skipTo(Player p, int stepNum) {
        if (stepNum < 1 || stepNum > STEPS.length) {
            p.sendMessage("§cStep must be between 1 and " + STEPS.length + ".");
            return;
        }
        int idx = stepNum - 1;
        playerSteps.put(p.getUniqueId(), idx);
        showStep(p, idx);
    }

    /** Reset the tutorial for a player. */
    public void reset(Player p) {
        playerSteps.put(p.getUniqueId(), 0);
        showStep(p, 0);
    }

    /** Check if a player is currently in the tutorial. */
    public boolean isActive(Player p) {
        return playerSteps.containsKey(p.getUniqueId());
    }

    /** Get the current step index (0-based), or -1 if not in tutorial. */
    public int currentStep(Player p) {
        return playerSteps.getOrDefault(p.getUniqueId(), -1);
    }

    /** Total number of steps. */
    public int totalSteps() {
        return STEPS.length;
    }

    // ---------- event listeners for auto-advance ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        int step = playerSteps.getOrDefault(p.getUniqueId(), -1);
        if (step < 0) return;

        String msg = e.getMessage().toLowerCase().trim();

        // Step 1 (Open the editor): auto-advance on /room open
        if (step == 1 && msg.equals("/room open")) {
            scheduleAdvance(p, 2);
        }
        // Step 2 (Create a new room): auto-advance on /room new
        if (step == 2 && msg.startsWith("/room new")) {
            scheduleAdvance(p, 3);
        }
        // Step 4 (Set pos1): auto-advance on /room pos1
        if (step == 4 && msg.equals("/room pos1")) {
            scheduleAdvance(p, 5);
        }
        // Step 5 (Set pos2): auto-advance on /room pos2
        if (step == 5 && msg.equals("/room pos2")) {
            scheduleAdvance(p, 6);
        }
        // Step 6 (Add region): auto-advance on /room region
        if (step == 6 && msg.equals("/room region")) {
            scheduleAdvance(p, 7);
        }
        // Step 7 (Add connection): auto-advance on /room conn
        if (step == 7 && msg.startsWith("/room conn")) {
            scheduleAdvance(p, 8);
        }
        // Step 8 (Set player spawn): auto-advance on /room playerspawn
        if (step == 8 && msg.equals("/room playerspawn")) {
            scheduleAdvance(p, 9);
        }
        // Step 9 (Add spawn floor): auto-advance on /room spawnfloor
        if (step == 9 && msg.equals("/room spawnfloor")) {
            scheduleAdvance(p, 10);
        }
        // Step 10 (Capture): auto-advance on /room capture
        if (step == 10 && msg.equals("/room capture")) {
            scheduleAdvance(p, 11);
        }
        // Step 11 (Validate): auto-advance on /room validate
        if (step == 11 && msg.equals("/room validate")) {
            scheduleAdvance(p, 12);
        }
        // Step 12 (Export): auto-advance on /room export
        if (step == 12 && msg.startsWith("/room export")) {
            scheduleAdvance(p, 13);
        }
        // Step 13 (Test): auto-advance on /room testlocal
        if (step == 13 && msg.equals("/room testlocal")) {
            scheduleAdvance(p, 14);
        }
    }

    // ---------- internal ----------

    private void scheduleAdvance(Player p, int nextStep) {
        // Use a small delay so the command's own feedback messages arrive first
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            int cur = playerSteps.getOrDefault(p.getUniqueId(), -1);
            if (cur < 0) return;
            // Only advance if the player hasn't manually moved past this step
            if (cur < nextStep) {
                playerSteps.put(p.getUniqueId(), nextStep);
                showStep(p, nextStep);
            }
        }, 5L); // 5 ticks = ~0.25s
    }

    private void showStep(Player p, int idx) {
        if (idx < 0 || idx >= STEPS.length) return;
        Step s = STEPS[idx];
        p.sendMessage("");
        p.sendMessage("§9╔══════════════════════════════════════════╗");
        p.sendMessage("§9║ §fTutorial Step §e" + (idx + 1) + "/" + STEPS.length + "§f: " + s.title + "§9");
        p.sendMessage("§9╚══════════════════════════════════════════╝");
        p.sendMessage("");
        for (String line : s.instructions.split("\n")) {
            p.sendMessage(line);
        }
        p.sendMessage("");
        // Show navigation hints
        if (idx == 0) {
            p.sendMessage("§7Type §f/room tutorial next§7 to begin.");
        } else if (idx < STEPS.length - 1) {
            p.sendMessage("§7Type §f/room tutorial next§7 when done, §f/room tutorial back§7 to go back, or §f/room tutorial skip <n>§7 to jump to a step.");
        } else {
            p.sendMessage("§7Type §f/room tutorial§7 to replay the tutorial anytime.");
        }
    }

    // ---------- step data ----------

    private static final class Step {
        final String title;
        final String instructions;

        Step(String title, String instructions) {
            this.title = title;
            this.instructions = instructions;
        }
    }
}