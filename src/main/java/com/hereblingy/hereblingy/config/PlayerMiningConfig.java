package com.hereblingy.hereblingy.config;

import org.bukkit.Material;
import java.util.HashMap;
import java.util.Map;

public class PlayerMiningConfig {

    public enum MiningMode {
        STRIP_MINING,
        TERRAFORMING;

        public MiningMode toggle() {
            return this == STRIP_MINING ? TERRAFORMING : STRIP_MINING;
        }
    }

    private final String playerId;
    private final Map<Material, MiningSettings> settingsMap = new HashMap<>();
    private MiningMode miningMode = MiningMode.STRIP_MINING;

    public PlayerMiningConfig(String playerId) {
        this.playerId = playerId;
        initializeDefaults();
    }

    public MiningMode getMiningMode() {
        return miningMode;
    }

    public void setMiningMode(MiningMode miningMode) {
        this.miningMode = miningMode;
    }

    public String getPlayerId() {
        return playerId;
    }

    public Map<Material, MiningSettings> getSettingsMap() {
        return settingsMap;
    }

    public MiningSettings getSettings(Material material) {
        return settingsMap.computeIfAbsent(material, m -> {
            // Default fallback if material was not loaded
            if (isDefaultTreasure(m)) {
                return new MiningSettings(true, MiningSettings.Destination.KEEP_CHEST);
            } else {
                return new MiningSettings(true, MiningSettings.Destination.TRASH_CHEST);
            }
        });
    }

    private void initializeDefaults() {
        // Treasures / Keep Chest
        Material[] treasures = {
                Material.DIAMOND, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
                Material.EMERALD, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
                Material.RAW_IRON, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT,
                Material.RAW_GOLD, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT,
                Material.RAW_COPPER, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT,
                Material.REDSTONE, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
                Material.LAPIS_LAZULI, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
                Material.COAL, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
                Material.NETHER_QUARTZ_ORE, Material.QUARTZ,
                Material.NETHER_GOLD_ORE,
                Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP,
                Material.FLINT
        };

        for (Material mat : treasures) {
            settingsMap.put(mat, new MiningSettings(true, MiningSettings.Destination.KEEP_CHEST));
        }

        // Debris / Trash Chest
        Material[] debris = {
                Material.STONE, Material.COBBLESTONE,
                Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
                Material.NETHERRACK,
                Material.SANDSTONE,
                Material.OBSIDIAN,
                Material.TUFF,
                Material.CALCITE,
                Material.DIORITE,
                Material.ANDESITE,
                Material.GRANITE,
                Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.ROOTED_DIRT,
                Material.GRAVEL,
                Material.SAND, Material.RED_SAND,
                Material.MUD,
                Material.CLAY, Material.CLAY_BALL
        };

        for (Material mat : debris) {
            settingsMap.put(mat, new MiningSettings(true, MiningSettings.Destination.TRASH_CHEST));
        }
    }

    private boolean isDefaultTreasure(Material material) {
        String name = material.name();
        return name.contains("DIAMOND") || name.contains("EMERALD") || name.contains("IRON") 
                || name.contains("GOLD") || name.contains("COPPER") || name.contains("REDSTONE") 
                || name.contains("LAPIS") || name.contains("COAL") || name.contains("QUARTZ") 
                || name.contains("ANCIENT_DEBRIS") || name.contains("NETHERITE") || material == Material.FLINT;
    }
}
