package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.lang.Lang;
import com.lieyabull.dung.lang.Language;
import com.lieyabull.dung.meta.MetaManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /language} — let a player pick their UI language. Choices are stored per player via
 * {@link MetaManager} (persisted across restarts) and exposed to the rest of the plugin through
 * {@link Lang#forPlayer} / {@link Lang#languageOf}.
 */
public final class LanguageCommand implements CommandExecutor, TabCompleter {
    private final Dung plugin;

    public LanguageCommand(Dung plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Lang.get(Language.defaultLang(), "language.noConsole"));
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(Lang.forPlayer(p, "language.current",
                    Lang.languageOf(p).displayName));
            p.sendMessage(Lang.forPlayer(p, "language.available", available()));
            return true;
        }
        Language lang = Language.parse(args[0]);
        if (lang == null) {
            p.sendMessage(Lang.forPlayer(p, "language.unknown", args[0]));
            p.sendMessage(Lang.forPlayer(p, "language.available", available()));
            return true;
        }
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        prof.language = lang.code;
        plugin.meta().save(); // persist immediately so a crash/restart can't roll the choice back
        // Re-localize gear the player already holds and stash contents so the new language shows
        // immediately instead of only on the next acquire.
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && com.lieyabull.dung.items.GearFactory.isGear(s)) {
                com.lieyabull.dung.items.GearFactory.localizeLore(s, lang);
            }
        }
        for (ItemStack s : p.getInventory().getArmorContents()) {
            if (s != null && com.lieyabull.dung.items.GearFactory.isGear(s)) {
                com.lieyabull.dung.items.GearFactory.localizeLore(s, lang);
            }
        }
        for (ItemStack s : plugin.stashUI().items(p.getUniqueId())) {
            if (s != null && com.lieyabull.dung.items.GearFactory.isGear(s)) {
                com.lieyabull.dung.items.GearFactory.localizeLore(s, lang);
            }
        }
        // Repaint the HUD/sidebar and tab menu if the player is mid-run, so the new language shows
        // immediately instead of on the next floor change.
        com.lieyabull.dung.game.DungeonInstance di = plugin.game().instanceOf(p);
        if (di != null) di.repaintPlayer(p);
        p.sendMessage(Lang.get(lang, "language.set", lang.displayName));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length != 1) return List.of();
        String q = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Language lang : Language.values()) {
            if (lang.name().toLowerCase(Locale.ROOT).startsWith(q)) out.add(lang.name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** Human-readable list of every available language, e.g. "English, Magyar". */
    private static String available() {
        List<String> names = new ArrayList<>();
        for (Language lang : Language.values()) names.add(lang.displayName);
        return String.join(", ", names);
    }
}
