package com.hereblingy.hereblingy.listener;

import com.hereblingy.hereblingy.config.MiningConfigManager;
import com.hereblingy.hereblingy.map.ScanManager;
import com.hereblingy.hereblingy.map.ScanResult;
import com.hereblingy.hereblingy.selection.SelectionManager;
import com.hereblingy.hereblingy.setup.SetupManager;
import com.hereblingy.hereblingy.task.MineTaskManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MineListener implements Listener {

    private final SelectionManager selectionManager;
    private final MineTaskManager mineTaskManager;
    private final ScanManager scanManager;
    private final SetupManager setupManager;
    private final MiningConfigManager configManager;

    public MineListener(SelectionManager selectionManager, MineTaskManager mineTaskManager,
                        ScanManager scanManager, SetupManager setupManager, MiningConfigManager configManager) {
        this.selectionManager = selectionManager;
        this.mineTaskManager = mineTaskManager;
        this.scanManager = scanManager;
        this.setupManager = setupManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // If in chest setup, let SetupWizardListener handle it
        if (setupManager.isInSetup(uuid)) {
            return;
        }

        // Only allow selection if the player is explicitly in Selection Mode
        if (!selectionManager.isSelectionMode(uuid)) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || !held.getType().name().contains("PICKAXE")) {
            return;
        }

        if (!player.isSneaking()) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        event.setCancelled(true);
        Location loc = block.getLocation();

        Location pointA = selectionManager.getPointA(uuid);
        Location pointB = selectionManager.getPointB(uuid);

        if (pointA == null || (pointA != null && pointB != null)) {
            // First point selection or resetting both
            selectionManager.setPointA(uuid, loc);
            selectionManager.setPointB(uuid, null); // Clear Point B
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f);
            player.sendMessage(Component.text("★ HereBlingy Selection: Point A set at [" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "].")
                    .color(NamedTextColor.GREEN));
        } else {
            // Second point selection
            selectionManager.setPointB(uuid, loc);
            selectionManager.setSelectionMode(uuid, false); // Disable selection mode once selection is complete
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            player.sendMessage(Component.text("★ HereBlingy Selection: Point B set at [" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]. Selection complete, selection mode disabled!")
                    .color(NamedTextColor.GREEN));

            // Trigger scan and print stats
            player.sendMessage(Component.text("Scanning selected region...").color(NamedTextColor.GOLD));
            scanManager.scanAreaAsync(uuid, pointA, loc, result -> {
                player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("       Region Scan Summary       ").color(NamedTextColor.GOLD));
                player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text(" ✔ Total Blocks in Volume: " + result.getTotalBlocks()).color(NamedTextColor.GRAY));
                player.sendMessage(Component.text(" ✔ Mineable Stone/Ores: " + result.getMineableCount()).color(NamedTextColor.GREEN));
                player.sendMessage(Component.text(" ✔ Water & Lava fluid pockets: " + result.getFluidCount()).color(NamedTextColor.BLUE));
                player.sendMessage(Component.text(" ✔ Bedrock & Obstructed: " + result.getObstructedCount()).color(NamedTextColor.RED));
                player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text(" Start auto-mining using '/hb start'!").color(NamedTextColor.YELLOW));
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (mineTaskManager.isMining(player)) {
            mineTaskManager.stopTask(player);
        }
        mineTaskManager.stopAutoDefense(player, true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (mineTaskManager.hasAutoDefense(player)) {
            com.hereblingy.hereblingy.task.AutoDefenseTask defenseTask = mineTaskManager.getAutoDefenseTask(player);
            if (defenseTask != null) {
                Location expected = defenseTask.getExpectedTeleportLocation();

                // If it's a plugin-driven teleport/rotation, ignore it
                if (expected != null && expected.getWorld() == event.getTo().getWorld()) {
                    double distSq = expected.distanceSquared(event.getTo());
                    // A tiny threshold handles floating-point inaccuracies
                    if (distSq < 0.05) {
                        defenseTask.clearExpectedTeleportLocation();
                        return;
                    }
                }

                // Check if they actually moved (e.g. moved X, Y, or Z by > 0.05 blocks)
                Location from = event.getFrom();
                Location to = event.getTo();
                if (from.getWorld() != to.getWorld() || 
                    Math.abs(from.getX() - to.getX()) > 0.05 || 
                    Math.abs(from.getY() - to.getY()) > 0.05 || 
                    Math.abs(from.getZ() - to.getZ()) > 0.05) {

                    // Manual movement detected! Stop auto defense
                    mineTaskManager.stopAutoDefense(player, false);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) return;

        if (mineTaskManager.isMining(player)) {
            com.hereblingy.hereblingy.task.MineTask task = mineTaskManager.getActiveTask(player);
            if (task != null) {
                task.handleSuffocationRescue();
            }
        }
    }
}
