package com.hereblingy.hereblingy.command;

import com.hereblingy.hereblingy.setup.SetupConfiguration;
import com.hereblingy.hereblingy.setup.SetupManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SetupWizardCommand implements Listener {

    private final SetupManager setupManager;
    private final Plugin plugin;
    private final Map<UUID, Long> lastActiveTime = new HashMap<>();

    public SetupWizardCommand(SetupManager setupManager, Plugin plugin) {
        this.setupManager = setupManager;
        this.plugin = plugin;
    }

    public void onCommand(Player player) {
        UUID uuid = player.getUniqueId();
        if (setupManager.isInSetup(uuid)) {
            player.sendMessage(Component.text("You are already in setup wizard!").color(NamedTextColor.YELLOW));
            return;
        }

        setupManager.startSetup(uuid);
        lastActiveTime.put(uuid, System.currentTimeMillis());

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("     HereBlingy Setup Wizard     ").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("★ Let's configure your storage chests and durability parameters!").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  Step 1: Hold a Pickaxe and Shift-Right-Click the container where you want to deposit DEBRIS (Stone/Dirt/Sand/Gravel).")
                .color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("  Type '/hb stop' or let it timeout to cancel setup.").color(NamedTextColor.GRAY));
    }

    public void checkTimeouts() {
        long now = System.currentTimeMillis();
        lastActiveTime.entrySet().removeIf(entry -> {
            UUID id = entry.getKey();
            long lastActive = entry.getValue();
            if (now - lastActive > 300000) { // 5 minutes
                setupManager.cancelSetup(id);
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) {
                    player.sendMessage(Component.text("HereBlingy setup wizard timed out (5 minutes of inactivity).").color(NamedTextColor.RED));
                }
                return true;
            }
            return false;
        });
    }

    public void updateActivity(UUID uuid) {
        lastActiveTime.put(uuid, System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!setupManager.isInSetup(uuid)) {
            return;
        }

        int step = setupManager.getCurrentStep(uuid);
        if (step != 2 && step != 3) {
            return; // Chat input only used in steps 2 and 3
        }

        event.setCancelled(true);
        updateActivity(uuid);

        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim().toLowerCase();

        if (step == 2) {
            // Step 2 is selecting Tool Supply Chest. They can type "skip"
            if (message.equals("skip")) {
                player.sendMessage(Component.text("Supply chest skipped.").color(NamedTextColor.YELLOW));
                setupManager.setToolSupplyChest(uuid, null); // Skip setting
                setupManager.saveConfiguration(uuid);

                // Advance manually to step 3
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("  Step 4: Type a durability threshold (default: 10) below which the plugin swaps pickaxes/shovels, or type 'skip' to keep default.")
                            .color(NamedTextColor.GREEN));
                });
            } else {
                player.sendMessage(Component.text("Invalid input! Please Shift-Right-Click a chest with a pickaxe, or type 'skip' to bypass.").color(NamedTextColor.RED));
            }
            return;
        }

        if (step == 3) {
            // Step 3 is typing durability threshold
            int threshold = 10;
            if (message.equals("skip")) {
                player.sendMessage(Component.text("Durability threshold set to default: 10").color(NamedTextColor.YELLOW));
            } else {
                try {
                    threshold = Integer.parseInt(message);
                    if (threshold < 2 || threshold > 100) {
                        player.sendMessage(Component.text("Please enter a safe threshold between 2 and 100!").color(NamedTextColor.RED));
                        return;
                    }
                    player.sendMessage(Component.text("Durability threshold set to: " + threshold).color(NamedTextColor.YELLOW));
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid format! Please enter a valid number or type 'skip'.").color(NamedTextColor.RED));
                    return;
                }
            }

            final int finalThreshold = threshold;
            Bukkit.getScheduler().runTask(plugin, () -> {
                SetupConfiguration config = setupManager.getSetupConfig(uuid);
                config.setDurabilityThreshold(finalThreshold);
                setupManager.completeSetup(uuid);
                lastActiveTime.remove(uuid);

                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("   Setup Configuration Complete!   ").color(NamedTextColor.GOLD));
                player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("✔ Trash Chest (Debris): " + formatLoc(config.getTrashChest())).color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("✔ Keep Chest (Treasures): " + formatLoc(config.getKeepChest())).color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("✔ Supply Chest (Tools): " + (config.getToolSupplyChest() != null ? formatLoc(config.getToolSupplyChest()) : "Skipped")).color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("✔ Durability Swapper: swap at " + config.getDurabilityThreshold() + " durability").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("  You can now start auto-mining with '/hb start'!").color(NamedTextColor.GREEN));
            });
        }
    }

    private String formatLoc(org.bukkit.Location loc) {
        if (loc == null) return "None";
        return "[" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]";
    }
}
