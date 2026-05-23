package com.hereblingy.hereblingy.map;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScanManager {

    private final Plugin plugin;
    private final Map<UUID, ScanResult> scanResults = new HashMap<>();

    public ScanManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void scanAreaAsync(UUID playerId, Location pointA, Location pointB, ScanCallback callback) {
        new BukkitRunnable() {
            @Override
            public void run() {
                ScanResult result = AreaScanner.scan(pointA, pointB);
                scanResults.put(playerId, result);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        callback.onComplete(result);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    public ScanResult getScanResult(UUID playerId) {
        return scanResults.get(playerId);
    }

    public void clearScan(UUID playerId) {
        scanResults.remove(playerId);
    }

    public boolean hasScan(UUID playerId) {
        return scanResults.containsKey(playerId);
    }

    public interface ScanCallback {
        void onComplete(ScanResult result);
    }
}
