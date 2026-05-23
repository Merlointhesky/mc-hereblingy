package com.hereblingy.hereblingy.task;

import com.hereblingy.hereblingy.HereBlingyPlugin;
import com.hereblingy.hereblingy.auraskills.AuraSkillsHelper;
import com.hereblingy.hereblingy.config.MiningConfigManager;
import com.hereblingy.hereblingy.config.MiningSettings;
import com.hereblingy.hereblingy.map.ScanManager;
import com.hereblingy.hereblingy.map.ScanResult;
import com.hereblingy.hereblingy.path.PathGenerator;
import com.hereblingy.hereblingy.setup.SetupConfiguration;
import com.hereblingy.hereblingy.setup.SetupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MineTask extends BukkitRunnable {

    private static final double SPEED = 0.22;
    private static final double SNAP_DISTANCE = 0.3;
    private static final double MAX_DIRECT_STEP_DISTANCE = 1.75;
    private static final int MINE_PAUSE_TICKS = 6;
    private static final int STUCK_TICK_THRESHOLD = 12;

    private final HereBlingyPlugin plugin;
    private final Player player;
    private final List<Location> path;
    private final AuraSkillsHelper auraSkillsHelper;
    private final ScanManager scanManager;
    private final SetupManager setupManager;
    private final MiningConfigManager configManager;
    private ScanResult scanResult;
    
    private int currentIndex = 0;
    private int minePause = 0;
    private int stuckTicks = 0;
    private int lastTargetIndex = -1;
    private double lastDist = Double.MAX_VALUE;

    // Infinite Mode parameters
    private boolean isInfiniteMode = false;
    private Location startLoc;
    private PathGenerator.FacingDirection facing;
    private int lengthGenerated = 0;
    private int branchWidth = 16;

    // Activity tracking stats
    private final Map<Material, Integer> minedTreasures = new HashMap<>();
    private final Map<Material, Integer> minedDebris = new HashMap<>();
    private int refuelsCount = 0;
    private int leaksPluggedCount = 0;

    public MineTask(HereBlingyPlugin plugin, Player player, List<Location> path,
                    AuraSkillsHelper auraSkillsHelper, ScanResult scanResult) {
        this.plugin = plugin;
        this.player = player;
        this.path = path;
        this.auraSkillsHelper = auraSkillsHelper;
        this.scanManager = plugin.getScanManager();
        this.setupManager = plugin.getSetupManager();
        this.configManager = plugin.getMiningConfigManager();
        this.scanResult = scanResult;
        this.isInfiniteMode = false;
    }

    public MineTask(HereBlingyPlugin plugin, Player player, List<Location> path,
                    AuraSkillsHelper auraSkillsHelper, Location startLoc, int branchWidth) {
        this.plugin = plugin;
        this.player = player;
        this.path = path;
        this.auraSkillsHelper = auraSkillsHelper;
        this.scanManager = plugin.getScanManager();
        this.setupManager = plugin.getSetupManager();
        this.configManager = plugin.getMiningConfigManager();
        this.scanResult = null;
        this.isInfiniteMode = true;
        this.startLoc = startLoc.clone();
        this.branchWidth = branchWidth;
        this.facing = PathGenerator.getFacingDirection(startLoc.getYaw());
        this.lengthGenerated = 16; // Initial segment starts at 16 blocks deep
    }

    public HereBlingyPlugin getPlugin() {
        return plugin;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }

        // 1. Inventory Full Check
        if (isInventoryFull()) {
            if (attemptDumpToChests()) {
                player.sendMessage(Component.text("Inventory cleared into deposit chests. Resuming...").color(NamedTextColor.GREEN));
            } else {
                sendActivitySummary();
                cancel();
                plugin.getMineTaskManager().recordDurabilityStop(player, currentIndex);
                player.sendMessage(Component.text("Inventory full! Auto-mining paused. Configure Keep/Trash chests or clear inventory, then use /hb restart to resume.")
                        .color(NamedTextColor.RED));
                return;
            }
        }

        if (path.isEmpty()) {
            cancel();
            return;
        }

        // 2. Count down mining cooldown delay
        if (minePause > 0) {
            minePause--;
            return;
        }

        // 2b. Skip cobwebs to avoid entering cobweb areas!
        while (currentIndex < path.size() && isCobwebAt(path.get(currentIndex))) {
            currentIndex++;
            if (currentIndex >= path.size()) {
                sendActivitySummary();
                player.sendMessage(Component.text("Auto-mining run completed successfully!").color(NamedTextColor.GREEN));
                cancel();
                return;
            }
            player.sendActionBar(Component.text("Avoiding and bypassing cobweb area...").color(NamedTextColor.YELLOW));
        }

        Location target = path.get(currentIndex);
        Location current = player.getLocation();

        if (current.getWorld() != target.getWorld()) {
            sendActivitySummary();
            cancel();
            player.sendMessage(Component.text("Auto-mining stopped — you left the mining area.")
                    .color(NamedTextColor.RED));
            return;
        }

        // 3. Mine target blocks BEFORE entering them to prevent suffocation!
        // This ensures the player always stands in a 2-block high air space and mines safely from a distance.
        boolean mined = executeMiningAt(target);
        if (mined) {
            minePause = MINE_PAUSE_TICKS;
            // Apply velocity towards target slightly to keep player oriented, but do not snap or teleport yet
            Vector direction = new Vector(target.getX() - current.getX(), target.getY() - current.getY(), target.getZ() - current.getZ());
            if (direction.lengthSquared() > 0.01) {
                player.setVelocity(direction.normalize().multiply(0.05));
            }
            return; // Stand still in the safe air space until the blocks are mined!
        }

        double dx = target.getX() - current.getX();
        double dy = target.getY() - current.getY();
        double dz = target.getZ() - current.getZ();
        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // 4. Collision / Stuck checking
        if (currentIndex != lastTargetIndex) {
            lastTargetIndex = currentIndex;
            lastDist = totalDist;
            stuckTicks = 0;
        } else {
            if (totalDist >= lastDist - 0.02) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
            lastDist = totalDist;
        }

        if (totalDist > MAX_DIRECT_STEP_DISTANCE || stuckTicks >= STUCK_TICK_THRESHOLD) {
            teleportToTarget(current, target);
            stuckTicks = 0;
            return;
        }

        // 5. Check for fluid leaks adjacent to current position and plug them
        checkAndPlugLeaks(current);

        // 6. Check if we arrived at target (which is now guaranteed to be air!)
        if (totalDist < SNAP_DISTANCE) {
            teleportToTarget(current, target);
            currentIndex++;

            // If in infinite mode and getting close to the end, extend the path dynamically!
            if (isInfiniteMode && currentIndex >= path.size() - 6) {
                List<Location> nextSeg = PathGenerator.generateDynamicSegment(startLoc, facing, lengthGenerated, 8, branchWidth);
                path.addAll(nextSeg);
                lengthGenerated += 8;
            }

            if (currentIndex >= path.size()) {
                sendActivitySummary();
                player.sendMessage(Component.text("Auto-mining run completed all coordinates successfully!").color(NamedTextColor.GREEN));
                cancel();
            }
        } else {
            // Move player towards target using velocity (safely walking through already cleared air)
            Vector direction = new Vector(dx, dy, dz).normalize();
            double speedMultiplier = 1.0 + (auraSkillsHelper.getMiningLevel(player) * 0.008);
            Vector velocity = direction.multiply(SPEED * speedMultiplier);
            player.setVelocity(velocity);
        }
    }

    private void teleportToTarget(Location current, Location target) {
        Location snap = target.clone();
        snap.setPitch(current.getPitch());
        snap.setYaw(current.getYaw());
        player.teleport(snap);
    }

    private boolean mineBlock(Block block) {
        if (isSolidAndMineable(block)) {
            if (verifyToolAndDurability(block.getType())) {
                block.breakNaturally(player.getInventory().getItemInMainHand());
                return true;
            }
        }
        return false;
    }

    private boolean executeMiningAt(Location loc) {
        boolean broken = false;

        boolean isMainTunnel = (loc.getX() == Math.floor(loc.getX())) || (loc.getZ() == Math.floor(loc.getZ()));
        if (isInfiniteMode && isMainTunnel) {
            World world = loc.getWorld();
            int y = loc.getBlockY();
            boolean isNorthSouth = (loc.getX() == Math.floor(loc.getX()));

            if (isNorthSouth) {
                int x1 = (int) Math.floor(loc.getX());
                int x2 = x1 - 1;
                int z = loc.getBlockZ();

                Block feet1 = world.getBlockAt(x1, y, z);
                Block head1 = world.getBlockAt(x1, y + 1, z);
                Block feet2 = world.getBlockAt(x2, y, z);
                Block head2 = world.getBlockAt(x2, y + 1, z);

                broken |= mineBlock(feet1);
                broken |= mineBlock(head1);
                broken |= mineBlock(feet2);
                broken |= mineBlock(head2);
            } else {
                int z1 = (int) Math.floor(loc.getZ());
                int z2 = z1 - 1;
                int x = loc.getBlockX();

                Block feet1 = world.getBlockAt(x, y, z1);
                Block head1 = world.getBlockAt(x, y + 1, z1);
                Block feet2 = world.getBlockAt(x, y, z2);
                Block head2 = world.getBlockAt(x, y + 1, z2);

                broken |= mineBlock(feet1);
                broken |= mineBlock(head1);
                broken |= mineBlock(feet2);
                broken |= mineBlock(head2);
            }
        } else {
            Block feet = loc.getBlock();
            Block head = loc.clone().add(0, 1, 0).getBlock();
            broken |= mineBlock(feet);
            broken |= mineBlock(head);
        }

        // 3. Attract drops, apply Mending XP sharing
        if (broken) {
            attractDropsAndRepairMending(loc);
        }

        return broken;
    }

    private boolean isSolidAndMineable(Block block) {
        Material type = block.getType();
        if (type.isAir() || !type.isSolid()) return false;
        
        // Bypass logs, wood, leaves, stems (trees/hubris)
        String name = type.name();
        if (name.contains("LOG") || name.contains("LEAVES") || name.contains("WOOD") 
                || name.contains("STEM") || type == Material.MANGROVE_ROOTS) {
            return false;
        }

        // Obstructed or fluid blocks are bypassed
        if (type == Material.BEDROCK || type == Material.SPAWNER || type == Material.WATER || type == Material.LAVA) {
            return false;
        }

        // Check if config allows breaking this block
        return configManager.isMineEnabled(player.getUniqueId(), type);
    }

    private boolean verifyToolAndDurability(Material blockType) {
        // 1. Select and swap to appropriate tool type
        boolean isShovel = isShovelWorthy(blockType);
        String targetToolKeyword = isShovel ? "SHOVEL" : "PICKAXE";

        int hotbarSlot = findToolSlotInHotbar(targetToolKeyword);
        if (hotbarSlot != -1) {
            if (player.getInventory().getHeldItemSlot() != hotbarSlot) {
                player.getInventory().setHeldItemSlot(hotbarSlot);
            }
        } else {
            // Check main inventory and swap into slot 0
            int invSlot = findToolSlotInInventory(targetToolKeyword);
            if (invSlot != -1) {
                ItemStack tool = player.getInventory().getItem(invSlot);
                ItemStack currentSlot0 = player.getInventory().getItem(0);
                
                player.getInventory().setItem(invSlot, currentSlot0);
                player.getInventory().setItem(0, tool);
                player.getInventory().setHeldItemSlot(0);
                player.updateInventory();
            } else {
                // If chest is configured, try supply chest
                if (refuelFromToolSupplyChest(isShovel)) {
                    // Refuelled successfully!
                } else {
                    player.sendMessage(Component.text("No suitable tool (" + targetToolKeyword + ") found! Mining paused.")
                            .color(NamedTextColor.RED));
                    MineTask.this.cancel();
                    return false;
                }
            }
        }

        // 2. Check Durability
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getItemMeta() instanceof Damageable damageable) {
            int maxDur = held.getType().getMaxDurability();
            int currentDur = maxDur - damageable.getDamage();
            SetupConfiguration setup = setupManager.getSetupConfig(player.getUniqueId());
            int threshold = setup != null ? setup.getDurabilityThreshold() : 10;

            if (currentDur <= threshold) {
                // Low durability swap
                if (refuelFromToolSupplyChest(isShovel)) {
                    player.sendMessage(Component.text("Tool durability depleted! Swapped tool at Supply Chest.").color(NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("Tool durability low (" + currentDur + ")! Mining paused to prevent breakage.")
                            .color(NamedTextColor.RED));
                    MineTask.this.cancel();
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isShovelWorthy(Material material) {
        return switch (material) {
            case DIRT, GRASS_BLOCK, COARSE_DIRT, ROOTED_DIRT, CLAY, GRAVEL,
                 SAND, RED_SAND, MUD, SNOW, SNOW_BLOCK, POWDER_SNOW,
                 SOUL_SAND, SOUL_SOIL -> true;
            default -> false;
        };
    }

    private int findToolSlotInHotbar(String keyword) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType().name().contains(keyword) && item.getAmount() > 0) {
                return i;
            }
        }
        return -1;
    }

    private int findToolSlotInInventory(String keyword) {
        for (int i = 9; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType().name().contains(keyword) && item.getAmount() > 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean refuelFromToolSupplyChest(boolean isShovel) {
        SetupConfiguration setup = setupManager.getSetupConfig(player.getUniqueId());
        if (setup == null || setup.getToolSupplyChest() == null) return false;

        Location chestLoc = setup.getToolSupplyChest();
        if (!(chestLoc.getBlock().getState() instanceof Container container)) return false;

        String keyword = isShovel ? "SHOVEL" : "PICKAXE";
        Inventory chestInv = container.getInventory();

        // Find fresh tool in chest
        int freshSlot = -1;
        for (int i = 0; i < chestInv.getSize(); i++) {
            ItemStack item = chestInv.getItem(i);
            if (item != null && item.getType().name().contains(keyword)) {
                if (item.getItemMeta() instanceof Damageable dmg) {
                    // Make sure it is relatively healthy
                    if (item.getType().getMaxDurability() - dmg.getDamage() > 20) {
                        freshSlot = i;
                        break;
                    }
                } else {
                    freshSlot = i;
                    break;
                }
            }
        }

        if (freshSlot == -1) return false;

        // Teleport to chest, exchange items, teleport back
        Location originalLoc = player.getLocation();
        player.teleport(chestLoc.clone().add(0.5, 1.0, 0.5));

        ItemStack freshTool = chestInv.getItem(freshSlot);
        ItemStack wornTool = player.getInventory().getItemInMainHand();

        // Place worn tool in chest, and fresh tool in player's hand
        chestInv.setItem(freshSlot, wornTool);
        player.getInventory().setItemInMainHand(freshTool);
        player.updateInventory();

        refuelsCount++;

        // Return player immediately
        player.teleport(originalLoc);
        return true;
    }

    private void checkAndPlugLeaks(Location current) {
        World world = current.getWorld();
        int cx = current.getBlockX();
        int cy = current.getBlockY();
        int cz = current.getBlockZ();

        // 5 neighboring directions: East, West, South, North, and Top-ceiling
        int[][] directions = {
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 2, 0}
        };

        for (int[] dir : directions) {
            Block block = world.getBlockAt(cx + dir[0], cy + dir[1], cz + dir[2]);
            if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                // We have a leak! Seal it.
                Material plugMat = getMostAbundantPlugMaterial();
                if (plugMat != null) {
                    block.setType(plugMat);
                    removeInventoryItem(plugMat);
                    leaksPluggedCount++;
                    world.playSound(block.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 0.8f);
                    player.sendActionBar(Component.text("Plugged leak with " + plugMat.name()).color(NamedTextColor.YELLOW));
                }
            }
        }
    }

    private Material getMostAbundantPlugMaterial() {
        Material[] allowedPlugs = {
                Material.COBBLESTONE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.NETHERRACK
        };

        Material highestMaterial = null;
        int highestCount = 0;

        for (Material mat : allowedPlugs) {
            int count = countInventoryItems(mat);
            if (count > highestCount) {
                highestCount = count;
                highestMaterial = mat;
            }
        }

        return highestMaterial;
    }

    private int countInventoryItems(Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeInventoryItem(Material material) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == material) {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(i, item.getAmount() > 0 ? item : null);
                player.updateInventory();
                return;
            }
        }
    }

    private void attractDropsAndRepairMending(Location loc) {
        double radius = 6.0;
        World world = loc.getWorld();

        // 1. Attract Dropped Items (Ores & Blocks)
        world.getNearbyEntities(loc, radius, radius, radius, entity -> entity instanceof Item)
                .forEach(entity -> {
                    Item dropped = (Item) entity;
                    ItemStack stack = dropped.getItemStack();

                    // Sort items immediately
                    routeItemToStorage(stack);
                    dropped.remove();
                });

        // 2. Scan and Attract Experience Orbs to power Mending
        world.getNearbyEntities(loc, radius, radius, radius, entity -> entity instanceof ExperienceOrb)
                .forEach(entity -> {
                    ExperienceOrb orb = (ExperienceOrb) entity;
                    int xp = orb.getExperience();
                    
                    // Share/apply XP to hotbar tools to repair Mending
                    int leftoverXp = applySharedMendingRepair(xp);
                    if (leftoverXp > 0) {
                        player.giveExp(leftoverXp);
                    }

                    // Gain AuraSkills Mining experience
                    auraSkillsHelper.addMiningXp(player, xp * 2.0);

                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.2f);
                    orb.remove();
                });
    }

    private int applySharedMendingRepair(int xp) {
        if (xp <= 0) return 0;

        ItemStack pickaxe = null;
        ItemStack shovel = null;

        // Scan hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getAmount() > 0) {
                if (item.getType().name().contains("PICKAXE") && item.getEnchantmentLevel(Enchantment.MENDING) > 0) {
                    pickaxe = item;
                } else if (item.getType().name().contains("SHOVEL") && item.getEnchantmentLevel(Enchantment.MENDING) > 0) {
                    shovel = item;
                }
            }
        }

        int remainingXp = xp;

        // Each 1 XP point restores 2 durability
        while (remainingXp > 0) {
            ItemStack repairTarget = null;
            
            // Choose the target with lower durability ratio
            int pickDmg = getDurabilityDamage(pickaxe);
            int shovDmg = getDurabilityDamage(shovel);

            if (pickDmg > 0 && shovDmg > 0) {
                repairTarget = (pickDmg >= shovDmg) ? pickaxe : shovel;
            } else if (pickDmg > 0) {
                repairTarget = pickaxe;
            } else if (shovDmg > 0) {
                repairTarget = shovel;
            }

            if (repairTarget == null) {
                break; // Both fully repaired
            }

            Damageable dmg = (Damageable) repairTarget.getItemMeta();
            int newDmg = Math.max(0, dmg.getDamage() - 2);
            dmg.setDamage(newDmg);
            repairTarget.setItemMeta(dmg);

            remainingXp--;
        }

        player.updateInventory();
        return remainingXp;
    }

    private int getDurabilityDamage(ItemStack item) {
        if (item == null) return 0;
        if (item.getItemMeta() instanceof Damageable dmg) {
            return dmg.getDamage();
        }
        return 0;
    }

    private void routeItemToStorage(ItemStack stack) {
        Material mat = stack.getType();
        MiningSettings.Destination dest = configManager.getDestination(player.getUniqueId(), mat);

        if (dest == MiningSettings.Destination.KEEP_CHEST) {
            minedTreasures.put(mat, minedTreasures.getOrDefault(mat, 0) + stack.getAmount());
            depositIntoChest(stack, true);
        } else if (dest == MiningSettings.Destination.TRASH_CHEST) {
            minedDebris.put(mat, minedDebris.getOrDefault(mat, 0) + stack.getAmount());
            depositIntoChest(stack, false);
        } else {
            // Keep in inventory
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                // If inventory overflows, try to dump anyway
                for (ItemStack overflowStack : leftover.values()) {
                    depositIntoChest(overflowStack, false);
                }
            }
        }
    }

    private void depositIntoChest(ItemStack stack, boolean isKeepChest) {
        SetupConfiguration setup = setupManager.getSetupConfig(player.getUniqueId());
        if (setup != null) {
            Location chestLoc = isKeepChest ? setup.getKeepChest() : setup.getTrashChest();
            if (chestLoc != null && chestLoc.getBlock().getState() instanceof Container container) {
                Inventory chestInv = container.getInventory();
                HashMap<Integer, ItemStack> leftover = chestInv.addItem(stack);
                if (leftover.isEmpty()) {
                    return; // Deposited completely!
                } else {
                    // Try to distribute leftover to player inventory
                    stack.setAmount(leftover.values().iterator().next().getAmount());
                }
            }
        }

        // Fallback: place in player inventory
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            // Hard fallback: spawn at player feet to avoid deletion
            ItemStack fall = leftover.values().iterator().next();
            player.getWorld().dropItemNaturally(player.getLocation(), fall);
        }
    }

    private boolean isInventoryFull() {
        return player.getInventory().firstEmpty() == -1;
    }

    private boolean attemptDumpToChests() {
        SetupConfiguration setup = setupManager.getSetupConfig(player.getUniqueId());
        if (setup == null || setup.getKeepChest() == null || setup.getTrashChest() == null) return false;

        Inventory playerInv = player.getInventory();
        boolean successfullyClearedAny = false;

        for (int i = 0; i < 36; i++) {
            ItemStack item = playerInv.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            Material mat = item.getType();
            if (mat.name().contains("PICKAXE") || mat.name().contains("SHOVEL")) continue; // Keep tools

            MiningSettings.Destination dest = configManager.getDestination(player.getUniqueId(), mat);
            if (dest == MiningSettings.Destination.INVENTORY) continue;

            boolean isKeep = (dest == MiningSettings.Destination.KEEP_CHEST);
            Location chestLoc = isKeep ? setup.getKeepChest() : setup.getTrashChest();

            if (chestLoc != null && chestLoc.getBlock().getState() instanceof Container container) {
                Inventory chestInv = container.getInventory();
                HashMap<Integer, ItemStack> leftover = chestInv.addItem(item);
                if (leftover.isEmpty()) {
                    playerInv.setItem(i, null);
                    successfullyClearedAny = true;
                } else {
                    ItemStack remaining = leftover.values().iterator().next();
                    playerInv.setItem(i, remaining);
                    if (remaining.getAmount() < item.getAmount()) {
                        successfullyClearedAny = true;
                    }
                }
            }
        }

        if (successfullyClearedAny) {
            player.updateInventory();
        }
        return successfullyClearedAny;
    }

    public void sendActivitySummary() {
        if (minedTreasures.isEmpty() && minedDebris.isEmpty() && refuelsCount == 0 && leaksPluggedCount == 0) {
            return;
        }

        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("       HereBlingy Activity Summary       ").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));

        if (!minedTreasures.isEmpty()) {
            player.sendMessage(Component.text("★ Treasures Collected:").color(NamedTextColor.YELLOW));
            for (Map.Entry<Material, Integer> entry : minedTreasures.entrySet()) {
                player.sendMessage(Component.text("  - " + formatMaterialName(entry.getKey()) + ": x" + entry.getValue())
                        .color(NamedTextColor.GREEN));
            }
        }

        if (!minedDebris.isEmpty()) {
            player.sendMessage(Component.text("■ Debris Processed:").color(NamedTextColor.DARK_GRAY));
            int totalDebris = 0;
            for (int count : minedDebris.values()) {
                totalDebris += count;
            }
            player.sendMessage(Component.text("  - Total cobblestone, stone, soils: x" + totalDebris)
                    .color(NamedTextColor.GRAY));
        }

        if (refuelsCount > 0) {
            player.sendMessage(Component.text("🔧 Tool Supplies Replaced: " + refuelsCount).color(NamedTextColor.AQUA));
        }

        if (leaksPluggedCount > 0) {
            player.sendMessage(Component.text("🪣 Fluid Leaks Sealed: " + leaksPluggedCount).color(NamedTextColor.RED));
        }

        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
    }

    private String formatMaterialName(Material mat) {
        String[] split = mat.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String s : split) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return sb.toString();
    }

    private boolean isCobwebAt(Location loc) {
        if (loc == null) return false;
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        return feet.getType() == Material.COBWEB || head.getType() == Material.COBWEB;
    }
}
