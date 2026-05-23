package com.hereblingy.hereblingy.auraskills;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
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
}
