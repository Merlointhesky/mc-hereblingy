package com.hereblingy.hereblingy.command;

import com.hereblingy.hereblingy.HereBlingyPlugin;
import com.hereblingy.hereblingy.auraskills.AuraSkillsHelper;
import com.hereblingy.hereblingy.config.MiningConfigUI;
import com.hereblingy.hereblingy.map.ScanManager;
import com.hereblingy.hereblingy.map.ScanResult;
import com.hereblingy.hereblingy.path.PathGenerator;
import com.hereblingy.hereblingy.selection.SelectionManager;
import com.hereblingy.hereblingy.task.MineTask;
import com.hereblingy.hereblingy.task.MineTaskManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class HereBlingyCommand implements CommandExecutor {

    private final SelectionManager selectionManager;
    private final MineTaskManager mineTaskManager;
    private final AuraSkillsHelper auraSkillsHelper;
    private final ScanManager scanManager;
    private final SetupWizardCommand setupWizardCommand;
    private final MiningConfigUI configUI;
    private final com.hereblingy.hereblingy.config.MiningConfigManager configManager;

    public HereBlingyCommand(SelectionManager selectionManager, MineTaskManager mineTaskManager,
                             AuraSkillsHelper auraSkillsHelper, ScanManager scanManager,
                             SetupWizardCommand setupWizardCommand, MiningConfigUI configUI) {
        this.selectionManager = selectionManager;
        this.mineTaskManager = mineTaskManager;
        this.auraSkillsHelper = auraSkillsHelper;
        this.scanManager = scanManager;
        this.setupWizardCommand = setupWizardCommand;
        this.configUI = configUI;
        this.configManager = HereBlingyPlugin.getInstance().getMiningConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can execute this command!").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start" -> handleStart(player, args);
            case "stop" -> handleStop(player);
            case "restart" -> handleRestart(player);
            case "clear" -> handleClear(player);
            case "setup" -> handleSetup(player);
            case "config" -> handleConfig(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleStart(Player player, String[] args) {
        if (mineTaskManager.isMining(player)) {
            player.sendMessage(Component.text("Stopping previous mining run...").color(NamedTextColor.YELLOW));
            mineTaskManager.stopTask(player);
        }

        com.hereblingy.hereblingy.config.PlayerMiningConfig playerConfig = configManager.getPlayerConfig(player.getUniqueId());
        com.hereblingy.hereblingy.config.PlayerMiningConfig.MiningMode mode = playerConfig.getMiningMode();

        if (mode == com.hereblingy.hereblingy.config.PlayerMiningConfig.MiningMode.STRIP_MINING) {
            // Infinite Dynamic Strip Mining Mode (Selection-Free)
            int branchWidth = 16;
            if (args.length > 1) {
                try {
                    branchWidth = Integer.parseInt(args[1]);
                    if (branchWidth < 2 || branchWidth > 100) {
                        player.sendMessage(Component.text("Side branch width must be between 2 and 100 blocks!").color(NamedTextColor.RED));
                        return;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid width! Using default: 16").color(NamedTextColor.YELLOW));
                }
            }

            // Generate initial segment: 16 steps forward
            List<org.bukkit.Location> path = PathGenerator.generateDynamicSegment(player.getLocation(), 
                    PathGenerator.getFacingDirection(player.getLocation().getYaw()), 0, 16, branchWidth);

            mineTaskManager.cachePath(player.getUniqueId(), path);
            mineTaskManager.clearLastStop(player);

            MineTask task = new MineTask(HereBlingyPlugin.getInstance(), player, path, auraSkillsHelper, player.getLocation(), branchWidth);
            mineTaskManager.startTask(player, task);

            player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
            player.sendMessage(Component.text("   Infinite Strip Mining Started!  ").color(NamedTextColor.GOLD));
            player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
            player.sendMessage(Component.text(" ✔ Tunnel Width: 2x2 main trunk, 2x1 branches").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text(" ✔ Side Branches: dug every 3 blocks (extending " + branchWidth + " blocks left/right)").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text(" ✔ Y Level: " + player.getLocation().getBlockY() + " (mines straight ahead infinitely)").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text(" Type '/hb stop' at any time to pause and show stats.").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
        } else {
            // Bounded 3D Terraforming / Land Clearing Mode (Ignoring Trees/Hubris)
            if (!selectionManager.hasCompleteSelection(player.getUniqueId())) {
                player.sendMessage(Component.text("Please select a 3D area first (Shift-Right-Click Point A and Point B with a pickaxe).")
                        .color(NamedTextColor.RED));
                return;
            }

            player.sendMessage(Component.text("Analyzing terraforming region bounds...").color(NamedTextColor.GOLD));

            Location pointA = selectionManager.getPointA(player.getUniqueId());
            Location pointB = selectionManager.getPointB(player.getUniqueId());

            scanManager.scanAreaAsync(player.getUniqueId(), pointA, pointB, result -> {
                player.sendMessage(Component.text("Scan complete! Cleared materials summary:")
                        .color(NamedTextColor.GREEN));
                player.sendMessage(Component.text(" - Total Bounding Blocks: " + result.getTotalBlocks()).color(NamedTextColor.GRAY));
                player.sendMessage(Component.text(" - Excavatable Blocks: " + result.getMineableCount()).color(NamedTextColor.GREEN));
                player.sendMessage(Component.text(" - Fluid Blocks to Seal: " + result.getFluidCount()).color(NamedTextColor.BLUE));

                // Generate horizontal snake slices from top to bottom
                List<Location> path = PathGenerator.generateTerraformingPath(pointA, pointB);
                if (path.isEmpty()) {
                    player.sendMessage(Component.text("Failed to generate path!").color(NamedTextColor.RED));
                    return;
                }

                mineTaskManager.cachePath(player.getUniqueId(), path);
                mineTaskManager.clearLastStop(player);

                MineTask task = new MineTask(HereBlingyPlugin.getInstance(), player, path, auraSkillsHelper, result);
                mineTaskManager.startTask(player, task);

                player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("       Terraforming Started!       ").color(NamedTextColor.GOLD));
                player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text(" ✔ Mode: Clearing all ground, sand, soils, and stone inside boundaries").color(NamedTextColor.GREEN));
                player.sendMessage(Component.text(" ✔ Safety: ignoring trees, leaves, and other hubris").color(NamedTextColor.GREEN));
                player.sendMessage(Component.text(" ✔ Total Coordinates: " + path.size() + " steps").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text(" Runs until selected area is completely empty!").color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
            });
        }
    }

    private void handleStop(Player player) {
        if (!mineTaskManager.isMining(player)) {
            player.sendMessage(Component.text("Auto-mining is not currently active.").color(NamedTextColor.RED));
            return;
        }
        mineTaskManager.stopTask(player);
        player.sendMessage(Component.text("Auto-mining task stopped.").color(NamedTextColor.YELLOW));
    }

    private void handleRestart(Player player) {
        if (mineTaskManager.isMining(player)) {
            player.sendMessage(Component.text("Auto-mining is already active.").color(NamedTextColor.YELLOW));
            return;
        }

        if (!mineTaskManager.hasLastStop(player)) {
            player.sendMessage(Component.text("No paused mining run to resume. Use '/hb start' to begin.").color(NamedTextColor.RED));
            return;
        }

        List<org.bukkit.Location> path = mineTaskManager.getCachedPath(player.getUniqueId());
        if (path == null || path.isEmpty()) {
            player.sendMessage(Component.text("No cached path found. Please start a new run with '/hb start'.").color(NamedTextColor.RED));
            return;
        }

        int index = mineTaskManager.getLastStopIndex(player);
        if (index >= path.size()) {
            index = 0;
        }

        com.hereblingy.hereblingy.config.PlayerMiningConfig playerConfig = configManager.getPlayerConfig(player.getUniqueId());
        com.hereblingy.hereblingy.config.PlayerMiningConfig.MiningMode mode = playerConfig.getMiningMode();

        MineTask task;
        if (mode == com.hereblingy.hereblingy.config.PlayerMiningConfig.MiningMode.STRIP_MINING) {
            task = new MineTask(HereBlingyPlugin.getInstance(), player, path, auraSkillsHelper, player.getLocation(), 16);
        } else {
            task = new MineTask(HereBlingyPlugin.getInstance(), player, path, auraSkillsHelper, null);
        }
        
        task.setCurrentIndex(index);
        
        mineTaskManager.startTask(player, task);
        mineTaskManager.clearLastStop(player);

        player.sendMessage(Component.text("Auto-mining resumed from block index " + index + " of " + path.size() + "!").color(NamedTextColor.GREEN));
    }

    private void handleClear(Player player) {
        selectionManager.clearSelection(player.getUniqueId());
        scanManager.clearScan(player.getUniqueId());
        mineTaskManager.clearLastStop(player);
        mineTaskManager.clearCachedPath(player.getUniqueId());
        player.sendMessage(Component.text("Cleared all selections, cached paths, and paused progress.").color(NamedTextColor.GREEN));
    }

    private void handleSetup(Player player) {
        setupWizardCommand.onCommand(player);
    }

    private void handleConfig(Player player) {
        configUI.openMainMenu(player);
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("       HereBlingy Command Help       ").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text(" /hb start [length] [width] - Start strip mining straight ahead (default: 64 16)").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text(" /hb stop - Stop active mining run").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text(" /hb restart - Resume paused mining run at current block").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text(" /hb config - Open chest storage configuration GUI").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text(" /hb setup - Run chest deposit & supply wizard").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text(" /hb clear - Reset all selections, routes, and pauses").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
    }
}
