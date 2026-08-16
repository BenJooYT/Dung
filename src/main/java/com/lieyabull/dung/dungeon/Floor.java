package com.lieyabull.dung.dungeon;

import com.lieyabull.dung.game.PlayerState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;

/** One floor's room grid: nodes at (x,z) with type, connectivity, and clear state. */
public final class Floor {
    public final int floorIndex;
    public final int width;
    public final int height;
    private final Map<Long, RoomNode> nodes = new HashMap<>();
    public final RoomNode start;
    public RoomNode boss;

    public Floor(int floorIndex, int width, int height) {
        this.floorIndex = floorIndex;
        this.width = width;
        this.height = height;
        this.start = new RoomNode(width / 2, height / 2, RoomType.START);
        add(start);
        this.boss = start;
    }

    public long key(int x, int z) {
        return (long) x * 4096 + z;
    }

    public void add(RoomNode n) {
        nodes.put(key(n.x, n.z), n);
    }

    public RoomNode at(int x, int z) {
        return nodes.get(key(x, z));
    }

    public boolean inBounds(int x, int z) {
        return x >= 0 && x < width && z >= 0 && z < height;
    }

    public Iterable<RoomNode> rooms() {
        return nodes.values();
    }

    public int roomCount() {
        return nodes.size();
    }

    public Set<RoomNode> visited = new HashSet<>();

    public static final class RoomNode {
        public final int x, z;
        public RoomType type;
        /** interior dimensions (width along x, depth along z); rooms may be square or elongated */
        public int sizeW = RoomGen.SQUARE;
        public int sizeH = RoomGen.SQUARE;
        /** room shape: 0=rectangle, 1=L-shaped, 2=pillar room, 3=split room */
        public int shape;
        public boolean cleared;
        public boolean shopBought;
        public boolean visited;
        public boolean looted; // award-only room (treasure/secret) has given its loot once
        // connectors: N,E,S,W
        public boolean[] doors = new boolean[4];
        // bomb-through-wall secret: which combat room this secret is behind, and the direction
        // from that combat room toward this secret room
        public Floor.RoomNode secretParent;
        public int secretWallDir = -1;
        // world location of the destructible wall center (set during RoomGen.build)
        public Location destructibleWallLoc;
        // assigned room-template id + runtime template (template floor mode). null = procedural build.
        public String templateId;
        public com.lieyabull.dung.room.RoomTemplate template;

        public RoomNode(int x, int z, RoomType type) {
            this.x = x;
            this.z = z;
            this.type = type;
        }

        /** Randomize the room shape: mostly square, sometimes longer (wider or deeper). */
        public void randomizeShape(java.util.Random rng) {
            int r = rng.nextInt(4);
            if (r == 1) { sizeW = RoomGen.LONG; sizeH = RoomGen.SQUARE; }   // longer along x
            else if (r == 2) { sizeW = RoomGen.SQUARE; sizeH = RoomGen.LONG; } // longer along z
            else { sizeW = RoomGen.SQUARE; sizeH = RoomGen.SQUARE; }         // square (r==0 or 3)
            // randomize shape type: 60% rectangle, 15% L, 15% pillar, 10% split
            int s = rng.nextInt(100);
            if (s < 60)      shape = 0; // rectangle
            else if (s < 75) shape = 1; // L-shaped
            else if (s < 90) shape = 2; // pillar room
            else             shape = 3; // split room
        }

        public void open(int dir) {
            doors[dir] = true;
        }
    }
}