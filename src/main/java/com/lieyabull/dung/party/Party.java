package com.lieyabull.dung.party;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A party is a group of players sharing a dungeon run. The leader controls invites and kicks.
 * Parties are disbanded when all members leave or the leader disbands.
 */
public final class Party {
    public static final int MAX_SIZE = 4;

    private final UUID id;
    private final List<UUID> members = new ArrayList<>();
    private UUID leader;

    public Party(Player leader) {
        this.id = UUID.randomUUID();
        this.leader = leader.getUniqueId();
        this.members.add(leader.getUniqueId());
    }

    public UUID id() { return id; }
    public UUID leader() { return leader; }
    public List<UUID> members() { return Collections.unmodifiableList(members); }
    public int size() { return members.size(); }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    /** Add a player to the party. Returns false if the party is full. */
    public boolean addMember(Player p) {
        if (members.size() >= MAX_SIZE) return false;
        if (members.contains(p.getUniqueId())) return false;
        members.add(p.getUniqueId());
        return true;
    }

    /** Remove a player from the party. If the leader leaves, promote the next member. */
    public void removeMember(UUID uuid) {
        members.remove(uuid);
        if (members.isEmpty()) return;
        if (leader.equals(uuid)) {
            leader = members.get(0);
        }
    }

    /** Transfer leadership to another member. */
    public boolean transferLeadership(UUID newLeader) {
        if (!members.contains(newLeader)) return false;
        leader = newLeader;
        return true;
    }

    /** Broadcast a message to all online party members. */
    public void broadcast(String message) {
        for (UUID uid : members) {
            Player p = org.bukkit.Bukkit.getPlayer(uid);
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
            }
        }
    }

    /** Get all online party members. */
    public List<Player> onlineMembers() {
        List<Player> online = new ArrayList<>();
        for (UUID uid : members) {
            Player p = org.bukkit.Bukkit.getPlayer(uid);
            if (p != null && p.isOnline()) {
                online.add(p);
            }
        }
        return online;
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }
}