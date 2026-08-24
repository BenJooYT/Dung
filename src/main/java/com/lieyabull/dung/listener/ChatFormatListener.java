package com.lieyabull.dung.listener;

import com.lieyabull.dung.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Lets players type ampersand color codes (&a) in chat and in command arguments; they are
 * translated to real section codes (§a) before the message is sent / the command runs.
 */
public final class ChatFormatListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plain.indexOf('&') < 0) return;
        String translated = TextUtil.translateAmp(plain);
        event.message(LegacyComponentSerializer.legacySection().deserialize(translated));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        int sp = msg.indexOf(' ');
        if (sp < 0) return;
        String args = msg.substring(sp);
        if (args.indexOf('&') < 0) return;
        event.setMessage(msg.substring(0, sp) + TextUtil.translateAmp(args));
    }
}
