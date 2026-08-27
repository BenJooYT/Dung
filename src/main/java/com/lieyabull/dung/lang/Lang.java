package com.lieyabull.dung.lang;

import com.lieyabull.dung.Dung;
import com.lieyabull.dung.meta.MetaManager;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Central localization catalog. Every user-facing string that should be translatable is defined
 * here under a stable key, with one translation per {@link Language}. Missing translations fall
 * back to English, which is always fully populated.
 *
 * <p>Values may contain {@code %s} placeholders, substituted positionally by
 * {@link #get(Language, String, Object...)}.</p>
 *
 * <p>This is intentionally a single in-code catalog (rather than per-locale resource files) so
 * keys are compile-time safe and easy to map through the whole plugin as more messages are
 * migrated to localization over time.</p>
 */
public final class Lang {

    private Lang() {}

    /** message key -> per-language translation. The English entry is always present. */
    private static final Map<String, Map<Language, String>> CATALOG = Map.ofEntries(
            // ---- /language command ----
            entry("language.noConsole", "Only players can change their language.", "A nyelvet csak játékosok válthatják meg."),
            entry("language.current", "Your language is set to: %s", "A nyelved: %s"),
            entry("language.set", "Language set to: %s", "Nyelv beállítva: %s"),
            entry("language.available", "Available languages: %s", "Elérhető nyelvek: %s"),
            entry("language.usage", "Usage: /language <english|magyar>", "Használat: /language <english|magyar>"),
            entry("language.unknown", "Unknown language '%s'. Use /language to list them.", "Ismeretlen nyelv: '%s'. Használd a /language parancsot a lista megtekintéséhez."),

            // ---- party ----
            entry("party.alreadyIn", "§cYou are already in a party.", "§cMár egy csapatban vagy."),
            entry("party.joined", "§a%s joined the party.", "§a%s csatlakozott a csapathoz."),
            entry("party.left", "§e%s left the party.", "§e%s elhagyta a csapatot."),
            entry("party.newLeader", "§e%s is now the party leader.", "§e%s mostantól a csapatvezető."),
            entry("party.kicked", "§cYou were kicked from the party.", "§cKirúgtak a csapatból."),
            entry("party.kickedBroadcast", "§e%s was kicked from the party.", "§e%s-t kirúgták a csapatból."),
            entry("party.disbanded", "§cThe party has been disbanded.", "§cA csapat feloszlott."),
            entry("party.notInParty", "§7You are not in a party. Use §f/party create§7 to start one.", "§7Nem vagy csapatban. Használd a §f/party create§7 parancsot, hogy létrehozz egyet."),
            entry("party.leaderOnly", "§cOnly the party leader can do that.", "§cEzt csak a csapatvezető teheti meg."),
            entry("party.inviteSent", "§aInvite sent to %s§a.", "§aMeghívót küldtél a következőnek: %s§a."),
            entry("party.invitedYou", "§e%s§e invited you to their party.", "§e%s§e meghívott a csapatába."),
            entry("party.noInvite", "§cYou have no pending party invite.", "§cNincs függőben lévő csapatmeghívásod."),
            entry("party.inviteAccepted", "§aYou joined %s§a's party.", "§aCsatlakoztál %s§a csapatához."),
            entry("party.partyFull", "§cThat party is full.", "§cAz a csapat megtelt."),
            entry("party.notMember", "§cThat player is not in your party.", "§cAz a játékos nincs a csapatodban."),
            entry("party.usage", "§7Commands: §fcreate, invite <player>, accept, decline, leave, kick <player>, disband, info", "§7Parancsok: §fcreate, invite <player>, accept, decline, leave, kick <player>, disband, info"),
            entry("party.help", "§7Party commands: §fcreate, invite <player>, accept, decline, leave, kick <player>, disband, info", "§7Csapatparancsok: §fcreate, invite <player>, accept, decline, leave, kick <player>, disband, info"),
            entry("party.header", "§6--- Party ---", "§6--- Csapat ---"),
            entry("party.leaderLine", "§7Leader: §f%s", "§7Vezető: §f%s"),
            entry("party.membersLine", "§7Members (%s/%s):", "§7Tagok (%s/%s):"),
            entry("party.leaderTag", " §6(Leader)", " §6(Vezető)"),
            entry("party.memberLine", "  §7- §f%s%s", "  §7- §f%s%s"),
            entry("party.alreadyInLeaveFirst", "§cYou're already in a party. Leave first.", "§cMár egy csapatban vagy. Előbb lépj ki."),
            entry("party.created", "§aParty created! Invite players with §f/party invite <player>", "§aCsapat létrehozva! Hívd meg a játékosokat: §f/party invite <player>"),
            entry("party.inviteUsage", "§cUsage: /party invite <player>", "§cHasználat: /party invite <player>"),
            entry("party.inviteCooldown", "§cYou can't invite yet. Wait %ss.", "§cMég nem küldhetsz meghívót. Várj %s mp-et."),
            entry("party.playerNotFound", "§cPlayer not found.", "§cA játékos nem található."),
            entry("party.cantInviteSelf", "§cYou can't invite yourself.", "§cNem hívhatod meg magad."),
            entry("party.cantInviteInRun", "§cYou can't invite while your party is in a run.", "§cNem hívhatsz meg, amíg a csapatod futamban van."),
            entry("party.invitedTarget", "§aInvited %s to the party.", "§aMeghívtad a csapatba: %s."),
            entry("party.invitedYouCmd", "§a%s invited you to a party!", "§a%s meghívott a csapatába!"),
            entry("party.inviteBtnAccept", "§a[Accept]", "§a[Elfogad]"),
            entry("party.inviteBtnAcceptHover", "Join the party", "Csatlakozás a csapathoz"),
            entry("party.inviteBtnDecline", "§c[Decline]", "§c[Elutasít]"),
            entry("party.inviteBtnDeclineHover", "Decline the invite", "Meghívás elutasítása"),
            entry("party.couldNotInvite", "§cCould not invite. They may already be in a party, or the party is full.", "§cNem sikerült meghívni. Lehet, hogy már csapatban van, vagy a csapat megtelt."),
            entry("party.cantJoinInRun", "§cYou can't join a party while you're in a run.", "§cNem csatlakozhatsz csapathoz futam közben."),
            entry("party.alreadyStartedRun", "§cThat party has already started a run.", "§cAz a csapat már elindított egy futamot."),
            entry("party.joinedSelf", "§aYou joined the party!", "§aCsatlakoztál a csapathoz!"),
            entry("party.noPendingOrFull", "§cNo pending invite or party is full.", "§cNincs függő meghívás, vagy a csapat megtelt."),
            entry("party.inviteDeclined", "§7Invite declined.", "§7Meghívás elutasítva."),
            entry("party.leftSelf", "§7You left the party.", "§7Kiléptél a csapatból."),
            entry("party.kickUsage", "§cUsage: /party kick <player>", "§cHasználat: /party kick <player>"),
            entry("party.kickedSelf", "§aKicked %s from the party.", "§aKirúgtad a csapatból: %s."),
            entry("party.couldNotKick", "§cCould not kick. You may not be the leader.", "§cNem sikerült kirúgni. Lehet, hogy nem te vagy a vezető."),
            entry("party.disbandedSelf", "§cParty disbanded.", "§cA csapat feloszlott."),
            entry("party.notLeader", "§cYou are not the party leader.", "§cNem te vagy a csapatvezető."),
            entry("run.alreadyIn", "§cYou're already in a run.", "§cMár egy futamban vagy."),
            entry("run.cantShopInRun", "§cYou can't use /shop while inside a dungeon run. Leave with /dung leave first.", "§cNem használhatod a /shop parancsot futam közben. Előbb lépj ki a /dung leave paranccsal."),
            entry("run.cantUpgradesInRun", "§cYou can't use /upgrades while inside a dungeon run. Leave with /dung leave first.", "§cNem használhatod a /upgrades parancsot futam közben. Előbb lépj ki a /dung leave paranccsal."),
            entry("run.cantStashInRun", "§cYou can't use /stash while inside a dungeon run. Leave with /dung leave first.", "§cNem használhatod a /stash parancsot futam közben. Előbb lépj ki a /dung leave paranccsal."),
            entry("salvage.holdArmor", "§cHold a Dung armor piece in your main hand to salvage it.", "§cTarts egy Dung páncéldarabot a fő kezedben a selejtezéshez."),
            entry("salvage.holdArmorFavorite", "§cHold a Dung armor piece to favorite/un-favorite it.", "§cTarts egy Dung páncéldarabot a kedvencnek jelöléshez / eltávolításhoz."),
            entry("salvage.favorited", "§cThat armor is §bfavorited§c. Run §f/salvage favorite§c to un-favorite it first.", "§cEz a páncél §bkedvenc§c. A §f/salvage favorite§c paranccsal távolítsd el előbb a kedvencek közül."),
            entry("salvage.starter", "§8That's your free starter kit — it can't be salvaged.", "§8Ez az ingyenes kezdőkészleted — nem selejtezhető."),
            entry("salvage.doneBalance", "§bSalvaged %s%s§b → §b+%s shards§7 (balance §b%s§7).", "§bSelejtezted: %s%s§b → §b+%s szilánk§7 (egyenleg: §b%s§7)."),
            entry("salvage.doneFloor", "§bSalvaged %s%s§b → §b+%s shards§7 (floor total §b%s§7).", "§bSelejtezted: %s%s§b → §b+%s szilánk§7 (szint összesen: §b%s§7)."),
            entry("salvage.favoritedOn", "§bFavorited — §f/salvage§b and §f/salvage all§b will skip this piece.", "§bKedvencnek jelölve — §f/salvage§b és a §f/salvage all§b kihagyja ezt a darabot."),
            entry("salvage.favoritedOff", "§7Un-favorited — this piece can be salvaged again.", "§7A kedvencek közül eltávolítva — ez a darab újra selejtezhető."),
            entry("salvage.nothing", "§7Nothing to salvage — no Dung armor in your bag that isn't favorited, hotbar, or equipped.", "§7Nincs mit selejtezni — nincs olyan Dung páncélod, amelyik nem kedvenc, nem a gyorssávban van és nincs felszerelve."),
            entry("salvage.allBalance", "§bSalvaged §f%s§b armor pieces §b→ §b+%s shards§7 (balance §b%s§7).", "§bSelejteztél §f%s§b páncéldarabot §b→ §b+%s szilánk§7 (egyenleg: §b%s§7)."),
            entry("salvage.allFloor", "§bSalvaged §f%s§b armor pieces §b→ §b+%s shards§7 (floor total §b%s§7).", "§bSelejteztél §f%s§b páncéldarabot §b→ §b+%s szilánk§7 (szint összesen: §b%s§7)."),

            // ---- tutorial (startup) ----
            entry("tutorial.title", "§cDUNGEON", "§cTÖMLÖC"),
            entry("tutorial.subtitle", "§7Clear every room. Find the Warden. Descend deeper.", "§7Takarítsd ki az összes szobát. Találd meg a Wardent. Ereszkedj mindig mélyebbre."),
            entry("tutorial.clearRooms", "§6Clear rooms to earn coins and gear. Find the boss room to go deeper.", "§6Takarítsd ki a szobákat érmékért és felszerelésért. Találd meg a főnök szobáját, hogy tovább ereszkedhess."),
            entry("tutorial.attack", "§7Attack: §fLeft-Click    §7Weapon Ability: §fSneak + Right-Click", "§7Támadás: §fBal egérgomb    §7Fegyverképesség: §fGuggolás + Jobb egérgomb"),
            entry("tutorial.classAbility", "§7Class Ability: §fSneak + Drop (Q)    §7Heal: pick up §c♥§7 hearts", "§7Osztályképesség: §fGuggolás + Dobás (Q)    §7Gyógyulás: vedd fel a §c♥§7 szíveket"),
            entry("tutorial.keys", "§7Keys & Bombs appear in hotbar slots 7-8. Right-click locked doors with a key, cracked walls with a bomb.", "§7A kulcsok és a bombák a gyorssáv 7-8. slotjába kerülnek. Kattints jobb egérgombbal a zárt ajtóra kulccsal, a repedt falra bombával."),
            entry("tutorial.shield", "§7Equip a Mana Shield in slot 9 — hold it and sneak to charge it with mana.", "§7Szereld be a Mana Pajzsot a 9. slotba — tartsd a kezedben és guggolj, hogy manával töltsd."),
            entry("tutorial.exit", "§7Salvage spare armor: §f/salvage§7. Exit: §f/dung leave", "§7Selejtezd a felesleges páncélt: §f/salvage§7. Kilépés: §f/dung leave"),
            entry("tutorial.starterKit", "§7You were given a starter kit: §fFrayed Blade§7 + cloth armor.", "§7Kezdőfelszerelést kaptál: §fKopott Penge§7 + textil páncél."),

            // ---- ground pickups ----
            entry("pickup.heart", "§c+1 Red Heart §7(%s/%s)", "§c+1 Vörös Szív §7(%s/%s)"),
            entry("pickup.coin", "§e+1 Coin §7(%s)", "§e+1 Érme §7(%s)"),
            entry("pickup.key", "§9+1 Key §7(%s)", "§9+1 Kulcs §7(%s)"),
            entry("pickup.bomb", "§4+1 Bomb §7(%s)", "§4+1 Bomba §7(%s)"),
            entry("pickup.full", "§7%s §7is full — left on the ground.", "§7A(z) %s §7tele van — a földön maradt."),
            entry("pickup.fullHeart", "§7Your health is already full.", "§7Az életerőd nem tölthető tovább!"),
            entry("pickup.name.heart", "§cHearts", "§cSzívek"),
            entry("pickup.name.coin", "§eCoins", "§eÉrmék"),
            entry("pickup.name.key", "§9Keys", "§9Kulcsok"),
            entry("pickup.name.bomb", "§4Bombs", "§4Bombák"),

            // ---- /dung main menu (ChatUI.startPrompt) ----
            entry("menu.tagline", " — the dungeon awaits.", " — a Tömlöc rád vár."),
            entry("menu.start.label", "[ Start a Run ]", "[ Futam Indítása ]"),
            entry("menu.start.hover", "Begin a fresh run", "Kezdj egy új futamot"),
            entry("menu.shop.label", "[ Shop ]", "[ Bolt ]"),
            entry("menu.shop.hover", "Open the shop GUI — spend persistent coins on gear\n§cUnavailable during a run.", "Nyisd meg a bolt menüt — költsd el a maradandó érméidet felszerelésre\n§cFutam közben nem érhető el."),
            entry("menu.upgrades.label", "[ Upgrades ]", "[ Fejlesztések ]"),
            entry("menu.upgrades.hover", "Open the upgrades GUI — spend shards on permanent stat upgrades\n§cUnavailable during a run.", "Nyisd meg a fejlesztés menüt — használd a szilánkokat végleges tulajdonság-fejlesztésekre\n§cFutam közben nem érhető el."),
            entry("menu.stats.label", "[ My Stats ]", "[ Statisztikáim ]"),
            entry("menu.stats.hover", "View your meta-progression", "Nézd meg a haladásodat"),
            entry("menu.help.label", "[ Help ]", "[ Segítség ]"),
            entry("menu.help.hover", "List commands", "Parancsok listája"),

            // ---- dungeon room / door / boss feedback ----
            entry("room.seal.wait", "§cThe room won't seal until everyone is inside.", "§cA szoba addig nem záródik, amíg mindenki bent nincs."),
            entry("room.locked", "§cRoom locked — defeat all enemies!", "§cA szoba zárva — győzd le az összes ellenséget!"),
            entry("room.doorLocked", "§cThis room is locked — right-click the iron door with a key to unlock it!", "§cEz a szoba zárva van — jobb kattintás az acélajtón kulccsal a kinyitáshoz!"),
            entry("room.cleared", "§aRoom cleared! §7(+§e%s coins§7)", "§aTeljesítve! §7(+§e%s érme§7)"),
            entry("room.doorsOpened", "§aDoors opened!", "§aAz ajtók kinyíltak!"),
            entry("room.wardenWait", "§cThe Warden awaits until everyone is inside.", "§cA Warden addig vár, amíg mindenki bent nem lesz."),
            entry("room.bossAwaken", "§4The Warden of Floor %s awakens!", "§4A %s. szint Wardenje felébred!"),
            entry("room.bossSlain", "§6Boss slain!", "§6A főnök legyőzve!"),
            entry("room.hiddenFound", "§dYou found a hidden room!", "§dRejtett szobát találtál!"),

            // ---- keys & bombs ----
            entry("door.needKey", "§cYou need a key to unlock this door!", "§cKulcs kell a zárt ajtó kinyitásához!"),
            entry("door.keySnaps", "§cThe key snaps in the lock! §7(-1 key)", "§cA kulcs eltörik a zárban! §7(-1 kulcs)"),
            entry("door.unlocked", "§aYou unlock the door! §7(§e%s keys remaining§7)", "§aKinyitod az ajtót! §7(§e%s kulcs maradt§7)"),
            entry("door.needBomb", "§cYou need a bomb to destroy this wall!", "§cBomba kell a fal lerombolásához!"),
            entry("door.bombFizzle", "§cThe bomb fizzles out! §7(-1 bomb)", "§cA bomba kialszik! §7(-1 bomba)"),
            entry("door.bombDetonate", "§aYou detonate a bomb! §7(-1 bomb)", "§aFelrobbantasz egy bombát! §7(-1 bomba)"),

            // ---- descend voting ----
            entry("descend.bossFirst", "§cDefeat the boss first!", "§cElőbb győzd le a főnököt!"),
            entry("descend.alreadyVoted", "§7You already voted to descend.", "§7Már szavaztál a mélyebbre ereszkedésre."),
            entry("descend.passed", "§a§lDescend vote passed! (§e%s/%s§a)", "§a§lSikeres szavazás! Leszállás... (§e%s/%s§a)"),
            entry("descend.voted", "§e%s§7 voted to descend (§e%s/%s§7 needed)", "§e%s§7 leszállásra szavazott (§e%s/%s§7 szükséges)"),

            // ---- death summary ----
            entry("death.title", "§c§lYOU DIED §8— Floor %s", "§c§lMEGHALTÁL §8— %s. szint"),
            entry("death.kills", "§7  Kills this run: §f%s", "§7  Ölések ebben a futamban: §f%s"),
            entry("death.runCoins", "§7  Run coins: §e%s §7(gone unless revived by defeating the boss)", "§7  Futam érmék: §e%s §7(elvesznek, hacsak a főnök legyőzése fel nem támaszt)"),
            entry("death.durability", "§7  Persistent gear durability reduced by 10%", "§7  A maradandó felszerelés tartósága 10%-kal csökkent"),
            entry("death.unlocks", "§7Unlocks you keep:", "§7A megmaradt feloldások:"),
            entry("death.class", "§7  Class: §f%s", "§7  Osztály: §f%s"),
            entry("death.persistentCoins", "§7  Persistent coins: §6%s", "§7  maradandó érmék: §6%s"),
            entry("death.progress", "§7  Progress: §f%s§7 floors cleared, best §f%s§7, §f%s§7 kills", "§7  Előrehaladás: §f%s§7 szint kész, legjobb §f%s§7, §f%s§7 ölés"),
            entry("death.enoughForWeapon", "§a  You have enough for: §f/shop weapon", "§a  Van elég érméd egy fegyverre: §f/shop weapon"),
            entry("death.needForWeapon", "§8  Need §6%s§8 more coins for a weapon (/shop weapon)", "§8  Még §6%s§8 érme kell egy fegyverhez (/shop weapon)"),
            entry("death.tryAgain", "§7  Try /shop, /upgrades, or /dung start to go again.", "§7  Próbáld a /shop, /upgrades vagy /dung start parancsot."),
            entry("death.youDied", "§cYou died.", "§cMeghaltál."),
            entry("gear.armorBrokenEquip", "§cThat armor is broken — repair it at §6/shop§7 before equipping.", "§cEz a páncél eltört — javítsd meg a §6/shop§7 menüben, mielőtt felszereled."),
            entry("gear.noNonDungEquip", "§cYou can only equip Dung armor outside of a run.", "§cA futamon kívül csak Dung páncélt tudsz felszerelni."),
            entry("heal.self", "§aYou healed yourself for §c%s§c❤", "§aGyógyítottál önmagadon. §c+%s❤"),
            entry("heal.other", "§aHealed %s §afor §c%s§c❤", "§aMeggyógyítottad %s§a: §c%s§c❤"),
            entry("heal.otherTarget", "§a%s §ahealed you for §c%s§c❤", "§a%s §ameggyógyított: §c%s§c❤"),
            entry("heal.noHealth", "§cYou have no health to heal!", "§cNem tudsz gyógyítani!"),
            entry("heal.noStored", "§cNo stored health to spend! Attack enemies to charge it.", "§cNincs tárolt életerőd, amit elhasználhatnál! Támadj ellenségeket, hogy feltöltsd."),
            entry("heal.noStoredTransfer", "§cNo stored health to transfer! Attack enemies to charge it.", "§cNincs tárolt életerőd átviteléhez! Támadj ellenségeket, hogy feltöltsd."),
            entry("run.alreadyInLeaveFirst", "§cYou're already in a run. Use /dung leave first.", "§cMár egy futamban vagy. Előbb használd a /dung leave parancsot."),
            entry("run.leaderOnlyStart", "§cOnly the party leader can start a run.", "§cFutamot csak a csapatvezető indíthat."),
            entry("run.started", "§aRun started! Clear rooms, gear up, defeat the Warden.", "§aA futam elindult! Szerezz felszerelést, és győzd le a Wardent!"),
            entry("run.startFirst", "§cStart a run first.", "§cElőbb indíts egy futamot."),
            entry("run.left", "§7Left the run.", "§7Kiléptél a futamból."),
            entry("run.noActive", "§cNo active run.", "§cNincs aktív futam."),
            entry("run.cantLobbyInRun", "§cYou're in a run — use §f/dung leave§c first.", "§cFutamban vagy — előbb használd a §f/dung leave§c parancsot."),
            entry("stats.header", "§6--- %s ---", "§6--- %s ---"),
            entry("stats.class", "§7Class: §f%s", "§7Osztály: §f%s"),
            entry("stats.currency", "§7Persistent coins: §6%s   §7Shards: §b%s", "§7maradandó érmék: §6%s   §7Szilánkok: §b%s"),
            entry("stats.deathsFloor", "§7Deaths: §c%s   §7Best floor: §f%s   §7Kills: §f%s", "§7Halálok: §c%s   §7Legjobb szint: §f%s   §7Ölések: §f%s"),
            entry("stats.clears", "§7Floors cleared: §f%s", "§7Megtisztított szintek: §f%s"),
            entry("dung.classHint", "§7Classes: §fwarrior, mage, ranger", "§7Osztályok: §fwarrior, mage, ranger"),
            entry("dung.unknownClass", "§cUnknown class.", "§cIsmeretlen osztály."),
            entry("dung.classSet", "§aClass set to %s. Next run uses it.", "§aAz osztály beállítva: %s. A következő futam ezt használja."),
            entry("balance.playerNotFound", "§cPlayer not found.", "§cNincs ilyen játékos."),
            entry("balance.header", "§6--- %s's Balance ---", "§6--- %s egyenlege ---"),
            entry("balance.coins", "§7Persistent coins: §6%s", "§7maradandó érmék: §6%s"),
            entry("balance.shards", "§7Shards: §b%s", "§7Szilánkok: §b%s"),
            entry("lb.header", "§6§l%s §6§lLeaderboard ---", "§6§l%s §6§lRanglista ---"),
            entry("lb.noData", "§7No data yet.", "§7Még nincs adat."),
            entry("lb.offlineSuffix", " §8(offline)", " §8(offline)"),
            entry("lb.prevButton", "§7[§f◀ Prev§7]", "§7[§f◀ Előző§7]"),
            entry("lb.prevDisabled", "§8◀ Prev", "§8◀ Előző"),
            entry("lb.page", " §7Page %s/%s ", " §7Oldal %s/%s "),
            entry("lb.nextButton", "§7[§fNext ▶§7]", "§7[§fKövetkező ▶§7]"),
            entry("lb.nextDisabled", "§8Next ▶", "§8Következő ▶"),
            entry("lb.categories", "§7Categories: ", "§7Kategóriák: "),
            entry("lobby.playersOnly", "§cOnly players can use this command.", "§cEzt a parancsot csak játékosok használhatják."),
            entry("lobby.alreadyIn", "§7You're already in the lobby.", "§7Már a lobbiban vagy."),
            entry("lobby.welcomeBack", "§aWelcome back to the lobby.", "§aÜdv újra a lobbiban."),

            // ---- shop: supplies ----
            entry("shop.supply.needCoins", "§cYou need §e%s coins§c for a %s.", "§cEhhez §e%s érmére§c van szükséged (%s)."),
            entry("shop.roll.needCoins", "§cYou need §e%s coins§c to roll for a %s.", "§cEhhez §e%s érmére§c van szükséged a %s kipörgetéséhez."),
            entry("shop.roll.needPersistent", "§cYou need §6%s persistent coins§c to roll for a %s.", "§cEhhez §6%s maradandó érmére§c van szükséged a %s kipörgetéséhez."),
            entry("shop.fullInventory", "§cYour inventory is full — free up a slot, then click §aKEEP§c again (or choose §eSALVAGE§c).", "§cA hátizsákod tele van — szabadíts fel egy helyet, majd kattints ismét a §aMEGTARTÁS§c-ra (vagy válaszd a §eSELEJTEZÉS§c-t)."),
            entry("shop.kept", "§aYou kept the %s§a!", "§aMegtartottad: %s§a!"),
            entry("shop.salvageBanked", "§eSalvaged for §3%s shards§e. §7(Banked on boss defeat — lost if you die first.)", "§eSelejtezve §3%s szilánkért§e. §7(A főnök legyőzésekor bankba kerül — elvész, ha előbb meghalsz.)"),
            entry("shop.salvage", "§eSalvaged for §3%s shards§e.", "§eSelejtezve §3%s szilánkért§e."),

            // ---- shop: upgrades ----
            entry("upg.maxed", "§8That upgrade is already maxed.", "§8Ez a fejlesztés már maximalizálva van."),
            entry("upg.needShards", "§cYou need %s shards (have %s).", "§cEhhez %s szilánkra van szükséged (van %s)."),
            entry("upg.levelled", "§a%s §7is now §fLv %s§7.", "§a%s §7mostantól §f%s. szintű§7."),

            // ---- shop: repair ----
            entry("repair.holdDamaged", "§cHold a damaged persistent item in your main hand to repair it.", "§cTarts egy sérült maradandó tárgyat a fő kezedben a javításhoz."),
            entry("repair.notDamaged", "§cThat item is not damaged or is broken. Use 'Repair Broken Item' for broken items.", "§cEz a tárgy nem sérült vagy eltört. Törött tárgyakhoz használd a 'Törött tárgy javítása' opciót."),
            entry("repair.needCoins", "§cYou need %s coins to repair this item (repair #%s).", "§cEhhez %s érme kell a javításhoz (#%s. javítás)."),
            entry("repair.done", "§aRepaired item! §7(-§6%s coins§7) §7(repair #%s)", "§aA tárgyat megjavítottad! §7(-§6%s érme§7) §7(#%s. javítás)"),
            entry("repair.noneDamaged", "§cYou have no damaged persistent gear to repair.", "§cNincs sérült maradandó felszerelésed, amit meg lehetne javítani."),
            entry("repair.needAll", "§cYou need %s coins to repair all items.", "§cMindegyik tárgy javításához %s érme kell."),
            entry("repair.allDone", "§aRepaired %s item(s)! §7(-§6%s coins§7)", "§a%s tárgy javítva! §7(-§6%s érme§7)"),
            entry("repair.holdBroken", "§cHold a broken persistent item in your main hand to repair it.", "§cTarts egy eltört maradandó tárgyat a fő kezedben a javításhoz."),
            entry("repair.needBrokenCoins", "§cYou need %s persistent coins to repair a broken item.", "§cEgy eltört tárgy javításához %s maradandó érmére van szükséged."),
            entry("repair.needBrokenShards", "§cYou need %s shards to repair a broken item.", "§cEgy eltört tárgy javításához %s szilánkra van szükséged."),
            entry("repair.brokenDone", "§aRepaired broken item! §7(-§6%s coins§7, §3-%s shards§7) §7(+10 durability)", "§aEltört tárgy javítva! §7(-§6%s érme§7, §3-%s szilánk§7) §7(+10 tartósság)"),

            // ---- shop: potions ----
            entry("shop.needPersistentFor", "§cYou need §6%s persistent coins§c for a %s§c.", "§cEhhez §6%s maradandó érmére§c van szükséged (%s§c)."),
            entry("shop.purchased", "§aPurchased a %s§a! §7(-§6%s coins§7)", "§aMegvásároltad: %s§a! §7(-§6%s érme§7)"),

            // ---- workstation ----
            entry("workstation.unavailable", "§cThat item is no longer available.", "§cEz a tárgy már nem elérhető."),
            entry("workstation.changed", "§cThe selected item changed; please reselect it.", "§cA kijelölt tárgy megváltozott; válaszd ki újra."),

            // ---- stash ----
            entry("stash.took", "§7Took %s §7out of the stash.", "§7Kivetted a raktárból: %s §7."),
            entry("stash.full", "§cYour inventory is full — make room before taking items out of the stash.", "§cA hátizsákod tele van — szabadíts fel helyet, mielőtt kiveszel tárgyakat a raktárból."),

            // ---- stash GUI ----
            entry("stash.title", "§8Stash  §7(%s/%s items)", "§8Raktár  §7(%s/%s tárgy)"),
            entry("stash.fullDropped", "§cYour stash is full — your %s §c dropped. §7Open it with §f/stash§7 to make room.", "§cA raktárod tele van — a(z) %s §c leejtődött. §7Nyisd meg a §f/stash§7 paranccsal, hogy helyet csinálj."),
            entry("stash.stashed", "§8Your %s §8 was stashed. §7Retrieve it with §f/stash§7.", "§8A(z) %s §8 a raktárba került. §7Vedd ki a §f/stash§7 paranccsal."),
            entry("stash.reminder.one", "§8Your stash holds §f%s item§8. §7Click to open: §f/stash", "§8A raktáradban §f%s tárgy§8 vár. §7Kattints a megnyitáshoz: §f/stash"),
            entry("stash.reminder.many", "§8Your stash holds §f%s items§8. §7Click to open: §f/stash", "§8A raktáradban §f%s tárgy§8 vár. §7Kattints a megnyitáshoz: §f/stash"),

            // ---- shop GUI: titles ----
            entry("shop.title.run", "§8Shop  §7(Floor %s)  §e%s coins", "§8Bolt  §7(Emelet %s)  §e%s érme"),
            entry("shop.title.persistent", "§8Persistent Shop  §6%s pc  §3%s shards", "§8Maradandó Bolt  §6%s pc  §3%s szilánk"),
            entry("shop.titleSeparator", " §8— §f%s", " §8— §f%s"),
            entry("shop.supplies.titleSuffix", "Supplies", "Felszerelés"),

            // ---- shop GUI: category labels + menu entries ----
            entry("shop.category.weapon", "Weapons", "Fegyverek"),
            entry("shop.category.armor", "Armor", "Páncél"),
            entry("shop.category.manashield", "Mana Shields", "Mana Pajzsok"),
            entry("shop.category.weapon.article", "a weapon", "fegyvert"),
            entry("shop.category.armor.article", "an armor", "páncélt"),
            entry("shop.category.manashield.article", "a mana shield", "mana pajzsot"),
            entry("menuEntry.rollFor", "§7Roll for %s.", "§7Pörgetés erre: %s."),
            entry("menuEntry.enter", "§7Click to enter the %s roller.", "§7Kattints a %s pörgető megnyitásához."),

            // ---- shop GUI: common buttons ----
            entry("shop.back.name", "§7← Back to Shop", "§7← Vissza a Bolthoz"),
            entry("shop.back.lore", "§7Return to the shop menu", "§7Visszatérés a bolt menüjébe"),
            entry("shop.upgrades.name", "§fPermanent Upgrades", "§fMaradandó Fejlesztések"),
            entry("shop.upgrades.lore", "§7Spend shards on permanent stat boosts", "§7Költs szilánkokat maradandó tulajdonság-javításokra"),
            entry("shop.supplies.name", "§fSupplies", "§fFelszerelés"),
            entry("shop.supplies.lore1", "§7Keys, bombs, heals and run tonics.", "§7Kulcsok, bombák, gyógyítás és futam-tonikumok."),
            entry("shop.supplies.lore2", "§7Bought directly with §erun coins§7.", "§7Közvetlenül §efutam-érméből§7 vásárolható."),

            // ---- shop GUI: roll / keep / salvage ----
            entry("shop.roll.button", "§a§lROLL  §7(§e%s§7)", "§a§lPÖRGETÉS  §7(§e%s§7)"),
            entry("shop.roll.buttonDisabled", "§8§lROLL  §7(§e%s§7)", "§8§lPÖRGETÉS  §7(§e%s§7)"),
            entry("shop.roll.why", "§7Rolls a %s, then KEEP or SALVAGE.", "§7Pörget egy %s-t, majd MEGTARTÁS vagy SELEJTEZÉS."),
            entry("shop.roll.anItem", "piece of gear", "felszerelési tárgyat"),
            entry("shop.roll.cantAfford", "§cCan't afford — need %s more.", "§cNem futja — még %s kell."),
            entry("shop.roll.runCoinsLost", "§eRun coins §7are lost on death.", "§eA futam érmék §7halálkor elvésznek."),
            entry("shop.roll.persistentBase", "§6Persistent rolls §7produce base-quality gear.", "§6A maradandó pörgetés §7alapszintű felszerelést ad."),
            entry("shop.rolling", "§7Rolling...", "§7Pörgetés..."),
            entry("shop.categoryPreview.ready", "§7Ready to roll.", "§7Kész a pörgetésre."),
            entry("shop.keep.name", "§a§lKEEP", "§a§lMEGTARTÁS"),
            entry("shop.keep.lore", "§7Keep the %s§7 in your inventory.", "§7Tartsd meg a %s§7-t a hátizsákodban."),
            entry("shop.salvage.name", "§e§lSALVAGE", "§e§lSELEJTEZÉS"),
            entry("shop.salvage.lore", "§7Destroy the item for §3%s shards§7.", "§7Semmisítsd meg a tárgyat §3%s szilánkért§7."),

            // ---- shop GUI: supplies ----
            entry("shop.supply.key.name", "§9§lKey", "§9§lKulcs"),
            entry("shop.supply.key.lore", "§7Opens a locked door.", "§7Kinyit egy zárt ajtót."),
            entry("shop.supply.bomb.name", "§4§lBomb", "§4§lBomba"),
            entry("shop.supply.bomb.lore", "§7Blows up cracked walls hiding secret rooms.", "§7Felrobbantja a rejtekhelyeket rejtő repedt falakat."),
            entry("shop.supply.heart.name", "§c§lRed Heart", "§c§lPiros Szív"),
            entry("shop.supply.heart.lore", "§7Restores §c8 HP §7instantly.", "§7Azonnal visszaállít §c8 HP-t§7."),
            entry("shop.supply.mana.name", "§b§lMana Potion", "§b§lMana-ital"),
            entry("shop.supply.mana.lore", "§7Refills your mana to max.", "§7Maximumra tölti a manádat."),
            entry("shop.supply.dmgTonic.name", "§c§lDamage Tonic", "§c§lSebzés-tonikum"),
            entry("shop.supply.dmgTonic.lore", "§7+%s melee damage §7for the rest of the FLOOR.", "§7+%s közelharci sebzés §7az EMElet hátralévő részében."),
            entry("shop.supply.defTonic.name", "§a§lDefense Tonic", "§a§lVédelem-tonikum"),
            entry("shop.supply.defTonic.lore", "§7+%s defense §7for the rest of the FLOOR.", "§7+%s védelem §7az EMElet hátralévő részében."),
            entry("shop.supply.have", "%sYou have: §f%s", "%sA birtokodban: §f%s"),
            entry("shop.buy.afford", "§e%s %s §7— click to buy", "§e%s %s §7— kattints a vásárláshoz"),
            entry("shop.buy.cant", "§cCan't afford — need %s more.", "§cNem futja — még %s kell."),

            // ---- shop GUI: repair buttons ----
            entry("shop.repairItem.name", "§aRepair Item", "§aTárgy Javítása"),
            entry("shop.repairItem.lore1", "§7Repairs the held item · cost scales with repair count", "§7Javítja a kezedben lévő tárgyat · az ár a javítások számától függ"),
            entry("shop.repairItem.held", "§7Held: §f%s", "§7Kézben: §f%s"),
            entry("shop.repairItem.durability", "§7Durability: §f%s§7/§f%s", "§7Tartósság: §f%s§7/§f%s"),
            entry("shop.repairItem.repairNum", "§7Repair #%s: §6%s coins", "§7%s. javítás: §6%s érme"),
            entry("shop.repairItem.broken", "§cHeld item is broken — use 'Repair Broken Item'", "§cA kézben lévő tárgy eltört — használd a 'Törött tárgy javítása' opciót"),
            entry("shop.repairItem.fullDura", "§aHeld item is at full durability", "§aA kézben lévő tárgy teljes tartósságú"),
            entry("shop.repairItem.holdDamaged1", "§7Hold a damaged persistent item", "§7Tarts egy sérült maradandó tárgyat"),
            entry("shop.repairItem.holdDamaged2", "§7to see the repair cost", "§7a javítási ár megtekintéséhez"),
            entry("shop.repairAll.name", "§bRepair All", "§bMindegyik Javítása"),
            entry("shop.repairAll.lore1", "§7Repairs all damaged persistent gear · cost scales with repair count", "§7Javítja az összes sérült maradandó felszerelést · az ár a javítások számától függ"),
            entry("shop.repairAll.items", "§7Items to repair: §f%s", "§7Javítandó tárgyak: §f%s"),
            entry("shop.repairAll.total", "§7Total cost: §6%s coins", "§7Összköltség: §6%s érme"),
            entry("shop.repairAll.none", "§7No damaged items found", "§7Nincs sérült tárgy"),

            // ---- shop GUI: potions ----
            entry("shop.potion.forest.name", "§aForest Transmutation Elixir", "§aErdei Átalakító Elixír"),
            entry("shop.potion.forest.lore1", "§7Transforms wood blocks into new", "§7A fa blokkokat új"),
            entry("shop.potion.forest.lore2", "§7tree varieties on your plot.", "§7fafajtákká alakítja a telkeden."),
            entry("shop.potion.stone.name", "§7Stone Transmutation Elixir", "§7Kő Átalakító Elixír"),
            entry("shop.potion.stone.lore1", "§7Transforms stone blocks into new", "§7A kő blokkokat új"),
            entry("shop.potion.stone.lore2", "§7stone and ore variants on your plot.", "§7kő- és ércfajtákká alakítja a telkeden."),
            entry("shop.currency.run", "run coins", "futam érme"),
            entry("shop.currency.persistent", "persistent coins", "maradandó érme"),
            entry("shop.currency.run.short", "coins", "érme"),
            entry("shop.currency.persistent.short", "pc", "pc"),
            entry("shop.roll.pricePer", "§7per roll.", "§7pörgetésenként."),

            // ---- upgrades GUI ----
            entry("shop.upgrades.title", "§8Upgrades  §3%s shards", "§8Fejlesztések  §3%s szilánk"),
            entry("upg.label.damage", "Melee Damage", "Közelharci Sebzés"),
            entry("upg.label.magic_damage", "Magic Damage", "Varázssebzés"),
            entry("upg.label.hearts", "Max Hearts", "Max Szívek"),
            entry("upg.label.defense", "Defense", "Védelem"),
            entry("upg.label.crit", "Crit Chance", "Kritikus Esély"),
            entry("upg.label.speed", "Move Speed", "Mozgási Sebesség"),
            entry("upg.label.mana", "Max Mana", "Max Mana"),
            entry("upg.effect.damage", "+%s melee damage", "+%s közelharci sebzés"),
            entry("upg.effect.magic_damage", "+%s magic damage", "+%s varázssebzés"),
            entry("upg.effect.hearts", "+%s max HP", "+%s max HP"),
            entry("upg.effect.defense", "+%s defense", "+%s védelem"),
            entry("upg.effect.crit", "+%s%% crit chance", "+%s%% kritikus esély"),
            entry("upg.effect.speed", "+%s%% move speed", "+%s%% mozgási sebesség"),
            entry("upg.effect.mana", "+%s max mana", "+%s max mana"),
            entry("upg.lvLine", "§7Lv §f%s§7/§f%s §8· §7Effect: %s", "§7%s. §fszint§7/§f%s §8· §7Hatás: %s"),
            entry("upg.maxedBtn", "§8§lMAXED §7%s", "§8§lMAX §7%s"),
            entry("upg.clickUpgrade", "§b%s shards §7— click to upgrade", "§b%s szilánk §7— kattints a fejlesztéshez"),
            entry("upg.needMore", "§cNeed %s more shards", "§cMég %s szilánk kell"),
            entry("upg.back.name", "§7← Back to Shop", "§7← Vissza a Bolthoz"),
            entry("upg.back.lore", "§7Return to the main shop", "§7Visszatérés a fő bolt menüjébe"),

            // ---- workstation GUI labels + descriptions ----
            entry("ws.label.upgrade", "UPGRADE", "FEJLESZTÉS"),
            entry("ws.label.reforge", "REFORGE", "ÚJRAKOVÁCSOLÁS"),
            entry("ws.label.preserve", "PRESERVE", "MEGŐRZÉS"),
            entry("ws.label.salvage", "SALVAGE", "SELEJTEZÉS"),
            entry("ws.label.storage", "PERSISTENT STORAGE", "MARADANDÓ RAKTÁR"),
            entry("ws.desc.upgrade", "Improve an item's core stat", "Javítja a tárgy fő tulajdonságát"),
            entry("ws.desc.reforge", "Reroll an item's affixes", "Újra pörgeti a tárgy toldásait"),
            entry("ws.desc.preserve", "Make an item persist past this run", "A tárgy túléli a futamot"),
            entry("ws.desc.salvage", "Destroy an item for shards", "Megsemmisít egy tárgyat szilánkokért"),
            entry("ws.desc.storage", "View persistent items (read-only in-run)", "Maradandó tárgyak megtekintése (futamban csak olvasható)"),
            entry("ws.title", "%s  §8§7%s pc  §3%s shards  §e%s coins", "%s  §8§7%s pc  §3%s szilánk  §e%s érme"),
            entry("ws.confirm", "§aCONFIRM", "§aJÓVÁHAGYÁS"),
            entry("ws.confirmAgain", "§cCONFIRM AGAIN", "§cJÓVÁHAGYÁS MÉGEGYSZER"),
            entry("ws.confirmDestructive", "§cClick once more to destroy/remove the item.", "§cKattints még egyszer a tárgy megsemmisítéséhez/eltávolításához."),
            entry("ws.confirmApply", "§7Apply the operation to the selected item", "§7A művelet alkalmazása a kijelölt tárgyra"),
            entry("ws.back.name", "§7← Back", "§7← Vissza"),
            entry("ws.back.lore", "§7Back to the item list", "§7Vissza a tárgylistához"),
            entry("ws.info.upgrade.cost", "§eRun coins + §3shards §7(scale with level AND floor)", "§eFutam érme + §3szilánk §7(a szint ÉS az emelet szerint nő)"),
            entry("ws.info.upgrade.effect", "§7Effect: §5+%s%%§7 core stat per level", "§7Hatás: §5+%s%%§7 fő tulajdonság szintenként"),
            entry("ws.info.reforge.cost", "§3%s shards§7 (+§3%s§7 per prior reforge)", "§3%s szilánk§7 (+§3%s§7 minden korábbi újrakovácsolásért)"),
            entry("ws.info.reforge.effect", "§7Rerolls affixes; keeps base stats, rarity, ability.", "§7Újra pörgeti a toldásokat; az alaptulajdonságok, ritkaság és képesség marad."),
            entry("ws.info.preserve.chance", "§7Chance: §a%s%%§7 · pity after %s fails", "§7Esély: §a%s%%§7 · irgalom %s kudarc után"),
            entry("ws.info.preserve.effect", "§aSuccess: §7persists past the run (§ehalf durability§7). §cFail: one rarity worse.", "§aSiker: §7túléli a futamot (§efél tartóssággal§7). §cKudarc: eggyel rosszabb ritkaság."),
            entry("ws.info.preserve.cost", "§e%s run coins §d+ %s pc §3+ %s shards", "§e%s futam érme §d+ %s pc §3+ %s szilánk"),
            entry("ws.info.salvage.effect", "§cDestroys the item §7for §erun coins§7 (lost on death — not counted toward the boss persistent reward). Value scales with rarity + stats. §cRequires confirmation.", "§cMegsemmisíti a tárgyat §7§eFutam érmékért§7 (halálkor elvész — nem számít bele a főnök maradandó jutalmába). Az érték a ritkasággal és a tulajdonságokkal nő. §cJóváhagyás szükséges."),
            entry("ws.info.storage.effect", "§cRead-only §7inside a run: you may view but not withdraw persistent items while in a run.", "§cCsak olvasható §7futam közben: futam alatt csak megtekintheted, nem veheted ki a maradandó tárgyakat."),
            entry("ws.selected", "§7Selected: §f%s", "§7Kijelölve: §f%s"),
            entry("ws.currentLevel", "§7Current level: §5%s", "§7Jelenlegi szint: §5%s"),
            entry("ws.stat.dmg", "§cDMG", "§cSEBZ"),
            entry("ws.stat.magicdmg", "§dMagic DMG", "§dVarázs SEBZ"),
            entry("ws.stat.def", "§aDEF", "§aVÉD"),
            entry("ws.stat.shield", "§bShield", "§bPajzs"),
            entry("ws.stat.stat", "§7Stat", "§7Tulajdonság"),
            entry("ws.stat.value", "%s: §f%s §7(+%s)", "%s: §f%s §7(+%s)"),
            entry("ws.nextLevel", "§5Next: Lv %s §7(+%s%% core stat)", "§5Következő: %s. szint §7(+%s%% fő tulajdonság)"),
            entry("ws.upgrade.cost.persistent", "§e%s run coins §3+ %s shards §7(§6persistent§7, 2x)", "§e%s futam érme §3+ %s szilánk §7(§6maradandó§7, 2x)"),
            entry("ws.upgrade.cost.normal", "§e%s run coins §3+ %s shards", "§e%s futam érme §3+ %s szilánk"),
            entry("ws.reforge.newAffixes", "§bNew affixes: %s", "§bÚj toldások: %s"),
            entry("ws.reforge.cost.persistent", "§3Cost: %s shards §7(§6persistent§7, 2x)", "§3Ár: %s szilánk §7(§6maradandó§7, 2x)"),
            entry("ws.reforge.cost.normal", "§3Cost: %s shards", "§3Ár: %s szilánk"),
            entry("ws.reforge.prior", "§7(+§3%s§7 from %s prior reforge%s)", "§7(+§3%s§7 a %s korábbi újrakovácsolás%s miatt)"),
            entry("ws.preserve.attempt", "Attempts to preserve this item past the run.", "Megpróbálja a tárgyat a futam utánra megőrizni."),
            entry("ws.preserve.pity", "§6§l✦ PITY! §7Next attempt guaranteed!", "§6§l✦ IRGALOM! §7A következő próba garantált!"),
            entry("ws.preserve.chance", "§a%s%% §7· pity: §e%s§7 more fail%s → guaranteed", "§a%s%% §7· irgalom: §e%s§7 további kudarc%s → garantált"),
            entry("ws.preserve.effect", "§aSuccess: §7kept at half durability. §cFail: one rarity worse.", "§aSiker: §7fél tartóssággal marad. §cKudarc: eggyel rosszabb ritkaság."),
            entry("ws.salvage.value", "§e+ %s run coins §7(per-run, lost on death — not counted", "§e+ %s futam érme §7(futamonként, halálkor elvész — nem számít bele"),
            entry("ws.salvage.destroy", "§7toward boss persistent coin reward). §cThis destroys the item!", "§7a főnök maradandó éremjutalmába). §cEz megsemmisíti a tárgyat!"),

            // ---- sidebar HUD ----
            entry("hud.title", "§cDUNG §8Floor %s", "§cDUNG §8%s. szint"),
            entry("hud.dmgDef", "§7DMG §c%s§f/§b%s   §7DEF §a%s", "§7SEBZ §c%s§f/§b%s   §7VÉD §a%s"),
            entry("hud.crit", "§7Crit §f%s%% §b✕%s", "§7Krit §f%s%% §b✕%s"),
            entry("hud.reachSpd", "§7Reach §f%s   §7Spd §f%s", "§7Elérés §f%s   §7Temp §f%s"),
            entry("hud.coinsLine", "§e⛁ Coins §f%s   §9⛂ Keys §f%s §7[slot 7]", "§e⛁ Érme §f%s   §9⛂ Kulcs §f%s §7[7. slot]"),
            entry("hud.bombsLine", "§4✹ Bombs §f%s §7[slot 8]   §cKills §f%s", "§4✹ Bomba §f%s §7[8. slot]   §cÖlések §f%s"),
            entry("hud.room", "§6Room: §f%s", "§6Szoba: §f%s"),
            entry("hud.corridor", "§7Corridor", "§7Folyosó"),
            entry("hud.gear", "§7Gear: ", "§7Felszerelés: "),
            entry("hud.lockedHint", "§e🔒 Locked nearby (need key)", "§e🔒 Zárt szoba a közelben (kulcs kell)"),
            entry("hud.bossActive", "§4!! BOSS ACTIVE", "§4!! FŐNÖK AKTÍV"),
            entry("hud.class", "§7Class §f%s", "§7Osztály §f%s"),
            entry("hud.ready", "§7%s §aReady", "§7%s §aKÉSZ"),
            entry("hud.readyText", "§aReady", "§aKÉSZ"),
            entry("hud.cd", "§7%s §f%ss", "§7%s §f%ss"),

            // ---- sidebar room-type names (RoomType.label) ----
            entry("roomtype.START", "Spawn", "Indulás"),
            entry("roomtype.COMBAT", "Combat", "Harc"),
            entry("roomtype.TREASURE", "Treasure", "Kincs"),
            entry("roomtype.SHOP", "Shop", "Bolt"),
            entry("roomtype.SECRET", "Secret", "Rejtett"),
            entry("roomtype.ELITE", "Elite", "Elit"),
            entry("roomtype.BOSS", "Boss", "Főnök"),
            entry("roomtype.LOCKED", "Locked", "Zárt"),
            entry("roomtype.UPGRADE", "Upgrade", "Fejlesztés"),

            // ---- sidebar class ability names ----
            entry("ability.warrior", "War Cry", "Harci Riadó"),
            entry("ability.mage", "Arcane Nova", "Ősi Nova"),
            entry("ability.ranger", "Shadow Step", "Árnylépés"),
            entry("ability.class", "Class", "Osztály"),

            // ---- sidebar class names (lowercase ids are capitalized in code) ----
            entry("class.warrior", "Warrior", "Harcos"),
            entry("class.mage", "Mage", "Mágus"),
            entry("class.ranger", "Ranger", "Tolvaj"),

            // ---- in-run: weapon / gear state ----
            entry("gear.weaponBroken", "§cYour weapon is broken — repair it at §6/shop§7 before attacking.", "§cA fegyvered eltört — javítsd meg a §6/shop§7 menüben, mielőtt támadsz."),
            entry("gear.itemBroken", "§cThis item is broken — repair it at §6/shop§7 before using its ability.", "§cEz a tárgy eltört — javítsd meg a §6/shop§7 menüben, mielőtt a képességét használod."),
            entry("gear.itemBroke", "§cYour %s §cbroke and can no longer be used! §7Repair at §6/shop§7 (150 coins + 100 shards for 10 durability).", "§c%s §celtört, és már nem használható! §7Javítsd meg a §6/shop§7 menüben (150 érme + 100 szilánk 10 tartósságért)."),
            entry("gear.armorBroke", "§cYour %s §cbroke%s! §7Repair at §6/shop§7 (150 coins + 100 shards for 10 durability).", "§c%s §celtört%s! §7Javítsd meg a §6/shop§7 menüben (150 érme + 100 szilánk 10 tartósságért)."),
            entry("gear.fromDescent", " from the descent", " a leszállás során"),
            entry("gear.soulSiphonFull", "§cSoul Siphon is full! Shift+left-click to heal yourself with its stored health.", "§cA Soul Siphon megtelt! Guggolás + bal kattintás a gyógyuláshoz a tárolt egészségből."),
            entry("gear.manaShieldBroke", "§cYour mana shield broke! §7Repair at §6/shop§7 (150 coins + 100 shards for 10 durability).", "§cA mana pajzsod eltört! §7Javítsd meg a(z) §6/shop§7 menüben (150 érme + 100 szilánk 10 tartósságért)."),

            // ---- in-run: ability feedback ----
            entry("ability.noAbility", "§cYour hand item has no ability.", "§cA kezedben lévő tárgynak nincs képessége."),
            entry("ability.noManaCd", "§cNot enough mana or on cooldown.", "§cNincs elég manád, vagy töltődik."),
            entry("ability.tooFast", "§cToo fast!", "§cTúl gyors!"),
            entry("ability.noClass", "§cYour class has no active ability.", "§cAz osztályodnak nincs aktív képessége."),
            entry("ability.noTarget", "§cNo target in range!", "§cNincs célpont a hatótávolságon belül!"),
            entry("ability.warCryBoost", "§6War Cry! Damage boosted by 30% for 5s!", "§6Harci Riadó! A sebzés 30%-kal nőtt 5 mp-ig!"),
            entry("ability.warCry", "§6§lWAR CRY!", "§6§lHARCI RIADÓ!"),
            entry("ability.arcaneNova", "§d§lARCANE NOVA!", "§d§lŐSI NOVA!"),
            entry("ability.shadowStepWarden", "§aShadow Stepped behind the Warden!", "§aÁrnyékot léptél a Warden mögé!"),
            entry("ability.shadowStepNoEnemies", "§aShadow Step — no enemies nearby.", "§aÁrnyéklépés — nincs ellenség a közelben."),
            entry("ability.shadowStep", "§b§lSHADOW STEP!", "§b§lÁRNYÉKLÉPÉS!"),
            entry("ability.shadowSteppedBehind", "§aShadow Stepped behind %s§a!", "§aÁrnyékot léptél %s§a mögé!"),
            entry("ability.rush", "§6Rush!", "§6Roham!"),
            entry("ability.slash", "§6Slash!", "§6Vágás!"),
            entry("ability.cleave", "§6Cleave!", "§6Csobbantó!"),
            entry("ability.smash", "§6Smash!", "§6Összecsapás!"),
            entry("ability.bladeStorm", "§6Blade Storm!", "§6Pengévihar!"),
            entry("ability.arcaneBolt", "§6Arcane Bolt!", "§6Ősi Nyíl!"),
            entry("ability.ravage", "§6Ravage!", "§6Pusztítás!"),
            entry("ability.chainLightning", "§6Chain Lightning!", "§6Láncvillám!"),
            entry("ability.fireball", "§6Fireball!", "§6Tűzgolyó!"),
            entry("ability.lifeDrain", "§6Life Drain! §7Siphoned §c%s❤ §7→ Stored §c%s§7/§f%s§7❤", "§6Vámpirizmus! §7Leszívva §c%s❤ §7→ Tárolva §c%s§7/§f%s§7❤"),
            entry("ability.lightning", "§e§lLIGHTNING!", "§e§lVILLÁM!"),
            entry("ability.generic", "§6Ability!", "§6Képesség!"),

            // ---- in-run: workstation actions (upgrade / reforge / preserve / salvage) ----
            entry("ws.cantUpgrade", "§cThat item can't be upgraded.", "§cEz a tárgy nem fejleszthető."),
            entry("ws.maxUpgrade", "§5This item is already at max upgrade level.", "§5Ez a tárgy már maximális fejlesztési szinten van."),
            entry("ws.cantReforge", "§cThat item can't be reforged.", "§cEz a tárgy nem újrakovácsolható."),
            entry("ws.cantPreserve", "§cThat item can't be preserved.", "§cEz a tárgy nem őrizhető meg."),
            entry("ws.cantSalvage", "§cThat item can't be salvaged.", "§cEz a tárgy nem selejtezhető."),
            entry("ws.needRunCoins", "§cYou need §e%s run coins§c (have §e%s§c).", "§cEhhez §e%s futam érmére§c van szükséged (van §e%s§c)."),
            entry("ws.needShards", "§cYou need §3%s shards§c (have §b%s§c).", "§cEhhez §3%s szilánkra§c van szükséged (van §b%s§c)."),
            entry("ws.needPersistentCoins", "§dYou need §b%s persistent coins§d (have §b%s§d).", "§dEhhez §b%s maradandó érmére§d van szükséged (van §b%s§d)."),
            entry("ws.upgraded", "§aUpgraded to §5Lv %s§a! §7(-§e%s coins§7, §3-%s shards§7)%s", "§aFejlesztve %s. §5szintre§a! §7(-§e%s érme§7, §3-%s szilánk§7)%s"),
            entry("ws.reforged", "§bReforged! §7New affixes: %s §7(-§3%s shards§7)%s", "§bÚjrakovácsolva! §7Új toldások: %s §7(-§3%s szilánk§7)%s"),
            entry("ws.persistNote", " §7(§6persistent§7, 2x cost)", " §7(§6maradandó§7, 2x ár)"),
            entry("ws.preserved", "§d§l✦ PRESERVED! §dYour item will persist past this run (at half durability).%s", "§d§l✦ MEGŐRIZVE! §dA tárgyad túléli ezt a futamot (fél tartóssággal).%s"),
            entry("ws.preservedPity", " §6§l✦ PITY! §7Guaranteed after %s fails!", " §6§l✦ IRGALOM! §7%s kudarc után garantált!"),
            entry("ws.preservedDeliver", "§7  You'll receive it when the run ends.", "§7  A futam végén kapod meg."),
            entry("ws.preserveFailed", "§cThe preserve failed. Your item was returned, §lone rarity worse§r§c.%s", "§cA megőrzés kudarcot vallott. A tárgyat visszakaptad, §lritkasága eggyel rosszabb§r§c.%s"),
            entry("ws.preservePityLeft", " §7(Pity: §e%s§7 more fail%s → guaranteed)", " §7(Irgalom: §e%s§7 további kudarc%s → garantált)"),
            entry("ws.salvaged", "§aSalvaged the item §e→ +%s run coins§7 (total §e%s§7).", "§aA tárgy selejtezve §e→ +%s futam érme§7 (összesen §e%s§7)."),

            // ---- in-run: boss fight / banking / descend ----
            entry("boss.beamStrikes", "§cThe Warden's beam strikes through you!", "§cA Warden sugara áthatol rajtad!"),
            entry("boss.telegraphBeam", "§cThe Warden telegraphs a beam to the §4%s§c!", "§cA Warden sugarat céloz §4%s§c felé!"),
            entry("boss.coreFlares", "§cThe Warden's core flares!", "§cA Warden magja fellobban!"),
            entry("dir.east", "East", "Kelet"),
            entry("dir.southEast", "South-East", "Dél-Kelet"),
            entry("dir.south", "South", "Dél"),
            entry("dir.southWest", "South-West", "Dél-Nyugat"),
            entry("dir.west", "West", "Nyugat"),
            entry("dir.northWest", "North-West", "Észak-Nyugat"),
            entry("dir.north", "North", "Észak"),
            entry("dir.northEast", "North-East", "Észak-Kelet"),
            entry("boss.bankedCoins", "§dYou banked §6%s§d coins into your persistent coins.", "§d§6%s§d érmét tettél a maradandó érméid közé."),
            entry("boss.bankedShards", "§dYou banked §b%s§d shards from salvaged gear.", "§d§b%s§d szilánkot zsákmányoltál a selejtezett felszerelésből."),
            entry("boss.revived", "§a§lYou have been revived by the boss's defeat!", "§a§lA főnök legyőzése életre keltett téged!"),
            entry("boss.crackOpens", "§dA crack opens below... ", "§dRepedés nyílik alattad... "),
            entry("boss.descendBtn", "[Descend]", "[Folytatás]"),
            entry("boss.descendHover", "Click to descend to the next floor", "Kattints a következő szintre való leszálláshoz"),
            entry("boss.endBtn", " [End Run]", " [Futam Vége]"),
            entry("boss.endHover", "Leave the run and return to the hub", "Hagyd el a futamot, és térj vissza a központba"),

            // ---- in-run: shield switch / leaving ----
            entry("shield.prompt", "§7A better shield (§b%s§7) is in your inventory. Your §e%s§7 is persistent — keep it or switch? ", "§7Egy jobb pajzs (§b%s§7) van a hátizsákodban. A(z) §e%s§7 maradandó — megtartod vagy lecseréled? "),
            entry("shield.switchBtn", "§a[Switch]", "§a[Cserélj]"),
            entry("shield.switchHover", "Swap in the better shield", "Cseréld be a jobb pajzsot"),
            entry("shield.noneEquipped", "§7No persistent shield equipped to switch.", "§7Nincs maradandó pajzs felszerelve, amit cserélni lehetne."),
            entry("shield.noBetter", "§7No better shield available.", "§7Nincs jobb pajzs elérhető."),
            entry("shield.switched", "§aSwitched to §b%s§a.", "§aLecserélted: §b%s§a."),
            entry("leave.early", "§7  Left the run early: persistent gear durability reduced by 5%", "§7  Korán hagytad el a futamot: a maradandó felszerelés tartóssága 5%-kal csökkent"),
            entry("party.allFallen", "§c§lAll party members have fallen! The run is over.", "§c§lAz összes csapattag elesett! A futam véget ért."),
            entry("gear.persistedDelivery", "§d§l✦ §dPERSISTED! §7Your %s §7arrived safe and sound.", "§d§l✦ §dMEGŐRIZVE! §7A(z) %s §7épségben megérkezett."),

            // ---- tab menu (TabUI) ----
            entry("tab.title", "§cDUNG §7— §fFloor %s%s", "§cDUNG §7— §f%s. szint%s"),
            entry("tab.titleClass", " §8Class §f%s", " §8Osztály §f%s"),
            entry("tab.build", "§cDMG §f%s§7/§b%s   §aDEF §f%s   §fCRIT §f%s%%§fx%s", "§cSEBZ §f%s§7/§b%s   §aVÉD §f%s   §fKRIT §f%s%%§fx%s"),
            entry("tab.mana", "§bMana §f%s/%s   §6Speed §f%s   §7FireRate §f%st", "§bMana §f%s/%s   §6Temp §f%s   §7Tűzráta §f%st"),
            entry("tab.consumables", "§eCoins %s   §9Keys %s   §4Bombs %s", "§eÉrme %s   §9Kulcs %s   §4Bomba %s"),
            entry("tab.equipment", "§6Equipment", "§6Felszerelés"),
            entry("tab.mainhand", "   §fMainhand: %s", "   §fFő kéz: %s"),
            entry("tab.armorSlot", "   §f%s: %s", "   §f%s: %s"),
            entry("tab.armor.boots", "Boots", "Csizma"),
            entry("tab.armor.legs", "Legs", "Nadrág"),
            entry("tab.armor.chest", "Chest", "Mellvért"),
            entry("tab.armor.helmet", "Helmet", "Sisak"),
            entry("tab.dungeon", "§6Dungeon  §7(F%s)", "§6TÖMLÖC  §7(F%s)"),
            entry("tab.roomsExplored", "   §fRooms explored §7%s/%s   §aCleared §7%s", "   §fFelfedezett szobák §7%s/%s   §aKész §7%s"),
            entry("tab.boss", "   §cBoss: %s", "   §cFőnök: %s"),
            entry("tab.boss.engaged", "§4ENGAGED", "§4HARCBAN"),
            entry("tab.boss.awaiting", "§6AWAITING", "§6VÁR"),
            entry("tab.boss.hidden", "§8hidden", "§8rejtve"),
            entry("tab.abilityCd", "§6%s §7%s", "§6%s §7%s"),
            entry("tab.controls", "§8Sneak+Q=Class  Sneak+RMB=Weapon  Click=Attack", "§8Guggolás+Q=Osztály  Guggolás+JOBB EGÉR=Fegyver  Kattintás=Támadás"),
            entry("tab.durability", "§7Durability: ", "§7Tartósság: "),

            // ---- gear item lore (localized on pickup / /language) ----
            entry("gear.damage", "§7Damage: §c%s", "§7Sebzés: §c%s"),
            entry("gear.magicDamage", "§7Magic Damage: §d%s", "§7Varázssebzés: §d%s"),
            entry("gear.defense", "§7Defense: §a%s", "§7Védekezés: §a%s"),
            entry("gear.health", "§7Health: §a+%s", "§7Életerő: §a+%s"),
            entry("gear.shieldCapacity", "§7Shield Capacity: §b%s", "§7Pajzskapacitás: §b%s"),
            entry("gear.upgrade", "§5✦ §5Upgrade §d%s", "§5✦ §5Fejlesztés §d%s"),
            entry("gear.ability", "§7Ability: §6%s §8(§b%s mana§8)", "§7Képesség: §6%s §8(§b%s mana§8)"),
            entry("gear.how", "§8How: §7Sneak + Right-Click", "§8Hogyan: §7Guggolás + Jobb Egér"),
            entry("gear.stored", "§7Stored: §c%s§7/§f%s§7❤", "§7Tárolt: §c%s§7/§f%s§7❤"),
            entry("gear.perfection", "of Perfection", "a Tökéletességről"),
            entry("gear.shield.activate", "§7Hotbar slot 9 to activate", "§7Gyorssáv 9. mezője az aktiváláshoz"),
            entry("gear.shield.charge", "§7Sneak to charge shield with mana", "§7Guggolva töltsd a pajzsot manával"),
            entry("gear.shield.absorb", "§7Absorbs damage while active", "§7Aktív állapotban elnyeli a sebzést"),

            // ---- gear ability usage descriptions ----
            entry("gear.use.rush", "§8     dash forward to dodge", "§8     előrevetődés a kitéréshez"),
            entry("gear.use.slash", "§8     a quick, heavy strike ahead", "§8     gyors, erős csapás előre"),
            entry("gear.use.cleave", "§8     slash everything in a cone ahead", "§8     mindent lecsap, ami előtted kúp alakban van"),
            entry("gear.use.smash", "§8     blast all nearby enemies", "§8     szétrobbantja a közeli ellenségeket"),
            entry("gear.use.bladeStorm", "§8     spin, damaging around you", "§8     pörgés, sebzés magad körül"),
            entry("gear.use.arcaneBolt", "§8     mage strike in a line", "§8     varázscsapás egy vonalban"),
            entry("gear.use.ravage", "§8     devastate every enemy in the room", "§8     minden ellenséget romba dönt a szobában"),
            entry("gear.use.chainLightning", "§8     strike a target, chaining to nearby enemies", "§8     célpontot sújt, és továbbugrik a közeli ellenségekre"),
            entry("gear.use.fireball", "§8     launch an explosive fireball", "§8     robbanó tűzgolyót lő ki"),
            entry("gear.use.lifeDrain", "§8     drain life from enemies, right-click ally to heal", "§8     életerőt szív az ellenségekből, jobb egérrel szövetségest gyógyít"),
            entry("gear.use.lightning", "§8     call a bolt of lightning down on your target", "§8     villámot idéz a célpontodra"),
            entry("gear.use.default", "§8     trigger a burst of damage", "§8     sebzéshullámot indít"),

            // ---- rarity names (used in the lore rarity line) ----
            entry("gear.rar.common", "Common", "Közönséges"),
            entry("gear.rar.uncommon", "Uncommon", "Szokatlan"),
            entry("gear.rar.rare", "Rare", "Ritka"),
            entry("gear.rar.epic", "Epic", "Epikus"),
            entry("gear.rar.legendary", "Legendary", "Legendás"),
            entry("gear.rar.mythic", "Mythic", "Mítikus"),

            // ---- gear item display names (base name of the item, shown colored by rarity) ----
            entry("item.frayed_blade", "Frayed Blade", "Kopott Penge"),
            entry("item.crude_axe", "Crude Axe", "Durva Fejsze"),
            entry("item.longsword", "Longsword", "Hosszúkard"),
            entry("item.war_hammer", "War Hammer", "Háborús Pöröly"),
            entry("item.crystal_shard", "Crystal Shard", "Kristályszilánk"),
            entry("item.arcane_staff", "Arcane Staff", "Ősi Pálca"),
            entry("item.doomblade", "Doomblade", "Végzetpengé"),
            entry("item.storm_rod", "Storm Rod", "Viharpálca"),
            entry("item.blaze_staff", "Blaze Staff", "Lángpálca"),
            entry("item.soul_siphon", "Soul Siphon", "Lélekszívó"),
            entry("item.cloth", "Cloth", "Textil"),
            entry("item.chain", "Chain", "Lánc"),
            entry("item.iron", "Iron", "Vas"),
            entry("item.golden", "Golden", "Arany"),
            entry("item.diamond", "Diamond", "Gyémánt"),
            entry("item.netherite", "Netherite", "Necrit"),
            entry("item.mana_shield", "Mana Shield", "Mana Pajzs"),
            entry("item.key", "Key", "Kulcs"),
            entry("item.bomb", "Bomb", "Bomba"),
            entry("item.slot.empty", "Empty", "Üres"),
            entry("item.slot.equipShield", "Equip Shield", "Pajzs felszerelése")
    );

    private static Map.Entry<String, Map<Language, String>> entry(String key,
                                                                  String en, String hu) {
        return Map.entry(key, Map.of(Language.ENGLISH, en, Language.MAGYAR, hu));
    }

    /** Get a localized string for the given language, replacing {@code %s} placeholders in order.
     *  Falls back to English (then the key itself) when a translation is missing. */
    public static String get(Language lang, String key, Object... args) {
        String text = text(lang, key);
        if (text == null) text = text(Language.ENGLISH, key);
        if (text == null) text = key;
        if (args.length == 0) return text;
        return String.format(text, args);
    }

    /** Resolve the player's stored language and localize a message for them. */
    public static String forPlayer(Player p, String key, Object... args) {
        return get(languageOf(p), key, args);
    }

    /** The language the given player has chosen (defaulting to English). */
    public static Language languageOf(Player p) {
        Dung plugin = Dung.instance();
        if (plugin == null) return Language.defaultLang();
        MetaManager meta = plugin.meta();
        if (meta == null) return Language.defaultLang();
        MetaManager.MetaProfile prof = meta.profile(p.getUniqueId());
        String code = prof.language;
        for (Language lang : Language.values()) {
            if (lang.code.equals(code)) return lang;
        }
        return Language.defaultLang();
    }

    private static String text(Language lang, String key) {
        Map<Language, String> byLang = CATALOG.get(key);
        return byLang == null ? null : byLang.get(lang);
    }
}
