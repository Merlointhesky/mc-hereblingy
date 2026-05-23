package com.hereblingy.hereblingy.map;

import org.bukkit.Location;
import java.util.Map;

public class ScanResult {

    private final Location pointA;
    private final Location pointB;
    private final Map<String, BlockClassification> classifications;
    private final int mineableCount;
    private final int passableCount;
    private final int fluidCount;
    private final int obstructedCount;

    public ScanResult(Location pointA, Location pointB, Map<String, BlockClassification> classifications) {
        this.pointA = pointA;
        this.pointB = pointB;
        this.classifications = classifications;

        int mineable = 0, passable = 0, fluid = 0, obstructed = 0;
        for (BlockClassification bc : classifications.values()) {
            switch (bc) {
                case MINEABLE -> mineable++;
                case PASSABLE -> passable++;
                case FLUID -> fluid++;
                case OBSTRUCTED -> obstructed++;
            }
        }
        this.mineableCount = mineable;
        this.passableCount = passable;
        this.fluidCount = fluid;
        this.obstructedCount = obstructed;
    }

    public Location getPointA() {
        return pointA;
    }

    public Location getPointB() {
        return pointB;
    }

    public BlockClassification getClassification(int x, int y, int z) {
        return classifications.getOrDefault(key(x, y, z), BlockClassification.OBSTRUCTED);
    }

    public boolean isMineable(int x, int y, int z) {
        return getClassification(x, y, z) == BlockClassification.MINEABLE;
    }

    public boolean isPassable(int x, int y, int z) {
        return getClassification(x, y, z) == BlockClassification.PASSABLE;
    }

    public boolean isFluid(int x, int y, int z) {
        return getClassification(x, y, z) == BlockClassification.FLUID;
    }

    public boolean isObstructed(int x, int y, int z) {
        return getClassification(x, y, z) == BlockClassification.OBSTRUCTED;
    }

    public int getMineableCount() {
        return mineableCount;
    }

    public int getPassableCount() {
        return passableCount;
    }

    public int getFluidCount() {
        return fluidCount;
    }

    public int getObstructedCount() {
        return obstructedCount;
    }

    public int getTotalBlocks() {
        return mineableCount + passableCount + fluidCount + obstructedCount;
    }

    public static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
