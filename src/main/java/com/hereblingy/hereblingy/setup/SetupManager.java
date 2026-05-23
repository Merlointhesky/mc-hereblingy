package com.hereblingy.hereblingy.setup;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SetupManager {
    private final Plugin plugin;
    private final File setupDir;
    private final Map<UUID, SetupConfiguration> configurations = new HashMap<>();
    private final Map<UUID, Integer> setupSteps = new HashMap<>();

    public SetupManager(Plugin plugin) {
        this.plugin = plugin;
        this.setupDir = new File(plugin.getDataFolder(), "setup-configs");
        if (!setupDir.exists()) {
            setupDir.mkdirs();
        }
    }

    public void startSetup(UUID playerId) {
        configurations.put(playerId, new SetupConfiguration(playerId.toString()));
        setupSteps.put(playerId, 0);
    }

    public int getCurrentStep(UUID playerId) {
        return setupSteps.getOrDefault(playerId, -1);
    }

    public boolean isInSetup(UUID playerId) {
        return setupSteps.containsKey(playerId) && setupSteps.get(playerId) >= 0;
    }

    public void setTrashChest(UUID playerId, Location location) {
        if (!isInSetup(playerId)) return;
        SetupConfiguration config = configurations.get(playerId);
        config.setTrashChest(location);
        setupSteps.put(playerId, 1);
    }

    public void setKeepChest(UUID playerId, Location location) {
        if (!isInSetup(playerId)) return;
        SetupConfiguration config = configurations.get(playerId);
        config.setKeepChest(location);
        setupSteps.put(playerId, 2);
    }

    public void setToolSupplyChest(UUID playerId, Location location) {
        if (!isInSetup(playerId)) return;
        SetupConfiguration config = configurations.get(playerId);
        config.setToolSupplyChest(location);
        setupSteps.put(playerId, 3);
    }

    public void setDurabilityThreshold(UUID playerId, int threshold) {
        if (!isInSetup(playerId)) return;
        SetupConfiguration config = configurations.get(playerId);
        config.setDurabilityThreshold(threshold);
        completeSetup(playerId);
    }

    public void completeSetup(UUID playerId) {
        SetupConfiguration config = configurations.get(playerId);
        if (config != null && config.isComplete()) {
            setupSteps.remove(playerId);
            saveConfiguration(playerId);
        }
    }

    public void cancelSetup(UUID playerId) {
        configurations.remove(playerId);
        setupSteps.remove(playerId);
    }

    public SetupConfiguration getSetupConfig(UUID playerId) {
        if (!configurations.containsKey(playerId)) {
            loadConfiguration(playerId);
        }
        return configurations.get(playerId);
    }

    public boolean hasSetupConfig(UUID playerId) {
        SetupConfiguration config = getSetupConfig(playerId);
        return config != null && config.isComplete();
    }

    public void clearSetupConfig(UUID playerId) {
        configurations.remove(playerId);
        setupSteps.remove(playerId);
        File file = new File(setupDir, playerId + ".yml");
        if (file.exists()) {
            file.delete();
        }
    }

    public void saveConfiguration(UUID playerId) {
        SetupConfiguration config = configurations.get(playerId);
        if (config == null) return;

        File file = new File(setupDir, playerId + ".yml");
        FileConfiguration yaml = new YamlConfiguration();

        yaml.set("playerId", config.getPlayerId());
        yaml.set("trashChest", config.getTrashChest());
        yaml.set("keepChest", config.getKeepChest());
        yaml.set("toolSupplyChest", config.getToolSupplyChest());
        yaml.set("durabilityThreshold", config.getDurabilityThreshold());
        yaml.set("createdAt", config.getCreatedAt());
        yaml.set("lastModified", config.getLastModified());

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save setup configuration for " + playerId + ": " + e.getMessage());
        }
    }

    public void loadConfiguration(UUID playerId) {
        File file = new File(setupDir, playerId + ".yml");
        if (!file.exists()) return;

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        SetupConfiguration config = new SetupConfiguration(playerId.toString());

        config.setTrashChest(yaml.getLocation("trashChest"));
        config.setKeepChest(yaml.getLocation("keepChest"));
        config.setToolSupplyChest(yaml.getLocation("toolSupplyChest"));
        config.setDurabilityThreshold(yaml.getInt("durabilityThreshold", 10));

        configurations.put(playerId, config);
    }
}
