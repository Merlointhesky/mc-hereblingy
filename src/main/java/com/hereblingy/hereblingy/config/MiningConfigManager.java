package com.hereblingy.hereblingy.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MiningConfigManager {

    private final Plugin plugin;
    private final File playerConfigDir;
    private final Map<UUID, PlayerMiningConfig> playerConfigs = new HashMap<>();

    public MiningConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.playerConfigDir = new File(plugin.getDataFolder(), "player-configs");
        if (!playerConfigDir.exists()) {
            playerConfigDir.mkdirs();
        }
    }

    public PlayerMiningConfig getPlayerConfig(UUID playerId) {
        return playerConfigs.computeIfAbsent(playerId, id -> {
            PlayerMiningConfig config = new PlayerMiningConfig(id.toString());
            loadConfig(id, config);
            return config;
        });
    }

    public boolean isMineEnabled(UUID playerId, Material material) {
        PlayerMiningConfig config = getPlayerConfig(playerId);
        return config.getSettings(material).isMineEnabled();
    }

    public MiningSettings.Destination getDestination(UUID playerId, Material material) {
        PlayerMiningConfig config = getPlayerConfig(playerId);
        return config.getSettings(material).getDestination();
    }

    public void saveConfig(UUID playerId) {
        PlayerMiningConfig config = playerConfigs.get(playerId);
        if (config == null) return;

        File file = new File(playerConfigDir, playerId + ".yml");
        FileConfiguration yaml = new YamlConfiguration();

        yaml.set("miningMode", config.getMiningMode().name());

        for (Map.Entry<Material, MiningSettings> entry : config.getSettingsMap().entrySet()) {
            String path = "settings." + entry.getKey().name();
            yaml.set(path + ".mineEnabled", entry.getValue().isMineEnabled());
            yaml.set(path + ".destination", entry.getValue().getDestination().name());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save mining configuration for " + playerId + ": " + e.getMessage());
        }
    }

    private void loadConfig(UUID playerId, PlayerMiningConfig config) {
        File file = new File(playerConfigDir, playerId + ".yml");
        if (!file.exists()) return;

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String modeStr = yaml.getString("miningMode", PlayerMiningConfig.MiningMode.STRIP_MINING.name());
        try {
            config.setMiningMode(PlayerMiningConfig.MiningMode.valueOf(modeStr));
        } catch (Exception e) {
            config.setMiningMode(PlayerMiningConfig.MiningMode.STRIP_MINING);
        }

        if (yaml.getConfigurationSection("settings") == null) return;

        for (String key : yaml.getConfigurationSection("settings").getKeys(false)) {
            try {
                Material material = Material.valueOf(key);
                String path = "settings." + key;
                boolean mineEnabled = yaml.getBoolean(path + ".mineEnabled", true);
                MiningSettings.Destination destination = MiningSettings.Destination.valueOf(
                        yaml.getString(path + ".destination", MiningSettings.Destination.INVENTORY.name())
                );
                
                MiningSettings settings = config.getSettings(material);
                settings.setMineEnabled(mineEnabled);
                settings.setDestination(destination);
            } catch (Exception e) {
                // ignore invalid materials or enum values
            }
        }
    }
}
