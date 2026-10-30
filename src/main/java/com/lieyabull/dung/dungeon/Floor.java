package com.lieyabull.dung.dungeon;

import com.lieyabull.dung.game.PlayerState;

import java.util.ArrayDeque;
import java.util.EnumSet;
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
    /** Optional theme used for graph tuning and normal-room palettes. */
    public Archetype archetype;
    public final Set<FloorModifier> modifiers = EnumSet.noneOf(FloorModifier.class);
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
        /** Graph purpose: normal room, hub chamber, required destination, or optional side room. */
        public RoomRole role = RoomRole.NORMAL;
        /** interior dimensions (width along x, depth along z); rooms may be square or elongated */
        public int sizeW = RoomGen.SQUARE;
        public int sizeH = RoomGen.SQUARE;
        /** Interior footprint shape. */
        public RoomShape shape = RoomShape.RECTANGLE;
        /** Interior features placed after the shell is built. */
        public final Set<RoomFeature> features = EnumSet.noneOf(RoomFeature.class);
        /** Stable seed for deterministic feature placement. */
        public long featureSeed;
        /** Optional layout template applied to this room. */
        public RoomTemplate template;
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
        // assigned structure-library id + runtime (rotated) structure. null = procedural build.
        public String structureId;
        public com.lieyabull.dung.structure.StructureDefinition structure;
        // WorldEdit clipboard to paste for this room (from the structure library).
        public com.sk89q.worldedit.extent.clipboard.Clipboard clipboard;
        // Clockwise rotation steps (0..3) applied to both the clipboard and the metadata.
        public int rotationSteps;

        public RoomNode(int x, int z, RoomType type) {
            this.x = x;
            this.z = z;
            this.type = type;
        }

        /** Randomize the room shape: mostly square, sometimes longer (wider or deeper). */
        public void randomizeShape(java.util.Random rng) {
            randomizeShape(rng, null);
        }

        /** Prefer an archetype signature shape for roughly half of the rooms. */
        public void randomizeShape(java.util.Random rng, RoomShape preferred) {
            int r = rng.nextInt(4);
            if (r == 1) { sizeW = RoomGen.LONG; sizeH = RoomGen.SQUARE; }   // longer along x
            else if (r == 2) { sizeW = RoomGen.SQUARE; sizeH = RoomGen.LONG; } // longer along z
            else { sizeW = RoomGen.SQUARE; sizeH = RoomGen.SQUARE; }         // square (r==0 or 3)
            // shape type: 60% rectangle, 15% L, 15% pillar, 10% split; preferred replaces
            // the first 55% of the roll.
            int s = rng.nextInt(100);
            if (preferred != null && s < 55) {
                shape = preferred;
            } else if (s < 60) {
                shape = RoomShape.RECTANGLE;
            } else if (s < 75) {
                shape = RoomShape.L_SHAPED;
            } else if (s < 90) {
                shape = RoomShape.PILLAR;
            } else {
                shape = RoomShape.SPLIT;
            }
        }

        /** Pick zero or one safe feature, preferring an archetype/shape signature feature. */
        public void randomizeFeatures(java.util.Random rng, Archetype archetype) {
            features.clear();
            featureSeed = rng.nextLong();
            RoomFeature preferred = signatureFeature(archetype);
            if (preferred != null && rng.nextInt(100) < 55) {
                features.add(preferred);
            } else if (rng.nextInt(100) < 35) {
                RoomFeature extra = randomSafeFeature(rng);
                if (extra != null) features.add(extra);
            }
        }

        private RoomFeature signatureFeature(Archetype archetype) {
            if (archetype != null) {
                return switch (archetype) {
                    case RUINS -> RoomFeature.RUBBLE;
                    case CAVERNS -> RoomFeature.PLATFORMS;
                    case TEMPLE -> RoomFeature.ALTAR;
                    case OVERGROWN -> RoomFeature.ROOTS;
                    case INFERNO -> RoomFeature.LAVA_POOL;
                    case CRYPT -> shape == RoomShape.RING
                            ? RoomFeature.CENTRAL_PIT : RoomFeature.SPIKES;
                };
            }
            return switch (shape) {
                case CAVE -> RoomFeature.WATER_POOL;
                case RING -> RoomFeature.CENTRAL_PIT;
                case CROSS -> RoomFeature.PILLARS;
                case L_SHAPED -> RoomFeature.RUBBLE;
                case ARENA -> RoomFeature.CHEST_PLATFORM;
                case SPLIT -> RoomFeature.RUBBLE;
                default -> null;
            };
        }

        private RoomFeature randomSafeFeature(java.util.Random rng) {
            RoomFeature[] all = RoomFeature.values();
            int start = rng.nextInt(all.length);
            for (int i = 0; i < all.length; i++) {
                RoomFeature feature = all[(start + i) % all.length];
                if (!feature.safe()) continue;
                if (feature == RoomFeature.PILLARS && shape == RoomShape.PILLAR) continue;
                if (feature == RoomFeature.PLATFORMS && shape == RoomShape.RING) continue;
                return feature;
            }
            return null;
        }

        public void open(int dir) {
            doors[dir] = true;
        }
    }
}