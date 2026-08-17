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
        p.sendMessage(Component.text("Dung", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text(" — the dungeon awaits.", NamedTextColor.GRAY)));
        p.sendMessage(command("[ Start a run ]", "/dung start", "Begin a fresh run"));
        p.sendMessage(command("[ Shop ]", "/shop", "Open the shop GUI — spend persistent coins on gear"));
        p.sendMessage(command("[ Upgrades ]", "/upgrades", "Open the upgrades GUI — spend shards on permanent stat upgrades"));
        p.sendMessage(command("[ My Stats ]", "/dung stats", "View your meta-progression"));
        p.sendMessage(command("[ Help ]", "/dung help", "List commands"));
    }

    public static void notify(Player p, String msg) {
        // msg may carry legacy § codes (e.g. pickup text); Component.text() would throw
        // LegacyFormattingDetected, so parse them through the legacy serializer instead.
        p.sendMessage(LegacyComponentSerializer.legacySection().deserialize(msg));
    }

    public static void notify(Player p, Component c) {
        p.sendMessage(c);
    }

    public static Component command(String label, String command, String hover) {
        return LegacyComponentSerializer.legacySection().deserialize(label)
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand(command));
    }
}