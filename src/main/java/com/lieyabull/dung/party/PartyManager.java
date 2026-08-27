package com.lieyabull.dung.party;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages all active parties, invites, and membership operations.
 * Each player can belong to at most one party at a time.
 */
public final class PartyManager {
    private final Map<UUID, Party> playerParties = new HashMap<>(); // player -> party
    private final Map<UUID, Party> partyById = new HashMap<>();     // party id -> party
    private final Map<UUID, UUID> pendingInvites = new HashMap<>(); // invitee -> inviter

    /** Create a new party with the given player as leader. */
    public Party createParty(Player leader) {
        if (playerParties.containsKey(leader.getUniqueId())) {
            return null; // already in a party
        }
        Party p = new Party(leader);
        partyById.put(p.id(), p);
        playerParties.put(leader.getUniqueId(), p);
        return p;
    }

    /** Get the party a player belongs to, or null. */
    public Party partyOf(Player p) {
        return partyOf(p.getUniqueId());
    }

    public Party partyOf(UUID uuid) {
        return playerParties.get(uuid);
    }

    /** Send an invite. If the inviter is not in a party, automatically creates one first. */
    public boolean invite(Player inviter, Player invitee) {
        Party p = partyOf(inviter);
        if (p == null) {
            p = createParty(inviter);
            if (p == null) return false;
        }
        if (!p.isLeader(inviter.getUniqueId())) return false;
        if (partyOf(invitee) != null) return false;
        if (p.size() >= Party.MAX_SIZE) return false;
        pendingInvites.put(invitee.getUniqueId(), inviter.getUniqueId());
        return true;
    }

    /** Accept a pending invite. Returns false if no invite exists or party is full. */
    public boolean acceptInvite(Player invitee) {
        UUID inviterId = pendingInvites.remove(invitee.getUniqueId());
        if (inviterId == null) return false;
        if (partyOf(invitee) != null) {
            invitee.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(invitee, "party.alreadyIn"));
            return false;
        }
        Party p = partyOf(inviterId);
        if (p == null || p.size() >= Party.MAX_SIZE) return false;
        if (!p.addMember(invitee)) return false;
        playerParties.put(invitee.getUniqueId(), p);
        p.broadcastLocalized("party.joined", invitee.getName());
        return true;
    }

    /** Decline a pending invite. */
    public void declineInvite(Player p) {
        pendingInvites.remove(p.getUniqueId());
    }

    /** Remove a player from their party. If the party becomes empty, disband it. */
    public void leaveParty(Player p) {
        Party party = partyOf(p);
        if (party == null) return;
        boolean wasLeader = party.leader().equals(p.getUniqueId());
        party.removeMember(p.getUniqueId());
        playerParties.remove(p.getUniqueId());
        if (party.isEmpty()) {
            partyById.remove(party.id());
        } else {
            party.broadcastLocalized("party.left", p.getName());
            if (wasLeader) {
                try {
                    Player newLeader = org.bukkit.Bukkit.getPlayer(party.leader());
                    if (newLeader != null) {
                        party.broadcastLocalized("party.newLeader", newLeader.getName());
                    }
                } catch (Exception ignored) {
                    // Bukkit not initialized (e.g. during testing)
                }
            }
        }
    }

    /** Kick a player from the party (leader only). */
    public boolean kick(Player leader, Player target) {
        Party p = partyOf(leader);
        if (p == null || !p.isLeader(leader.getUniqueId())) return false;
        if (!p.isMember(target.getUniqueId())) return false;
        boolean leaderWasKicked = p.leader().equals(target.getUniqueId());
        p.removeMember(target.getUniqueId());
        playerParties.remove(target.getUniqueId());
        target.sendMessage(com.lieyabull.dung.lang.Lang.forPlayer(target, "party.kicked"));
        p.broadcastLocalized("party.kickedBroadcast", target.getName());
        if (p.isEmpty()) {
            partyById.remove(p.id());
        } else if (leaderWasKicked) {
            try {
                Player newLeader = org.bukkit.Bukkit.getPlayer(p.leader());
                if (newLeader != null) {
                    p.broadcastLocalized("party.newLeader", newLeader.getName());
                }
            } catch (Exception ignored) {
                // Bukkit not initialized (e.g. during testing)
            }
        }
        return true;
    }

    /** Disband the party entirely (leader only). Removes every member from the party
     *  and clears the internal member list so the Party object is fully empty. */
    public boolean disband(Player leader) {
        Party p = partyOf(leader);
        if (p == null || !p.isLeader(leader.getUniqueId())) return false;
        p.broadcastLocalized("party.disbanded");
        for (UUID uid : p.members()) {
            playerParties.remove(uid);
        }
        // Clear the party's internal member list so the Party object is truly empty
        for (UUID uid : List.copyOf(p.members())) {
            p.removeMember(uid);
        }
        partyById.remove(p.id());
        return true;
    }

    /** Check if a player has a pending invite from someone. */
    public boolean hasPendingInvite(Player p) {
        return pendingInvites.containsKey(p.getUniqueId());
    }

    /** Get the inviter's name for a pending invite. */
    public UUID getInviter(Player p) {
        return pendingInvites.get(p.getUniqueId());
    }

    /** Clean up when a player disconnects. */
    public void onPlayerQuit(Player p) {
        pendingInvites.remove(p.getUniqueId());
        Party party = partyOf(p);
        if (party != null) {
            leaveParty(p);
        }
    }

    /** Called after a player leaves a dungeon instance. Removes the player from the
     *  playerParties map (the party's member list was already cleared by the instance).
     *  If the party is now empty, also removes it from partyById so it won't be reused
     *  when the player starts a new run. */
    public void cleanupAfterLeave(Player p) {
        UUID uid = p.getUniqueId();
        Party party = playerParties.remove(uid);
        if (party != null && party.isEmpty()) {
            partyById.remove(party.id());
        }
    }
}