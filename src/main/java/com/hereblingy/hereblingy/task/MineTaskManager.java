package com.hereblingy.hereblingy.task;

import com.hereblingy.hereblingy.HereBlingyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MineTaskManager {

    private final Map<UUID, MineTask> activeTasks = new HashMap<>();
    private final Map<UUID, AutoDefenseTask> activeDefenseTasks = new HashMap<>();
    private final Map<UUID, Integer> lastStopIndices = new HashMap<>();
    private final Map<UUID, java.util.List<org.bukkit.Location>> cachedPaths = new HashMap<>();

    public void cachePath(UUID playerId, java.util.List<org.bukkit.Location> path) {
        cachedPaths.put(playerId, path);
    }

    public java.util.List<org.bukkit.Location> getCachedPath(UUID playerId) {
        return cachedPaths.get(playerId);
    }

    public boolean hasCachedPath(UUID playerId) {
        return cachedPaths.containsKey(playerId);
    }

    public void clearCachedPath(UUID playerId) {
        cachedPaths.remove(playerId);
    }

    public void startTask(Player player, MineTask task) {
        stopTask(player);
        stopAutoDefense(player, true);
        activeTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(task.getPlugin(), 0L, 1L);
    }

    public void stopTask(Player player) {
        MineTask task = activeTasks.remove(player.getUniqueId());
        if (task != null) {
            recordDurabilityStop(player, task.getCurrentIndex());
            task.sendActivitySummary();
            task.cancel();
        }
    }

    public void removeActiveTask(UUID playerId) {
        activeTasks.remove(playerId);
    }

    public void recordDurabilityStop(Player player, int index) {
        lastStopIndices.put(player.getUniqueId(), index);
    }

    public boolean hasLastStop(Player player) {
        return lastStopIndices.containsKey(player.getUniqueId());
    }

    public int getLastStopIndex(Player player) {
        return lastStopIndices.getOrDefault(player.getUniqueId(), 0);
    }

    public void clearLastStop(Player player) {
        lastStopIndices.remove(player.getUniqueId());
    }

    public boolean isMining(Player player) {
        return activeTasks.containsKey(player.getUniqueId());
    }

    public MineTask getActiveTask(Player player) {
        return activeTasks.get(player.getUniqueId());
    }

    public void startAutoDefense(Player player) {
        if (activeDefenseTasks.containsKey(player.getUniqueId())) return;
        AutoDefenseTask defenseTask = new AutoDefenseTask(HereBlingyPlugin.getInstance(), player);
        activeDefenseTasks.put(player.getUniqueId(), defenseTask);
        defenseTask.runTaskTimer(HereBlingyPlugin.getInstance(), 0L, 1L);
        player.sendMessage(Component.text("⚔ AFK Auto-defense activated! Stand still; we will protect you. Move manually to deactivate.").color(NamedTextColor.GOLD));
    }

    public void stopAutoDefense(Player player, boolean silent) {
        AutoDefenseTask defenseTask = activeDefenseTasks.remove(player.getUniqueId());
        if (defenseTask != null) {
            defenseTask.cancel();
            if (!silent) {
                player.sendMessage(Component.text("⚔ AFK Auto-defense deactivated.").color(NamedTextColor.YELLOW));
            }
        }
    }

    public boolean hasAutoDefense(Player player) {
        return activeDefenseTasks.containsKey(player.getUniqueId());
    }

    public AutoDefenseTask getAutoDefenseTask(Player player) {
        return activeDefenseTasks.get(player.getUniqueId());
    }

    public void stopAllTasks() {
        for (MineTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
        for (AutoDefenseTask task : activeDefenseTasks.values()) {
            task.cancel();
        }
        activeDefenseTasks.clear();
    }
}
