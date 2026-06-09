package com.hereblingy.hereblingy.hereroleplay;

import com.here.hereroleplay.api.HereRolePlayAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class HereRolePlayHelper {

    private boolean available = false;

    public void init() {
        available = Bukkit.getPluginManager().getPlugin("HereRolePlay") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    public void addCollectXp(Player player, double amount) {
        if (!available) return;
        try {
            HereRolePlayAPI.giveCollectXp(player, amount);
        } catch (Exception e) {
            // Failsafe
        }
    }
}
