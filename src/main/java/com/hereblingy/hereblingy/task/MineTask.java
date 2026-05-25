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
    public synchronized void cancel() throws IllegalStateException {
        plugin.getMineTaskManager().removeActiveTask(player.getUniqueId());
        super.cancel();
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }

        // 1a. Check for hostiles and defend!
        if (handleDefense()) {
            minePause = 8; // pause auto-mining for 8 ticks to honor weapon sweep cooldown
            return;
        }

        // 1b. Check food status and eat if below maximum well-fed saturation!
        if (player.getFoodLevel() < 20) {
            handleFeeding();
            if (minePause > 0) return; // return if currently eating
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

        // 2c. If in infinite strip-mining mode, scan ahead. 
        // If the next 10 blocks along the path (and their surrounding walls/ceiling/floor) contain only air, auto-stop!
        if (isInfiniteMode) {
            boolean hasSolidAhead = false;
            World world = target.getWorld();
            int maxCheck = Math.min(path.size(), currentIndex + 10);

            if (path.size() < currentIndex + 10) {
                List<Location> nextSeg = PathGenerator.generateDynamicSegment(startLoc, facing, lengthGenerated, 16, branchWidth);
                path.addAll(nextSeg);
                lengthGenerated += 16;
                maxCheck = Math.min(path.size(), currentIndex + 10);
            }

            for (int i = currentIndex; i < maxCheck; i++) {
                Location loc = path.get(i);
                int tx = loc.getBlockX();
                int ty = loc.getBlockY();
                int tz = loc.getBlockZ();

                for (int dy = -1; dy <= 2; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Block block = world.getBlockAt(tx + dx, ty + dy, tz + dz);
                            if (block.getType().isSolid()) {
                                hasSolidAhead = true;
                                break;
                            }
                        }
                        if (hasSolidAhead) break;
                    }
                    if (hasSolidAhead) break;
                }
                if (hasSolidAhead) break;
            }

            if (!hasSolidAhead) {
                sendActivitySummary();
                player.sendMessage(Component.text("Auto-mining stopped — reached open empty air space (no blocks ahead to mine).").color(NamedTextColor.YELLOW));
                cancel();
                return;
            }
        }

        // Check if player has fallen down a mountain or is too far from target, immediately teleport back
        double dx = target.getX() - current.getX();
        double dy = target.getY() - current.getY();
        double dz = target.getZ() - current.getZ();
        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (totalDist > MAX_DIRECT_STEP_DISTANCE) {
            teleportToTarget(current, target);
            stuckTicks = 0;
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

        if (stuckTicks >= STUCK_TICK_THRESHOLD) {
            // Bypass stuck block and continue to the next path node
            currentIndex = (currentIndex + 1) % path.size();
            Location nextTarget = path.get(currentIndex);
            teleportToTarget(current, nextTarget);
            stuckTicks = 0;
            player.sendMessage(Component.text("Bypassed stuck coordinate and continuing to next path point...").color(NamedTextColor.YELLOW));
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
        Location safeTarget = findSafeTeleportLocation(target);
        Location snap = safeTarget.clone();
        snap.setPitch(current.getPitch());
        snap.setYaw(current.getYaw());
        player.teleport(snap);
    }

    private Location findSafeTeleportLocation(Location target) {
        World world = target.getWorld();
        int targetX = target.getBlockX();
        int targetY = target.getBlockY();
        int targetZ = target.getBlockZ();

        Location bestLoc = null;
        double bestDistSq = Double.MAX_VALUE;
        boolean foundGround = false;

        // Search in a 2-block radius
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int x = targetX + dx;
                    int y = targetY + dy;
                    int z = targetZ + dz;

                    Block feet = world.getBlockAt(x, y, z);
                    Block head = world.getBlockAt(x, y + 1, z);
                    Block stand = world.getBlockAt(x, y - 1, z);

                    // Spot is air/passable for feet and head
                    if ((feet.getType().isAir() || !feet.getType().isSolid()) &&
                        (head.getType().isAir() || !head.getType().isSolid())) {

                        boolean ground = stand.getType().isSolid();
                        double distSq = dx * dx + dy * dy + dz * dz;

                        if (ground && !foundGround) {
                            // First time finding a spot with ground, override any spot without ground
                            bestDistSq = distSq;
                            bestLoc = new Location(world, x + 0.5, y, z + 0.5);
                            foundGround = true;
                        } else if (ground == foundGround) {
                            // If both have ground (or both do not), take the closest one
                            if (distSq < bestDistSq) {
                                bestDistSq = distSq;
                                bestLoc = new Location(world, x + 0.5, y, z + 0.5);
                            }
                        }
                    }
                }
            }
        }

        return bestLoc != null ? bestLoc : target;
    }

    private org.bukkit.entity.LivingEntity findNearbyHostileMob() {
        double attackRadius = 3.5;
        Location loc = player.getLocation();
        org.bukkit.entity.LivingEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, attackRadius, attackRadius, attackRadius)) {
            if (entity instanceof org.bukkit.entity.Monster || 
                entity instanceof org.bukkit.entity.Slime || 
                entity instanceof org.bukkit.entity.Phantom ||
                entity instanceof org.bukkit.entity.Spider) {
                
                if (entity instanceof org.bukkit.entity.LivingEntity living) {
                    if (!living.isDead() && player.hasLineOfSight(living)) {
                        double distSq = loc.distanceSquared(living.getLocation());
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closest = living;
                        }
                    }
                }
            }
        }
        return closest;
    }

    private int findBestWeaponSlot() {
        int bestSlot = -1;
        double maxDamage = -1.0;

        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getAmount() == 0) continue;

            String name = item.getType().name();
            double dmgValue = 0.0;

            if (name.contains("SWORD")) {
                dmgValue = 100.0;
            } else if (name.contains("AXE") && !name.contains("PICKAXE")) {
                dmgValue = 80.0;
            } else if (name.contains("PICKAXE")) {
                dmgValue = 60.0;
            } else if (name.contains("SHOVEL")) {
                dmgValue = 40.0;
            }

            if (name.startsWith("NETHERITE")) {
                dmgValue += 5.0;
            } else if (name.startsWith("DIAMOND")) {
                dmgValue += 4.0;
            } else if (name.startsWith("IRON")) {
                dmgValue += 3.0;
            } else if (name.startsWith("STONE")) {
                dmgValue += 2.0;
            } else if (name.startsWith("GOLD")) {
                dmgValue += 1.0;
            }

            if (dmgValue > maxDamage) {
                maxDamage = dmgValue;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private boolean handleDefense() {
        org.bukkit.entity.LivingEntity target = findNearbyHostileMob();
        if (target == null) return false;

        int slot = findBestWeaponSlot();
        if (slot != -1) {
            if (player.getInventory().getHeldItemSlot() != slot) {
                player.getInventory().setHeldItemSlot(slot);
            }
        }

        Location targetEye = target.getEyeLocation();
        Location playerEye = player.getEyeLocation();
        Vector dir = targetEye.toVector().subtract(playerEye.toVector()).normalize();
        
        Location look = player.getLocation();
        look.setDirection(dir);
        player.teleport(look);

        player.attack(target);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        player.sendActionBar(Component.text("⚔ Fending off " + target.getName() + "!").color(NamedTextColor.RED));

        return true;
    }

    private boolean mineBlockWithoutVein(Block block) {
        if (isSolidAndMineable(block)) {
            if (verifyToolAndDurability(block.getType())) {
                block.breakNaturally(player.getInventory().getItemInMainHand());
                return true;
            }
        }
        return false;
    }

    private boolean mineBlock(Block block) {
        Material type = block.getType();
        if (isSolidAndMineable(block)) {
            if (verifyToolAndDurability(type)) {
                block.breakNaturally(player.getInventory().getItemInMainHand());
                if (isOreBlock(type)) {
                    mineVein(block, type);
                }
                return true;
            }
        }
        return false;
    }

    private boolean isSameOreType(Material m1, Material m2) {
        if (m1 == m2) return true;
        if (isOreBlock(m1) && isOreBlock(m2)) {
            String name1 = m1.name().replace("DEEPSLATE_", "").replace("NETHER_", "");
            String name2 = m2.name().replace("DEEPSLATE_", "").replace("NETHER_", "");
            return name1.equals(name2);
        }
        return false;
    }

    private void mineVein(Block startBlock, Material oreType) {
        java.util.Queue<Block> queue = new java.util.LinkedList<>();
        java.util.Set<Location> visited = new java.util.HashSet<>();
        
        Location startLoc = startBlock.getLocation();
        visited.add(startLoc);
        enqueueAdjacent(startBlock, oreType, queue, visited, startLoc);
        
        int minedCount = 0;
        int maxVeinBlocks = 64;
        
        while (!queue.isEmpty() && minedCount < maxVeinBlocks) {
            Block block = queue.poll();
            if (!isSameOreType(block.getType(), oreType)) continue;
            
            if (mineBlockWithoutVein(block)) {
                minedCount++;
                enqueueAdjacent(block, oreType, queue, visited, startLoc);
            }
        }
    }

    private void enqueueAdjacent(Block block, Material oreType, java.util.Queue<Block> queue, java.util.Set<Location> visited, Location startLoc) {
        World world = block.getWorld();
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        
        int[][] directions = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
        };
        
        for (int[] dir : directions) {
            int x = bx + dir[0];
            int y = by + dir[1];
            int z = bz + dir[2];
            
            Location loc = new Location(world, x, y, z);
            if (visited.contains(loc)) continue;
            visited.add(loc);
            
            if (loc.distanceSquared(startLoc) > 36.0) continue;
            
            Block adj = world.getBlockAt(x, y, z);
            if (isSameOreType(adj.getType(), oreType)) {
                queue.add(adj);
            }
        }
    }

    private boolean isOreBlock(Material mat) {
        String name = mat.name();
        return name.contains("ORE") || mat == Material.ANCIENT_DEBRIS;
    }

    private boolean checkAndMineSides(World world, int x, int y, int z) {
        boolean broken = false;
        int[][] sideOffsets = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 0, 1}, {0, 0, -1}
        };

        for (int[] offset : sideOffsets) {
            Block sideFeet = world.getBlockAt(x + offset[0], y, z + offset[2]);
            Block sideHead = world.getBlockAt(x + offset[0], y + 1, z + offset[2]);

            if (isOreBlock(sideFeet.getType())) {
                broken |= mineBlock(sideFeet);
            }
            if (isOreBlock(sideHead.getType())) {
                broken |= mineBlock(sideHead);
            }
        }
        return broken;
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

                // Scan and collect ores directly below and above
                Block floor1 = world.getBlockAt(x1, y - 1, z);
                Block ceil1 = world.getBlockAt(x1, y + 2, z);
                Block floor2 = world.getBlockAt(x2, y - 1, z);
                Block ceil2 = world.getBlockAt(x2, y + 2, z);

                if (isOreBlock(floor1.getType())) broken |= mineBlock(floor1);
                if (isOreBlock(ceil1.getType())) broken |= mineBlock(ceil1);
                if (isOreBlock(floor2.getType())) broken |= mineBlock(floor2);
                if (isOreBlock(ceil2.getType())) broken |= mineBlock(ceil2);

                // Scan and collect ores on the left/right walls of the main tunnel
                broken |= checkAndMineSides(world, x1, y, z);
                broken |= checkAndMineSides(world, x2, y, z);
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

                // Scan and collect ores directly below and above
                Block floor1 = world.getBlockAt(x, y - 1, z1);
                Block ceil1 = world.getBlockAt(x, y + 2, z1);
                Block floor2 = world.getBlockAt(x, y - 1, z2);
                Block ceil2 = world.getBlockAt(x, y + 2, z2);

                if (isOreBlock(floor1.getType())) broken |= mineBlock(floor1);
                if (isOreBlock(ceil1.getType())) broken |= mineBlock(ceil1);
                if (isOreBlock(floor2.getType())) broken |= mineBlock(floor2);
                if (isOreBlock(ceil2.getType())) broken |= mineBlock(ceil2);

                // Scan and collect ores on the left/right walls of the main tunnel
                broken |= checkAndMineSides(world, x, y, z1);
                broken |= checkAndMineSides(world, x, y, z2);
            }
        } else {
            Block feet = loc.getBlock();
            Block head = loc.clone().add(0, 1, 0).getBlock();
            broken |= mineBlock(feet);
            broken |= mineBlock(head);

            if (isInfiniteMode) {
                // Scan and collect ores directly below and above in side branches
                Block floor = loc.clone().add(0, -1, 0).getBlock();
                Block ceil = loc.clone().add(0, 2, 0).getBlock();
                if (isOreBlock(floor.getType())) broken |= mineBlock(floor);
                if (isOreBlock(ceil.getType())) broken |= mineBlock(ceil);

                // Scan and collect ores on the left/right walls of the side branches
                broken |= checkAndMineSides(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            }
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

        // Avoid breaking storage containers ("boxes") to keep items safe
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL 
                || type == Material.ENDER_CHEST || name.contains("SHULKER_BOX")) {
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
            // Try to keep in player's inventory first so they are available to plug leaks in an emergency.
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                // If inventory overflows, deposit the leftovers into the trash chest.
                for (ItemStack overflowStack : leftover.values()) {
                    depositIntoChest(overflowStack, false);
                }
            }
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

    private int findFoodSlotInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getAmount() == 0) continue;

            Material mat = item.getType();
            switch (mat) {
                case COOKED_BEEF:
                case COOKED_PORKCHOP:
                case GOLDEN_CARROT:
                case COOKED_MUTTON:
                case COOKED_CHICKEN:
                case COOKED_SALMON:
                case COOKED_COD:
                case BAKED_POTATO:
                case BREAD:
                case GOLDEN_APPLE:
                case ENCHANTED_GOLDEN_APPLE:
                case APPLE:
                case CARROT:
                case COOKED_RABBIT:
                case MELON_SLICE:
                case COOKIE:
                case SWEET_BERRIES:
                case GLOW_BERRIES:
                    return i;
                default:
                    break;
            }
        }
        return -1;
    }

    private void handleFeeding() {
        if (player.getFoodLevel() >= 20) {
            return;
        }

        int foodSlot = findFoodSlotInHotbar();
        if (foodSlot == -1) {
            return;
        }

        ItemStack foodStack = player.getInventory().getItem(foodSlot);
        Material foodType = foodStack.getType();

        // Define food restoration values (Food points, Saturation points)
        int hungerRestore = 4;
        float saturationRestore = 6.0f;

        switch (foodType) {
            case COOKED_BEEF, COOKED_PORKCHOP, PUMPKIN_PIE -> {
                hungerRestore = 8;
                saturationRestore = 12.8f;
            }
            case GOLDEN_CARROT -> {
                hungerRestore = 6;
                saturationRestore = 14.4f;
            }
            case COOKED_MUTTON, COOKED_SALMON -> {
                hungerRestore = 6;
                saturationRestore = 9.6f;
            }
            case COOKED_CHICKEN -> {
                hungerRestore = 6;
                saturationRestore = 7.2f;
            }
            case COOKED_COD, BAKED_POTATO, BREAD -> {
                hungerRestore = 5;
                saturationRestore = 6.0f;
            }
            case GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE -> {
                hungerRestore = 4;
                saturationRestore = 9.6f;
                // Add golden apple potion effects
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 100, 1));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, 2400, 0));
            }
            case APPLE, CARROT -> {
                hungerRestore = 4;
                saturationRestore = 2.4f;
            }
            case COOKED_RABBIT -> {
                hungerRestore = 5;
                saturationRestore = 6.0f;
            }
            case MELON_SLICE, COOKIE, SWEET_BERRIES, GLOW_BERRIES -> {
                hungerRestore = 2;
                saturationRestore = 1.2f;
            }
            default -> {
                break;
            }
        }

        // Consume 1 food item from slot
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            foodStack.setAmount(foodStack.getAmount() - 1);
            player.getInventory().setItem(foodSlot, foodStack.getAmount() > 0 ? foodStack : null);
            player.updateInventory();
        }

        // Apply food and saturation
        int newFood = Math.min(20, player.getFoodLevel() + hungerRestore);
        float newSat = Math.min(20.0f, player.getSaturation() + saturationRestore);
        player.setFoodLevel(newFood);
        player.setSaturation(newSat);

        // Play eating and burp sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.0f);
        player.sendActionBar(Component.text("😋 Consumed " + formatMaterialName(foodType) + " to keep well-fed!").color(NamedTextColor.GREEN));

        // Pause briefly (e.g. 15 ticks) to simulate eating animation/cooldown
        minePause = 15;
    }
}
