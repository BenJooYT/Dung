package com.lieyabull.dung.command;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.game.DungeonInstance;
import com.lieyabull.dung.game.Run;
import com.lieyabull.dung.game.WorkstationRules;
import com.lieyabull.dung.items.GearFactory;
import com.lieyabull.dung.meta.MetaManager;
import com.lieyabull.dung.party.Party;
import com.lieyabull.dung.party.PartyManager;
import com.lieyabull.dung.ui.ChatUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles every NON-run command that isn't the core {@code /dung} command itself:
 * {@code /shop}, {@code /upgrades}, {@code /salvage}, {@code /stash}, {@code /party},
 * {@code /balance} and {@code /leaderboard}. Run-lifecycle logic stays in
 * {@link DungCommand}; this class only needs the shared {@link Dung} services.
 */
public final class MetaCommand implements CommandExecutor, TabCompleter {
    private final Dung plugin;
    private final Map<UUID, Long> lastPartyInvite = new HashMap<>();
    private static final long PARTY_INVITE_COOLDOWN_MS = 5000;

    public MetaCommand(Dung plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use Dung.");
            return true;
        }
        switch (label.toLowerCase()) {
            case "shop": return shopCmd(p, args);
            case "upgrades": return upgradesCmd(p, args);
            case "stash": return stashCmd(p, args);
            case "salvage": return salvageCmd(p, args);
            case "party": return partyCmd(p, args);
            case "balance": balance(p, args); return true;
            case "leaderboard": leaderboard(p, args); return true;
            default: return true;
        }
    }

    // ---------- /party ----------

    public boolean partyCmd(Player p, String[] args) {
        PartyManager pm = plugin.game().partyManager();
        com.lieyabull.dung.game.GameManager gm = plugin.game();
        if (args.length == 0) {
            Party party = pm.partyOf(p);
            if (party == null) {
                p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§7You are not in a party. Use §f/party create§7 to start one."));
                p.sendMessage("§7Commands: §fcreate, invite <player>, accept, decline, leave, kick <player>, disband, info");
                return true;
            }
            p.sendMessage("§6--- Party ---");
            p.sendMessage("§7Leader: §f" + Bukkit.getOfflinePlayer(party.leader()).getName());
            p.sendMessage("§7Members (" + party.size() + "/" + Party.MAX_SIZE + "):");
            for (UUID uid : party.members()) {
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
                p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§aParty created! Invite players with §f/party invite <player>"));
                return true;
            }
            case "invite": {
                if (args.length < 2) { p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cUsage: /party invite <player>")); return true; }
                long now = System.currentTimeMillis();
                Long last = lastPartyInvite.get(p.getUniqueId());
                if (last != null && now - last < PARTY_INVITE_COOLDOWN_MS) {
                    p.sendMessage("§cYou can't invite yet. Wait " + (int) Math.ceil((PARTY_INVITE_COOLDOWN_MS - (now - last)) / 1000.0) + "s.");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage("§cPlayer not found."); return true; }
                if (target.equals(p)) { p.sendMessage("§cYou can't invite yourself."); return true; }
                if (gm.isInInstance(p)) {
                    p.sendMessage("§cYou can't invite while your party is in a run.");
                    return true;
                }
                if (pm.invite(p, target)) {
                    lastPartyInvite.put(p.getUniqueId(), now);
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
                if (args.length < 2) { p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cUsage: /party kick <player>")); return true; }
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

    public boolean shopCmd(Player p, String[] args) {
        if (plugin.game().isInInstance(p)) {
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cYou can't use /shop while inside a dungeon run. Leave with /dung leave first."));
            return true;
        }
        plugin.shopUI().openPersistentShop(p);
        return true;
    }

    // ---------- /upgrades ----------

    public boolean upgradesCmd(Player p, String[] args) {
        if (plugin.game().isInInstance(p)) {
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cYou can't use /upgrades while inside a dungeon run. Leave with /dung leave first."));
            return true;
        }
        plugin.shopUI().openUpgrades(p);
        return true;
    }

    // ---------- /stash ----------

    public boolean stashCmd(Player p, String[] args) {
        if (plugin.game().isInInstance(p)) {
            p.sendMessage(com.lieyabull.dung.ui.ChatUI.clickableCommands("§cYou can't use /stash while inside a dungeon run. Leave with /dung leave first."));
            return true;
        }
        plugin.stashUI().open(p);
        return true;
    }

    // ---------- /salvage ----------

    public boolean salvageCmd(Player p, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("all")) return salvageAll(p);
        if (args.length > 0 && (args[0].equalsIgnoreCase("fav") || args[0].equalsIgnoreCase("favorite"))) {
            return toggleFavorite(p);
        }
        return salvageHeld(p);
    }

    /** Break the held Dung armor piece into salvage shards. Shards are permanent: during a run they're
     *  added to a per-floor counter that becomes persistent shards when the floor boss is defeated;
     *  outside a run they go straight into your persistent shard balance. */
    private boolean salvageHeld(Player p) {
        org.bukkit.inventory.ItemStack held = p.getInventory().getItemInMainHand();
        String kind = tag(held, com.lieyabull.dung.items.ItemTags.KIND);
        if (!"armor".equals(kind)) {
            p.sendMessage("§cHold a Dung armor piece in your main hand to salvage it.");
            return true;
        }
        if (GearFactory.isFavorite(held)) {
            p.sendMessage(ChatUI.clickableCommands("§8That armor is §bfavorited§8. Run §f/salvage favorite§8 to un-favorite it first."));
            return true;
        }
        if (GearFactory.isStarter(held)) {
            p.sendMessage("§8That's your free starter kit — it can't be salvaged.");
            return true;
        }
        // Persistent gear IS salvable when held, so a player can consciously turn a permanent piece
        // into shards. Favorite it (via /salvage favorite) if you want it protected from accidental
        // salvage. Bulk salvage (/salvage all) still skips persistent gear.
        String name = held.getItemMeta() == null ? held.getType().name() : held.getItemMeta().getDisplayName();
        int value = WorkstationRules.salvageValueOf(held);
        int amount = held.getAmount() - 1;
        if (amount <= 0) p.getInventory().setItemInMainHand(null);
        else held.setAmount(amount);
        UUID pid = p.getUniqueId();
        DungeonInstance di = plugin.game().instanceOf(p);
        if (di == null) {
            addShards(p, value);
            p.sendMessage("§bSalvaged " + rarityColor(held) + name
                    + "§b → §b+" + value + " shards§7 (balance §b" + plugin.meta().profile(pid).shards + "§7).");
        } else {
            Run run = di.run();
            run.salvageShards.merge(pid, value, Integer::sum);
            int total = run.salvageShards.getOrDefault(pid, 0);
            p.sendMessage("§bSalvaged " + rarityColor(held) + name
                    + "§b → §b+" + value + " shards§7 (floor total §b" + total + "§7).");
        }
        return true;
    }

    /** Toggle the favorite flag on the held armor piece (works anywhere, protects from salvage). */
    private boolean toggleFavorite(Player p) {
        org.bukkit.inventory.ItemStack held = p.getInventory().getItemInMainHand();
        if (!"armor".equals(tag(held, com.lieyabull.dung.items.ItemTags.KIND))) {
            p.sendMessage("§cHold a Dung armor piece to favorite/un-favorite it.");
            return true;
        }
        boolean now = GearFactory.toggleFavorite(held);
        p.sendMessage(now
                ? "§bFavorited — §f/salvage§b and §f/salvage all§b will skip this piece."
                : "§7Un-favorited — this piece can be salvaged again.");
        return true;
    }

    /** Salvage every salvable armor piece in the main inventory OUTSIDE the hotbar, armor slots,
     *  and offhand. Favorited pieces are always skipped. Shards go to the persistent balance outside
     *  a run, or to the per-floor counter during a run. */
    private boolean salvageAll(Player p) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        int pieces = 0, totalValue = 0;
        // main storage only (0-35); slots 36+ are armor/offhand which getSize() ALSO includes,
        // and those are armed/equipped, not "in the bag".
        for (int slot = 9; slot < 36; slot++) {
            org.bukkit.inventory.ItemStack s = inv.getItem(slot);
            if (!WorkstationRules.isBulkSalvageable(s)) continue;
            pieces++;
            totalValue += WorkstationRules.salvageValueOf(s);
            inv.setItem(slot, null);
        }
        if (pieces == 0) {
            p.sendMessage("§7Nothing to salvage — no Dung armor in your bag that isn't favorited, hotbar, or equipped.");
            return true;
        }
        UUID pid = p.getUniqueId();
        DungeonInstance di = plugin.game().instanceOf(p);
        if (di == null) {
            addShards(p, totalValue);
            p.sendMessage("§bSalvaged §f" + pieces + "§b armor pieces §b→ §b+" + totalValue
                    + " shards§7 (balance §b" + plugin.meta().profile(pid).shards + "§7).");
        } else {
            Run run = di.run();
            run.salvageShards.merge(pid, totalValue, Integer::sum);
            int total = run.salvageShards.getOrDefault(pid, 0);
            p.sendMessage("§bSalvaged §f" + pieces + "§b armor pieces §b→ §b+" + totalValue
                    + " shards§7 (floor total §b" + total + "§7).");
        }
        return true;
    }

    private static String tag(org.bukkit.inventory.ItemStack s, String key) {
        if (s == null || s.getItemMeta() == null) return null;
        return s.getItemMeta().getPersistentDataContainer().get(
                org.bukkit.NamespacedKey.minecraft(key), org.bukkit.persistence.PersistentDataType.STRING);
    }

    private void addShards(Player p, int amount) {
        MetaManager.MetaProfile prof = plugin.meta().profile(p.getUniqueId());
        prof.shards += amount;
        plugin.meta().save();
    }

    private String rarityColor(org.bukkit.inventory.ItemStack s) {
        String rs = tag(s, com.lieyabull.dung.items.ItemTags.RARITY);
        if (rs == null) return "";
        try {
            return com.lieyabull.dung.items.Rarity.valueOf(rs).legacy;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    // ---------- balance ----------

    public void balance(Player p, String[] args) {
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

    private static final String[] PARTY_SUBS = {"create", "invite", "accept", "decline", "leave", "kick", "disband", "info"};
    private static final String[] SALVAGE_SUBS = {"all", "favorite", "fav"};

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        String label = cmd.getName().toLowerCase();
        if (label.equals("party")) {
            if (args.length == 1) return filter(PARTY_SUBS, args[0]);
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if (sub.equals("invite") || sub.equals("kick")) return playerNames(args[1]);
            }
            return List.of();
        }
        if (label.equals("salvage")) {
            if (args.length == 1) return filter(SALVAGE_SUBS, args[0]);
            return List.of();
        }
        if (label.equals("leaderboard")) {
            if (args.length == 1) return filter(LB_CATEGORIES, args[0]);
            if (args.length == 2) return filter(new String[]{"1", "2", "3", "4", "5"}, args[1]);
            return List.of();
        }
        // shop, upgrades, balance, stash: no arguments
        return List.of();
    }

    /** Return the options in {@code opts} that start with the given (case-insensitive) prefix. */
    private static List<String> filter(String[] opts, String prefix) {
        String q = prefix.toLowerCase();
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String o : opts) {
            if (o.toLowerCase().startsWith(q)) out.add(o);
        }
        return out;
    }

    /** Return the names of online players starting with the given prefix. */
    private static List<String> playerNames(String prefix) {
        String q = prefix.toLowerCase();
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getName().toLowerCase().startsWith(q)) out.add(pl.getName());
        }
        return out;
    }

    private static final String[] LB_CATEGORIES = {
            "persistent_coins", "shards", "kills", "clears", "max_floor"
    };
    private static final String[] LB_LABELS = {
            "§6Persistent Coins", "§bShards", "§cKills", "§aFloors Cleared", "§5Max Floor"
    };
    private static final int LB_PER_PAGE = 5;

    public void leaderboard(Player p, String[] args) {
        int catIdx = 0; // default: persistent_coins
        int page = 1;

        // args layout: /leaderboard <category> <page> (args[0] = category, args[1] = page)
        if (args.length > 0) {
            boolean found = false;
            for (int i = 0; i < LB_CATEGORIES.length; i++) {
                if (LB_CATEGORIES[i].equalsIgnoreCase(args[0])) {
                    catIdx = i;
                    found = true;
                    break;
                }
            }
            if (!found) {
                p.sendMessage("§cUnknown leaderboard category: §f" + args[0]
                        + "§c. Valid: §f" + String.join(", ", LB_CATEGORIES));
                return;
            }
        }
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {}
        }

        // Collect all saved profiles (including offline players) from the save file.
        var meta = plugin.meta();
        java.util.List<java.util.Map.Entry<java.util.UUID, MetaManager.MetaProfile>> sorted =
                new java.util.ArrayList<>(meta.allProfiles().entrySet());

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
                // Prefer the persisted profile name (so offline players are shown too); fall back to
                // Bukkit's offline lookup, then a placeholder.
                String name = entry.getValue().name;
                if (name == null) name = org.bukkit.Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = "§7Unknown";
                boolean online = org.bukkit.Bukkit.getPlayer(entry.getKey()) != null;
                String suffix = online ? "" : " §8(offline)";
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
                p.sendMessage(rankStr + " §f" + name + suffix + " §7- §e" + value);
            }
        }

        p.sendMessage("");
        // Page navigation
        var line = net.kyori.adventure.text.Component.empty();
        if (page > 1) {
            line = line.append(ChatUI.command("§7[§f◀ Prev§7]", "/leaderboard " + LB_CATEGORIES[catIdx] + " " + (page - 1), "Previous page"));
        } else {
            line = line.append(LegacyComponentSerializer.legacySection().deserialize("§8◀ Prev"));
        }
        line = line.append(LegacyComponentSerializer.legacySection().deserialize(" §7Page " + page + "/" + totalPages + " "));
        if (page < totalPages) {
            line = line.append(ChatUI.command("§7[§fNext ▶§7]", "/leaderboard " + LB_CATEGORIES[catIdx] + " " + (page + 1), "Next page"));
        } else {
            line = line.append(LegacyComponentSerializer.legacySection().deserialize("§8Next ▶"));
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
            case 1 -> "§b"; // aqua
            case 2 -> "§9"; // blue
            case 3 -> "§1"; // dark blue
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
}
