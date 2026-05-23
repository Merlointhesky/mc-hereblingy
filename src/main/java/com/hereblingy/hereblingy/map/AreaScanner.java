package com.hereblingy.hereblingy.map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;

import java.util.HashMap;
import java.util.Map;

public class AreaScanner {

    public static ScanResult scan(Location pointA, Location pointB) {
        World world = pointA.getWorld();
        int minX = Math.min(pointA.getBlockX(), pointB.getBlockX());
        int maxX = Math.max(pointA.getBlockX(), pointB.getBlockX());
        int minY = Math.min(pointA.getBlockY(), pointB.getBlockY());
        int maxY = Math.max(pointA.getBlockY(), pointB.getBlockY());
        int minZ = Math.min(pointA.getBlockZ(), pointB.getBlockZ());
        int maxZ = Math.max(pointA.getBlockZ(), pointB.getBlockZ());

        Map<String, BlockClassification> classifications = new HashMap<>();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    Block block = world.getBlockAt(x, y, z);
                    BlockClassification bc = classifyBlock(block);
                    classifications.put(x + "," + y + "," + z, bc);
                }
            }
        }

        return new ScanResult(pointA, pointB, classifications);
    }

    private static BlockClassification classifyBlock(Block block) {
        Material type = block.getType();
        
        if (type == Material.WATER || type == Material.LAVA) {
            return BlockClassification.FLUID;
        }
        
        if (type == Material.BEDROCK || type == Material.SPAWNER 
                || type == Material.END_PORTAL || type == Material.NETHER_PORTAL) {
            return BlockClassification.OBSTRUCTED;
        }

        // Avoid breaking chests and active inventory containers
        try {
            if (block.getState() instanceof Container) {
                return BlockClassification.OBSTRUCTED;
            }
        } catch (Exception e) {
            // ignore
        }

        if (type.isSolid()) {
            return BlockClassification.MINEABLE;
        }

        return BlockClassification.PASSABLE;
    }
}
