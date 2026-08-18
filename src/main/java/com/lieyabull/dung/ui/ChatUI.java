package com.lieyabull.dung.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/** Clickable chat actions + notifications (command shortcuts without needing to type). */
public final class ChatUI {
    private ChatUI() {}

    public static void startPrompt(Player p) {
        p.sendMessage("");
        p.sendMessage(Component.text("═══════════════════════════", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("    Dung", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text(" — the dungeon awaits.", NamedTextColor.GRAY)));
        p.sendMessage(menuButton("[ Start a Run ]", NamedTextColor.GREEN, "/dung start", "Begin a fresh run"));
        p.sendMessage(menuButton("[ Shop ]", NamedTextColor.GOLD, "/shop", "Open the shop GUI — spend persistent coins on gear"));
        p.sendMessage(menuButton("[ Upgrades ]", NamedTextColor.AQUA, "/upgrades", "Open the upgrades GUI — spend shards on permanent stat upgrades"));
        p.sendMessage(menuButton("[ My Stats ]", NamedTextColor.YELLOW, "/dung stats", "View your meta-progression"));
        p.sendMessage(menuButton("[ Help ]", NamedTextColor.GRAY, "/dung help", "List commands"));
        p.sendMessage(Component.text("═══════════════════════════", NamedTextColor.DARK_GRAY));
    }

    /** A colored, bold, clickable menu button with hover help. */
    private static Component menuButton(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    public static void notify(Player p, String msg) {
        // msg may carry legacy § codes (e.g. pickup text); Component.text() would throw
        // LegacyFormattingDetected, so parse them through the legacy serializer instead.
        // Any recognized command in the message is made clickable to run.
        p.sendMessage(clickableCommands(msg));
    }

    public static void notify(Player p, Component c) {
        p.sendMessage(c);
    }

    /** Build a clickable command button from a legacy-format label (keeps any § codes in the label). */
    public static Component command(String label, String command, String hover) {
        return LegacyComponentSerializer.legacySection().deserialize(label)
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(hover)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    /** Commands the plugin mentions in chat that should be clickable (command + optional argument word). */
    private static final java.util.regex.Pattern COMMAND_PATTERN = java.util.regex.Pattern.compile(
            "/(?:shop(?:\\s+weapon)?|\\bupgrades|\\bsalvage(?:\\s+favorite)?|\\bplots"
            + "|\\bplot(?:\\s+(?:claim|home|name|warp|unclaim))?"
            + "|\\broom(?:\\s+[a-z]+)?"
            + "|\\bdung(?:\\s+(?:start|leave|descend|stats|help|give|create|invite|accept|decline|kick|disband|info|shieldswitch|favorite))?"
            + "|\\bparty(?:\\s+(?:create|invite|accept|decline|leave|kick|disband|info))?)");

    /** Turn a legacy-format message string into a Component where every recognized command is
     *  clickable (and hoverable) so players can run it instead of typing it out. */
    public static Component clickableCommands(String legacy) {
        LegacyComponentSerializer ser = LegacyComponentSerializer.legacySection();
        java.util.regex.Matcher m = COMMAND_PATTERN.matcher(legacy);
        Component result = Component.empty();
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                result = result.append(ser.deserialize(legacy.substring(last, m.start())));
            }
            String cmd = m.group();
            result = result.append(ser.deserialize(cmd)
                    .hoverEvent(HoverEvent.showText(Component.text("Click to run: " + cmd, NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand(cmd)));
            last = m.end();
        }
        if (last < legacy.length()) {
            result = result.append(ser.deserialize(legacy.substring(last)));
        }
        return result;
    }
}