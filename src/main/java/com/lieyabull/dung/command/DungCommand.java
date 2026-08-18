package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.GameManager;
import com.lieyabull.dung.game.Run;
import com.lieyabull.dung.game.WorkstationRules;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.items.ItemPool;
import com.lieyabull.dung.items.ItemTags;
import com.lieyabull.dung.items.Rarity;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.party.Party;
import com.lieyabull.dung.party.PartyManager;
import com.lieyabull.dung.ui.ChatUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import com.lieyabull.dung.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class DungCommand implements CommandExecutor {
    private final Dung plugin;

    public DungCommand(Dung plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Admin cleanup, also runnable from console to clear a stuck boss HP bar: /dung bossbar
        if ((label.equalsIgnoreCase("dung") || label.equalsIgnoreCase("dungeon"))
                && args.length > 0 && args[0].equalsIgnoreCase("bossbar")) {
            return bossbarCmd(sender, args);
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use Dung.");
            return true;
        }
        switch (label.toLowerCase()) {
            case "shop": return shopCmd(p, args);
            case "upgrades": return upgradesCmd(p, args);
            case "salvage": return salvageCmd(p, args);
            case "party": return partyCmd(p, args);
            case "balance": balance(p, args); return true;
            case "leaderboard": leaderboard(p, args); return true;
            default: return dungCmd(p, args);
        }
    }

    /** Clear any leaked boss HP bars (keyed `dung_boss_*`). Runnable from console to fix a stuck bar. */
    private boolean bossbarCmd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dung.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        int removed = 0;
        java.util.List<org.bukkit.boss.KeyedBossBar> stuck = new java.util.ArrayList<>();
        java.util.Iterator<org.bukkit.boss.KeyedBossBar> it = Bukkit.getBossBars();
        while (it.hasNext()) {
            org.bukkit.boss.KeyedBossBar bar = it.next();
            if (bar.getKey().getKey().startsWith("dung_boss_")) stuck.add(bar);
        }
        for (org.bukkit.boss.KeyedBossBar k : stuck) {
            k.removeAll();
            k.setVisible(false);
            Bukkit.removeBossBar(k.getKey());
            removed++;
        }
        sender.sendMessage("§aCleared " + removed + " stuck boss bar" + (removed == 1 ? "" : "s") + ".");
        return true;
    }

    /** Wipe all player data (saves.yml, plots.yml), turn off natural mob spawning, and
     *  broadcast the reset. Requires dung.admin permission. */
    private void resetCmd(Player p) {
        // End all active runs
        for (DungeonInstance di : plugin.game().instances()) {
            di.endRun();
        }
        // Clear all player data in MetaManager
        plugin.meta().clearAll();
        // Clear all plot data
        plugin.plotManager().clearAll();
        // Turn off natural mob spawning in all worlds
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            w.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
        }
        Bukkit.broadcastMessage("§c§lAll player data has been reset. Natural mob spawning disabled.");
    }

    // ---------- /dung <sub> ----------

    private boolean dungCmd(Player p, String[] args) {
        GameManager gm = plugin.game();
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "start":
                if (gm.isInInstance(p)) {
                    p.sendMessage("§cYou're already in a run. Use /dung leave first.");
                    return true;
                }
                // Check if player is in a party
                Party party = gm.partyManager().partyOf(p);
                if (party != null) {
                    // Party leader starts the run for the whole party
                    if (!party.isLeader(p.getUniqueId())) {
                        p.sendMessage("§cOnly the party leader can start a run.");
                        return true;
                    }
                    gm.startRun(party, System.nanoTime());
                    party.broadcast("§aRun started! Clear rooms, gear up, defeat the Warden.");
                } else {
                    // Solo: create a single-player party
                    party = gm.partyManager().createParty(p);
                    gm.startRun(party, System.nanoTime());
                    p.sendMessage("§aRun started. Clear rooms, gear up, defeat the Warden.");
                }
                return true;
            case "descend": {
                DungeonInstance di = gm.instanceOf(p);
                if (di == null) { p.sendMessage("§cStart a run first."); return true; }
                di.descend(p);
                return true;
            }
            case "shieldswitch": {
                DungeonInstance di = gm.instanceOf(p);
                if (di == null) { p.sendMessage("§cStart a run first."); return true; }
                di.doShieldSwitch(p);
                return true;
            }
            case "leave": {
                DungeonInstance di = gm.instanceOf(p);
                if (di != null) {
                    gm.leaveInstance(p);
                    p.sendMessage("§7Left the run.");
                } else {
                    p.sendMessage("§cNo active run.");
                }
                return true;
            }
            case "party": return partyCmd(p, args);
            case "shop": return shopCmd(p, args);
            case "upgrades": return upgradesCmd(p, args);
            case "salvage": return salvageCmd(p, args);
            case "balance": balance(p, args); return true;
            case "stats": stats(p); return true;
            case "class": classCmd(p, args); return true;
            case "give":
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                give(p, args);
                return true;
            case "stop":
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                Bukkit.broadcastMessage("§c§lServer is stopping...");
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.shutdown());
                return true;
            case "reset":
                if (!p.hasPermission("dung.admin")) { p.sendMessage("§cNo permission."); return true; }
                resetCmd(p);
                return true;
            case "help":
            default:
                ChatUI.startPrompt(p);
                return true;
        }
    }

    // ---------- /party ----------

    private boolean partyCmd(Player p, String[] args) {
        PartyManager pm = plugin.game().partyManager();
        GameManager gm = plugin.game();
        if (args.length == 0) {
            Party party = pm.partyOf(p);
            if (party == null) {
                p.sendMessage("§7You are not in a party. Use §f/party create§7 to start one.");
                p.sendMessage("§7Commands: §fcreate, invite <player>, accept, decline, leave, kick <player>, disband, info");
                return true;
            }
            p.sendMessage("§6--- Party ---");
            p.sendMessage("§7Leader: §f" + Bukkit.getOfflinePlayer(party.leader()).getName());
            p.sendMessage("§7Members (" + party.size() + "/" + Party.MAX_SIZE + "):");
            for (java.util.UUID uid : party.members()) {
                String name = Bukkit.getOfflinePlayer(uid).getName();
                String tag = uid.equals(party.leader()) ? " §6(Leader)" : "";
                p.sendMessage("  §7- §f" + name + tag);
            }
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create": {
                if (pm.partyOf(p) != null) {
                    p.sendMessage("§cYou're already in a party. Leave first.");
                    return true;
                }
                pm.createParty(p);
                p.sendMessage("§aParty created! Invite players with §f/party invite <player>");
                return true;
            }
            case "invite": {
                if (args.length < 2) { p.sendMessage("§cUsage: /party invite <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage("§cPlayer not found."); return true; }
                if (target.equals(p)) { p.sendMessage("§cYou can't invite yourself."); return true; }
                if (gm.isInInstance(p)) {
                    p.sendMessage("§cYou can't invite while your party is in a run.");
                    return true;
                }
                if (pm.invite(p, target)) {
                    p.sendMessage("§aInvited " + target.getName() + " to the party.");
                    target.sendMessage(
                            com.lieyabull.dung.ui.ChatUI.command("§a[Accept]", "/party accept", "Join the party")
                                    .append(Component.text("  "))
                                    .append(com.lieyabull.dung.ui.ChatUI.command("§c[Decline]", "/party decline", "Decline the invite"))
                                    .hoverEvent(null) // remove hover from the container
                    );
                    target.sendMessage("§a" + p.getName() + " invited you to a party!");
                } else {
                    p.sendMessage("§cCould not invite. They may already be in a party, or the party is full.");
                }
                return true;
            }
            case "accept": {
                if (gm.isInInstance(p)) {
                    p.sendMessage("§cYou can't join a party while you're in a run.");
                    return true;
                }
                UUID inviterId = pm.getInviter(p);
                Player inviter = inviterId != null ? Bukkit.getPlayer(inviterId) : null;
                if (inviter != null && gm.isInInstance(inviter)) {
                    p.sendMessage("§cThat party has already started a run.");
                    return true;
                }
                if (pm.acceptInvite(p)) {
                    p.sendMessage("§aYou joined the party!");
                } else {
                    p.sendMessage("§cNo pending invite or party is full.");
                }
                return true;
            }
            case "decline": {
                pm.declineInvite(p);
                p.sendMessage("§7Invite declined.");
                return true;
            }
            case "leave": {
                pm.leaveParty(p);
                DungeonInstance leaveDi = gm.instanceOf(p);
                if (leaveDi != null) leaveDi.removePlayer(p);
                p.sendMessage("§7You left the party.");
                return true;
            }
            case "kick": {
                if (args.length < 2) { p.sendMessage("§cUsage: /party kick <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage("§cPlayer not found."); return true; }
                if (pm.kick(p, target)) {
                    DungeonInstance kickDi = gm.instanceOf(target);
                    if (kickDi != null) kickDi.removePlayer(target);
                    p.sendMessage("§aKicked " + target.getName() + " from the party.");
                } else {
                    p.sendMessage("§cCould not kick. You may not be the leader.");
                }
                return true;
            }
            case "disband": {
                DungeonInstance disbandDi = gm.instanceOf(p);
                if (pm.disband(p)) {
                    if (disbandDi != null) disbandDi.endRun();
                    p.sendMessage("§cParty disbanded.");
                } else {
                    p.sendMessage("§cYou are not the party leader.");
                }
                return true;
            }
            default:
                p.sendMessage("§7Party commands: §fcreate, invite <player>, accept, decline, leave, kick <player>, disband, info");
                return true;
        }
    }

    // ---------- /shop ----------

    private boolean shopCmd(Player p, String[] args) {
        if (plugin.game().isInInstance(p)) {
            p.sendMessage("§cYou can't use /shop while inside a dungeon run. Leave with /dung leave first.");
            return true;
        }
        plugin.shopUI().openPersistentShop(p);
        return true;
    }

    // ---------- /upgrades ----------

    private boolean upgradesCmd(Player p, String[] args) {
        if (plugin.game().isInInstance(p)) {
            p.sendMessage("§cYou can't use /upgrades while inside a dungeon run. Leave with /dung leave first.");
            return true;
        }
        plugin.shopUI().openUpgrades(p);
        return true;
    }

    // ---------- /salvage ----------

    private boolean salvageCmd(Player p, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("all")) return salvageAll(p);
        if (args.length > 0 && (args[0].equalsIgnoreCase("fav") || args[0].equalsIgnoreCase("favorite"))) {
            return toggleFavorite(p);
        }
        return salvageHeld(p);
    }

    /** Break the held Dung armor piece into salvage shards (only during a run). Shards are added to
     *  a per-floor counter and only become persistent shards when the floor boss is defeated. */
    private boolean salvageHeld(Player p) {
        GameManager gm = plugin.game();
        if (!gm.isInInstance(p)) {
            p.sendMessage("§cSalvage only works while inside a run. Start one with /dung start.");
            return true;
        }
        DungeonInstance di = gm.instanceOf(p);
        if (di == null) return true;
        ItemStack held = p.getInventory().getItemInMainHand();
        String kind = tag(held, ItemTags.KIND);
        if (!"armor".equals(kind)) {
            p.sendMessage("§cHold a Dung armor piece in your main hand to salvage it.");
            return true;
        }
        if (com.lieyabull.dung.items.GearFactory.isFavorite(held)) {
            p.sendMessage("§8That armor is §bfavorited§8. Run §f/salvage favorite§8 to un-favorite it first.");
            return true;
        }
        if (com.lieyabull.dung.items.GearFactory.isStarter(held)) {
            p.sendMessage("§8That's your free starter kit — it can't be salvaged.");
            return true;
        }
        int value = salvageValue(held);
        held.setAmount(held.getAmount() - 1);
        UUID pid = p.getUniqueId();
        Run run = di.run();
        run.salvageShards.merge(pid, value, Integer::sum);
        int total = run.salvageShards.getOrDefault(pid, 0);
        p.sendMessage("§bSalvaged " + rarityColor(held)
                + (held.getItemMeta() == null ? held.getType().name() : held.getItemMeta().getDisplayName())
                + "§b → §b+" + value + " shards§7 (floor total §b" + total + "§7).");
        return true;
    }

    /** Toggle the favorite flag on the held armor piece (works anywhere, protects from salvage). */
    private boolean toggleFavorite(Player p) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!"armor".equals(tag(held, ItemTags.KIND))) {
            p.sendMessage("§cHold a Dung armor piece to favorite/un-favorite it.");
            return true;
        }
        boolean now = com.lieyabull.dung.items.GearFactory.toggleFavorite(held);
        p.sendMessage(now
                ? "§bFavorited — §f/salvage§b and §f/salvage all§b will skip this piece."
                : "§7Un-favorited — this piece can be salvaged again.");
        return true;
    }

    /** Salvage every salvable armor piece in the main inventory OUTSIDE the hotbar, armor slots,
     *  and offhand. Favorited pieces are always skipped. */
    private boolean salvageAll(Player p) {
        GameManager gm = plugin.game();
        if (!gm.isInInstance(p)) {
            p.sendMessage("§cSalvage only works while inside a run. Start one with /dung start.");
            return true;
        }
        DungeonInstance di = gm.instanceOf(p);
        if (di == null) return true;
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        int pieces = 0, totalValue = 0;
        // main storage only (0-35); slots 36+ are armor/offhand which getSize() ALSO includes,
        // and those are armed/equipped, not "in the bag".
        for (int slot = 9; slot < 36; slot++) {
            org.bukkit.inventory.ItemStack s = inv.getItem(slot);
            if (!isSalvableArmor(s)) continue;
            pieces++;
            totalValue += salvageValue(s);
            inv.setItem(slot, null);
        }
        if (pieces == 0) {
            p.sendMessage("§7Nothing to salvage — no Dung armor in your bag that isn't favorited, hotbar, or equipped.");
            return true;
        }
        UUID pid = p.getUniqueId();
        Run run = di.run();
        run.salvageShards.merge(pid, totalValue, Integer::sum);
        int total = run.salvageShards.getOrDefault(pid, 0);
        p.sendMessage("§bSalvaged §f" + pieces + "§b armor pieces §b→ §b+" + totalValue
                + " shards§7 (floor total §b" + total + "§7).");
        return true;
    }

    private static boolean isSalvableArmor(org.bukkit.inventory.ItemStack s) {
        if (s == null || s.getType() == org.bukkit.Material.AIR) return false;
        if (com.lieyabull.dung.items.GearFactory.isFavorite(s)) return false;
        // /salvage all only sweeps the bag; persistent gear is never bulk-salvaged (only held salvage).
        if (com.lieyabull.dung.items.GearFactory.isPersistent(s)) return false;
        // Free starter-kit gear is never salvageable.
        if (com.lieyabull.dung.items.GearFactory.isStarter(s)) return false;
        return "armor".equals(pdcString(s, ItemTags.KIND));
    }

    /** Shard value of one armor piece: rarity-scaled + defense. */
    private static int salvageValue(org.bukkit.inventory.ItemStack s) {
        String rs = pdcString(s, ItemTags.RARITY);
        Rarity r = Rarity.COMMON;
        if (rs != null) {
            try {
                r = Rarity.valueOf(rs);
            } catch (IllegalArgumentException ignored) {
            }
        }
        int def = pdcInt(s, ItemTags.DEFENSE);
        return Math.max(1, (r.ordinal() + 1) * 2 + def / 10);
    }

    private static String pdcString(org.bukkit.inventory.ItemStack s, String key) {
        if (s == null || s.getItemMeta() == null) return null;
        return s.getItemMeta().getPersistentDataContainer().get(
                org.bukkit.NamespacedKey.minecraft(key), org.bukkit.persistence.PersistentDataType.STRING);
    }

    private static int pdcInt(org.bukkit.inventory.ItemStack s, String key) {
        if (s == null || s.getItemMeta() == null) return 0;
        Integer v = s.getItemMeta().getPersistentDataContainer().get(
                org.bukkit.NamespacedKey.minecraft(key), org.bukkit.persistence.PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private void addShards(Player p, int amount) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        prof.shards += amount;
        plugin.meta().save();
    }

    private String rarityColor(org.bukkit.inventory.ItemStack s) {
        String rs = pdcString(s, ItemTags.RARITY);
        if (rs == null) return "";
        try {
            return Rarity.valueOf(rs).legacy;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    // ---------- balance ----------

    private void balance(Player p, String[] args) {
        if (args.length > 1) {
            // Check another player's balance
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                p.sendMessage("§cPlayer not found.");
                return;
            }
            MetaManager.MetaProfile prof = plugin.meta().profile(target.getUniqueId());
            p.sendMessage("§6--- " + target.getName() + "'s Balance ---");
            p.sendMessage("§7Persistent coins: §6" + prof.persistentCoins);
            p.sendMessage("§7Shards: §b" + prof.shards);
        } else {
            MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
            p.sendMessage("§6--- " + p.getName() + "'s Balance ---");
            p.sendMessage("§7Persistent coins: §6" + prof.persistentCoins);
            p.sendMessage("§7Shards: §b" + prof.shards);
        }
    }

    // ---------- leaderboard ----------

    private static final String[] LB_CATEGORIES = {
            "persistent_coins", "shards", "kills", "clears", "max_floor"
    };
    private static final String[] LB_LABELS = {
            "§6Persistent Coins", "§bShards", "§cKills", "§aFloors Cleared", "§5Max Floor"
    };
    private static final int LB_PER_PAGE = 5;

    private void leaderboard(Player p, String[] args) {
        int catIdx = 0; // default: persistent_coins
        int page = 1;

        if (args.length > 1) {
            for (int i = 0; i < LB_CATEGORIES.length; i++) {
                if (LB_CATEGORIES[i].equalsIgnoreCase(args[1])) {
                    catIdx = i;
                    break;
                }
            }
        }
        if (args.length > 2) {
            try {
                page = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {}
        }

        // Collect all profiles
        var meta = plugin.meta();
        java.util.List<java.util.Map.Entry<java.util.UUID, MetaManager.MetaProfile>> sorted = new java.util.ArrayList<>();
        try {
            var field = MetaManager.class.getDeclaredField("profiles");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<java.util.UUID, MetaManager.MetaProfile> map =
                    (java.util.Map<java.util.UUID, MetaManager.MetaProfile>) field.get(meta);
            sorted.addAll(map.entrySet());
        } catch (Exception e) {
            p.sendMessage("§cError reading profiles.");
            return;
        }

        // Sort by the selected category descending
        java.util.Comparator<java.util.Map.Entry<java.util.UUID, MetaManager.MetaProfile>> comp;
        switch (catIdx) {
            case 0: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.persistentCoins)); break;
            case 1: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.shards)); break;
            case 2: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.kills)); break;
            case 3: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.clears)); break;
            case 4: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.bestFloor)); break;
            default: comp = java.util.Map.Entry.comparingByValue(
                    java.util.Comparator.comparingInt(prof -> prof.persistentCoins));
        }
        sorted.sort(comp.reversed());

        int totalPages = Math.max(1, (int) Math.ceil((double) sorted.size() / LB_PER_PAGE));
        if (page > totalPages) page = totalPages;
        int start = (page - 1) * LB_PER_PAGE;
        int end = Math.min(start + LB_PER_PAGE, sorted.size());

        // Build header
        p.sendMessage("");
        p.sendMessage("§6§l--- " + LB_LABELS[catIdx] + " §6§lLeaderboard ---");
        p.sendMessage("");

        if (sorted.isEmpty()) {
            p.sendMessage("§7No data yet.");
        } else {
            for (int i = start; i < end; i++) {
                var entry = sorted.get(i);
                String name = org.bukkit.Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = "§7Unknown";
                int rank = i + 1;
                String rankStr = rank <= 3 ? getRankColor(rank) + "#" + rank : "§7#" + rank;
                int value = switch (catIdx) {
                    case 0 -> entry.getValue().persistentCoins;
                    case 1 -> entry.getValue().shards;
                    case 2 -> entry.getValue().kills;
                    case 3 -> entry.getValue().clears;
                    case 4 -> entry.getValue().bestFloor;
                    default -> 0;
                };
                p.sendMessage(rankStr + " §f" + name + " §7- §e" + value);
            }
        }

        p.sendMessage("");
        // Page navigation
        var line = net.kyori.adventure.text.Component.empty();
        if (page > 1) {
            line = line.append(ChatUI.command("§7[§f◀ Prev§7]", "/leaderboard " + LB_CATEGORIES[catIdx] + " " + (page - 1), "Previous page"));
        } else {
            line = line.append(LegacyComponentSerializer.legacySection().deserialize("§8[ ◀ Prev ]"));
        }
        line = line.append(LegacyComponentSerializer.legacySection().deserialize(" §7Page " + page + "/" + totalPages + " "));
        if (page < totalPages) {
            line = line.append(ChatUI.command("§7[§fNext ▶§7]", "/leaderboard " + LB_CATEGORIES[catIdx] + " " + (page + 1), "Next page"));
        } else {
            line = line.append(LegacyComponentSerializer.legacySection().deserialize("§8[ Next ▶ ]"));
        }
        p.sendMessage(line);

        // Category switcher buttons
        var catLine = LegacyComponentSerializer.legacySection().deserialize("§7Categories: ");
        for (int i = 0; i < LB_CATEGORIES.length; i++) {
            if (i == catIdx) {
                catLine = catLine.append(LegacyComponentSerializer.legacySection().deserialize("§a§l" + getShortLabel(i) + "§7"));
            } else {
                catLine = catLine.append(ChatUI.command("§7" + getShortLabel(i), "/leaderboard " + LB_CATEGORIES[i] + " 1", LB_LABELS[i]));
            }
            if (i < LB_CATEGORIES.length - 1) {
                catLine = catLine.append(LegacyComponentSerializer.legacySection().deserialize(" §8| "));
            }
        }
        p.sendMessage(catLine);
        p.sendMessage("");
    }

    private static String getRankColor(int rank) {
        return switch (rank) {
            case 1 -> "§6"; // gold
            case 2 -> "§7"; // silver
            case 3 -> "§6"; // bronze-ish (gold on dark bg)
            default -> "§7";
        };
    }

    private static String getShortLabel(int idx) {
        return switch (idx) {
            case 0 -> "Coins";
            case 1 -> "Shards";
            case 2 -> "Kills";
            case 3 -> "Clears";
            case 4 -> "MaxFloor";
            default -> "?";
        };
    }

    // ---------- existing: stats / class / give ----------

    private void stats(Player p) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        p.sendMessage("§6--- " + p.getName() + " ---");
        p.sendMessage("§7Class: §f" + TextUtil.capital(prof.classId));
        p.sendMessage("§7Persistent coins: §6" + prof.persistentCoins + "   §7Shards: §b" + prof.shards);
        p.sendMessage("§7Deaths: §c" + prof.deaths + "   §7Best floor: §f" + prof.bestFloor + "   §7Kills: §f" + prof.kills);
        p.sendMessage("§7Floors cleared: §f" + prof.clears);
    }

    private void classCmd(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§7Classes: §fwarrior, mage, ranger");
            return;
        }
        String c = args[1].toLowerCase();
        if (!c.equals("warrior") && !c.equals("mage") && !c.equals("ranger")) {
            p.sendMessage("§cUnknown class.");
            return;
        }
        plugin.meta().profile(p.getUniqueId()).classId = c;
        plugin.meta().save(); // persist immediately so a crash/restart can't roll the choice back
        p.sendMessage("§aClass set to " + TextUtil.capital(c) + ". Next run uses it.");
    }

    private void give(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§7Usage: /dung give <rareweapon|heal|coins>"); return; }
        switch (args[1].toLowerCase()) {
            case "rareweapon":
                // free debug spawn (no coin cost): real purchases go through /shop
                p.getInventory().addItem(GearFactory.markPersistent(ItemPool.randomWeapon(2)));
                p.sendMessage("§aDebug: spawned a weapon (persists through death).");
                break;
            case "heal":
                p.setHealth(20);
                p.sendMessage("§aHealed.");
                break;
            case "coins":
                p.getInventory().addItem(new ItemStack(org.bukkit.Material.GOLD_NUGGET, 10));
                p.sendMessage("§e+10 coins (run).");
                break;
            default:
                p.sendMessage("§7Unknown give target.");
        }
    }

    private String tag(ItemStack s, String key) {
        if (s == null || s.getType() == org.bukkit.Material.AIR || s.getItemMeta() == null) return null;
        var pdc = s.getItemMeta().getPersistentDataContainer();
        String v = pdc.get(org.bukkit.NamespacedKey.minecraft(key), org.bukkit.persistence.PersistentDataType.STRING);
        return v;
    }

}
