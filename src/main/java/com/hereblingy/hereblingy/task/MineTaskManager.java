package com.hereblingy.hereblingy.task;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MineTaskManager {

    private final Map<UUID, MineTask> activeTasks = new HashMap<>();
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

    public void stopAllTasks() {
        for (MineTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
