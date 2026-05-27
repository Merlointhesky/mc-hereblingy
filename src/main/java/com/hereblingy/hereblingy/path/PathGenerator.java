package com.hereblingy.hereblingy.path;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class PathGenerator {

    public enum FacingDirection {
        NORTH, SOUTH, EAST, WEST
    }

    public static FacingDirection getFacingDirection(float yaw) {
        yaw = (yaw % 360 + 360) % 360;
        if (yaw >= 45 && yaw < 135) {
            return FacingDirection.WEST;
        } else if (yaw >= 135 && yaw < 225) {
            return FacingDirection.NORTH;
        } else if (yaw >= 225 && yaw < 315) {
            return FacingDirection.EAST;
        } else {
            return FacingDirection.SOUTH;
        }
    }

    /**
     * Generates a single dynamic branch mining segment at a specific step offset.
     * This supports infinite strip mining by appending path coordinates on the fly.
     */
    public static List<Location> generateDynamicSegment(Location startLoc, FacingDirection facing, int startStep, int stepsToGenerate, int width) {
        List<Location> path = new ArrayList<>();
        World world = startLoc.getWorld();
        int startX = startLoc.getBlockX();
        int startY = startLoc.getBlockY();
        int startZ = startLoc.getBlockZ();
        int y = startY; // Mine horizontally at player's start Y feet level

        if (facing == FacingDirection.NORTH || facing == FacingDirection.SOUTH) {
            int stepZ = (facing == FacingDirection.NORTH) ? -1 : 1;
            for (int i = startStep; i < startStep + stepsToGenerate; i++) {
                int z = startZ + i * stepZ;
                // Main tunnel seam coordinate
                path.add(new Location(world, startX + 1.0, y, z + 0.5));

                // Every 4th step along main tunnel (spacing of 3 solid blocks)
                if (i % 4 == 0) {
                    // Left branch: towards minX (startX - width)
                    for (int x = startX - 1; x >= startX - width; x--) {
                        path.add(new Location(world, x + 0.5, y, z + 0.5));
                    }
                    // Walk back
                    for (int x = startX - width; x <= startX - 1; x++) {
                        path.add(new Location(world, x + 0.5, y, z + 0.5));
                    }

                    // Right branch: towards maxX (startX + 1 + width)
                    for (int x = startX + 2; x <= startX + 1 + width; x++) {
                        path.add(new Location(world, x + 0.5, y, z + 0.5));
                    }
                    // Walk back
                    for (int x = startX + 1 + width; x >= startX + 2; x--) {
                        path.add(new Location(world, x + 0.5, y, z + 0.5));
                    }
                }
            }
        } else {
            int stepX = (facing == FacingDirection.WEST) ? -1 : 1;
            for (int i = startStep; i < startStep + stepsToGenerate; i++) {
                int x = startX + i * stepX;
                // Main tunnel seam coordinate
                path.add(new Location(world, x + 0.5, y, startZ + 1.0));

                // Every 4th step along main tunnel
                if (i % 4 == 0) {
                    // Left branch: towards minZ (startZ - width)
                    for (int z = startZ - 1; z >= startZ - width; z--) {
                        path.add(new Location(world, x + 0.5, y, z + 0.5));
                    }
                    // Walk back
                    for (int z = startZ - width; z <= startZ - 1; z++) {
                        path.add(new Location(world, x + 0.5, y, z + 0.5));
                    }

                    // Right branch: towards maxZ (startZ + 1 + width)
                    for (int z = startZ + 2; z <= startZ + 1 + width; z++) {
                        path.add(new Location(world, x + 0.5, y, z + 0.5));
                    }
                    // Walk back
                    for (int z = startZ + 1 + width; z >= startZ + 2; z--) {
                        path.add(new Location(world, x + 0.5, y, z + 0.5));
                    }
                }
            }
        }

        return path;
    }

    /**
     * Generates a 3D path to excavate the entire selected area layer-by-layer from the top down.
     * Carves out 2-block high horizontal slices safely.
     */
    public static List<Location> generateTerraformingPath(Location pointA, Location pointB) {
        List<Location> path = new ArrayList<>();
        if (pointA.getWorld() != pointB.getWorld()) {
            return path;
        }

        World world = pointA.getWorld();
        int minX = Math.min(pointA.getBlockX(), pointB.getBlockX());
        int maxX = Math.max(pointA.getBlockX(), pointB.getBlockX());
        int minY = Math.min(pointA.getBlockY(), pointB.getBlockY());
        int maxY = Math.max(pointA.getBlockY(), pointB.getBlockY());
        int minZ = Math.min(pointA.getBlockZ(), pointB.getBlockZ());
        int maxZ = Math.max(pointA.getBlockZ(), pointB.getBlockZ());

        boolean reverseLayer = false;
        int lastY = -1;
        
        // Step down vertically by 2 blocks (height of a player), making Y progression safe
        for (int y = maxY - 1; y >= minY; y -= 2) {
            lastY = y;
            addLayer(path, world, minX, maxX, minZ, maxZ, y, reverseLayer);
            reverseLayer = !reverseLayer;
        }

        // If the bottom Y layer (minY) was not reached/cleared, append a final layer at minY
        if (lastY == -1 || lastY > minY) {
            addLayer(path, world, minX, maxX, minZ, maxZ, minY, reverseLayer);
        }

        return path;
    }

    private static void addLayer(List<Location> path, World world, int minX, int maxX, int minZ, int maxZ, int y, boolean reverseLayer) {
        List<Location> layerPath = new ArrayList<>();
        boolean goingForwardZ = true;

        for (int z = minZ; z <= maxZ; z++) {
            if (goingForwardZ) {
                for (int x = minX; x <= maxX; x++) {
                    layerPath.add(new Location(world, x + 0.5, y, z + 0.5));
                }
            } else {
                for (int x = maxX; x >= minX; x--) {
                    layerPath.add(new Location(world, x + 0.5, y, z + 0.5));
                }
            }
            goingForwardZ = !goingForwardZ;
        }

        if (reverseLayer) {
            java.util.Collections.reverse(layerPath);
        }
        path.addAll(layerPath);
    }

    public static int findClosestIndex(List<Location> path, Location target) {
        if (path.isEmpty() || target == null) {
            return 0;
        }
        int bestIndex = 0;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            Location loc = path.get(i);
            if (loc.getWorld() != target.getWorld()) {
                continue;
            }
            double dx = loc.getX() - target.getX();
            double dy = loc.getY() - target.getY();
            double dz = loc.getZ() - target.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestIndex = i;
            }
        }
        return bestIndex;
    }
}
