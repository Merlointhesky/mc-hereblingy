package com.hereblingy.hereblingy.selection;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SelectionManager {

    private final Plugin plugin;
    private final File selectionDir;
    private final Map<UUID, Location[]> selections = new HashMap<>();
    private final Set<UUID> selectionModePlayers = new HashSet<>();

    public SelectionManager(Plugin plugin) {
        this.plugin = plugin;
        this.selectionDir = new File(plugin.getDataFolder(), "selections");
        if (!selectionDir.exists()) {
            selectionDir.mkdirs();
        }
    }

    public boolean isSelectionMode(UUID playerId) {
        return selectionModePlayers.contains(playerId);
    }

    public void setSelectionMode(UUID playerId, boolean active) {
        if (active) {
            selectionModePlayers.add(playerId);
        } else {
            selectionModePlayers.remove(playerId);
        }
    }

    public void setPointA(UUID playerId, Location location) {
        Location[] locs = selections.computeIfAbsent(playerId, k -> new Location[2]);
        locs[0] = location != null ? location.clone() : null;
        saveSelection(playerId);
    }

    public void setPointB(UUID playerId, Location location) {
        Location[] locs = selections.computeIfAbsent(playerId, k -> new Location[2]);
        locs[1] = location != null ? location.clone() : null;
        saveSelection(playerId);
    }

    public Location getPointA(UUID playerId) {
        if (!selections.containsKey(playerId)) {
            loadSelection(playerId);
        }
        Location[] locs = selections.get(playerId);
        return locs != null ? locs[0] : null;
    }

    public Location getPointB(UUID playerId) {
        if (!selections.containsKey(playerId)) {
            loadSelection(playerId);
        }
        Location[] locs = selections.get(playerId);
        return locs != null ? locs[1] : null;
    }

    public boolean hasCompleteSelection(UUID playerId) {
        if (!selections.containsKey(playerId)) {
            loadSelection(playerId);
        }
        Location[] locs = selections.get(playerId);
        return locs != null && locs[0] != null && locs[1] != null;
    }

    public void clearSelection(UUID playerId) {
        selections.remove(playerId);
        File file = new File(selectionDir, playerId + ".yml");
        if (file.exists()) {
            file.delete();
        }
    }

    public void clearAll() {
        selections.clear();
    }

    private void saveSelection(UUID playerId) {
        Location[] locs = selections.get(playerId);
        if (locs == null) return;

        File file = new File(selectionDir, playerId + ".yml");
        FileConfiguration yaml = new YamlConfiguration();

        if (locs[0] != null) yaml.set("pointA", locs[0]);
        if (locs[1] != null) yaml.set("pointB", locs[1]);

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save selection for " + playerId + ": " + e.getMessage());
        }
    }

    private void loadSelection(UUID playerId) {
        File file = new File(selectionDir, playerId + ".yml");
        if (!file.exists()) return;

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Location[] locs = new Location[2];
        locs[0] = yaml.getLocation("pointA");
        locs[1] = yaml.getLocation("pointB");

        selections.put(playerId, locs);
    }
}
