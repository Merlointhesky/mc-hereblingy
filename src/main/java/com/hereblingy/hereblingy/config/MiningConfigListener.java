package com.hereblingy.hereblingy.config;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MiningConfigListener implements Listener {

    private final MiningConfigUI configUI;
    private final MiningConfigManager configManager;

    public MiningConfigListener(MiningConfigUI configUI, MiningConfigManager configManager) {
        this.configUI = configUI;
        this.configManager = configManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith("§0HereBlingy -")) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        int slot = event.getRawSlot();
        UUID uuid = player.getUniqueId();

        // 1. Main Config Menu Click
        if (title.equals("§0HereBlingy - Mining Config")) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            if (slot == 11) {
                configUI.openTreasuresMenu(player);
            } else if (slot == 13) {
                configUI.openStandardMenu(player);
            } else if (slot == 15) {
                configUI.openDebrisMenu(player);
            } else if (slot == 22) {
                PlayerMiningConfig playerConfig = configManager.getPlayerConfig(uuid);
                playerConfig.setMiningMode(playerConfig.getMiningMode().toggle());
                configManager.saveConfig(uuid);
                player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.2f);
                configUI.openMainMenu(player); // refresh main menu
            }
            return;
        }

        // 2. Submenu Category Clicks
        if (slot == 31) { // Back button
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            configUI.openMainMenu(player);
            return;
        }

        if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        Material clickedMat = clicked.getType();
        PlayerMiningConfig config = configManager.getPlayerConfig(uuid);
        MiningSettings settings = config.getSettings(clickedMat);

        if (event.getClick() == ClickType.LEFT) {
            // Toggle breaking
            settings.setMineEnabled(!settings.isMineEnabled());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, settings.isMineEnabled() ? 1.4f : 0.8f);
        } else if (event.getClick() == ClickType.RIGHT) {
            // Cycle destination
            settings.setDestination(settings.getDestination().next());
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 0.6f, 1.2f);
        } else {
            return;
        }

        // Save and refresh current menu
        configManager.saveConfig(uuid);

        if (title.equals("§0HereBlingy - Treasures")) {
            configUI.openTreasuresMenu(player);
        } else if (title.equals("§0HereBlingy - Standard Blocks")) {
            configUI.openStandardMenu(player);
        } else if (title.equals("§0HereBlingy - Debris & Soils")) {
            configUI.openDebrisMenu(player);
        }
    }
}
