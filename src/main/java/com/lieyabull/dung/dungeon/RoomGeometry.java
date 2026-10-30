package com.lieyabull.dung.dungeon;

/** Interior footprint mask for one room shape. Coordinates are interior-relative (0..width-1). */
public final class RoomGeometry {
    private final RoomShape shape;
    private final int width;
    private final int depth;
    private final int cornerCut;
    private final int centerX;
    private final int centerZ;
    private final int holeHalf;
    private final int crossArm;
    private final double caveCx;
    private final double caveCz;
    private final double caveRx;
    private final double caveRz;

    public RoomGeometry(RoomShape shape, int width, int depth) {
        this.shape = shape;
        this.width = width;
        this.depth = depth;

        int perp = RoomGen.PERP_CENTER - RoomGen.WALL;
        this.centerX = Math.min(width - 1, Math.max(0, perp));
        this.centerZ = Math.min(depth - 1, Math.max(0, perp));
        this.cornerCut = Math.max(3, Math.min(5, Math.min(width, depth) / 3));
        this.holeHalf = Math.max(1, Math.min(2, Math.min(width, depth) / 10));
        this.crossArm = Math.max(5, Math.min(7, Math.min(width, depth) / 3));
        this.caveCx = (width - 1) / 2.0;
        this.caveCz = (depth - 1) / 2.0;
        this.caveRx = Math.max(3.0, width / 2.0 - 1.5);
        this.caveRz = Math.max(3.0, depth / 2.0 - 1.5);
    }

    public boolean inside(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= depth) return false;
        return switch (shape) {
            case L_SHAPED -> !(x >= width - cornerCut && z >= depth - cornerCut);
            case CROSS -> Math.abs(x - centerX) <= crossArm / 2
                    || Math.abs(z - centerZ) <= crossArm / 2;
            case RING -> !(Math.abs(x - centerX) <= holeHalf
                    && Math.abs(z - centerZ) <= holeHalf);
            case CAVE -> {
                double nx = (x - caveCx) / caveRx;
                double nz = (z - caveCz) / caveRz;
                yield nx * nx + nz * nz <= 1.0;
            }
            default -> true;
        };
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int holeHalf() {
        return holeHalf;
    }

    public RoomShape shape() {
        return shape;
    }
}
