package com.hereblingy.hereblingy.auraskills;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class AuraSkillsHelper {

    private boolean auraSkillsAvailable = false;

    public void init() {
        auraSkillsAvailable = Bukkit.getPluginManager().getPlugin("AuraSkills") != null;
    }

    public boolean isAvailable() {
        return auraSkillsAvailable;
    }

    public int getMiningLevel(Player player) {
        if (!auraSkillsAvailable) return 0;
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            SkillsUser user = api.getUser(player.getUniqueId());
            if (user != null) {
                return user.getSkillLevel(Skills.MINING);
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }

    public double getMiningFortune(Player player) {
        if (!auraSkillsAvailable) return 0.0;
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            SkillsUser user = api.getUser(player.getUniqueId());
            if (user != null) {
                return user.getSkillLevel(Skills.MINING);
            }
        } catch (Exception e) {
            // ignore
        }
        return 0.0;
    }

    public double getSpeedBonus(Player player) {
        if (!auraSkillsAvailable) return 0.0;
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            SkillsUser user = api.getUser(player.getUniqueId());
            if (user != null) {
                return user.getSkillLevel(Skills.MINING);
            }
        } catch (Exception e) {
            // ignore
        }
        return 0.0;
    }

    public void addMiningXp(Player player, double baseXp) {
        if (!auraSkillsAvailable) return;
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            SkillsUser user = api.getUser(player.getUniqueId());
            if (user != null) {
                int level = getMiningLevel(player);
                double xpAmount = baseXp * (1.0 + level * 0.02);
                user.addSkillXp(Skills.MINING, xpAmount);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public double getBaseXpForBlock(Material material) {
        String name = material.name();
        if (name.contains("DIAMOND_ORE") || name.contains("EMERALD_ORE")) {
            return 30.0;
        } else if (material == Material.ANCIENT_DEBRIS) {
            return 50.0;
        } else if (name.contains("GOLD_ORE")) {
            return 10.0;
        } else if (name.contains("REDSTONE_ORE")) {
            return 10.0;
        } else if (name.contains("LAPIS_ORE")) {
            return 12.0;
        } else if (name.contains("IRON_ORE") || name.contains("COPPER_ORE")) {
            return 7.0;
        } else if (name.contains("COAL_ORE") || name.contains("NETHER_QUARTZ_ORE")) {
            return 5.0;
        } else if (material == Material.OBSIDIAN) {
            return 20.0;
        } else if (material == Material.STONE || material == Material.COBBLESTONE 
                || material == Material.DEEPSLATE || material == Material.COBBLED_DEEPSLATE
                || material == Material.NETHERRACK || material == Material.SANDSTONE 
                || material == Material.RED_SANDSTONE || name.contains("TUFF") || name.contains("DIORITE") 
                || name.contains("ANDESITE") || name.contains("GRANITE") || name.contains("BASALT") 
                || name.contains("BLACKSTONE")) {
            return 0.5;
        }
        return 0.2; // default small XP for other mined blocks
    }
}
