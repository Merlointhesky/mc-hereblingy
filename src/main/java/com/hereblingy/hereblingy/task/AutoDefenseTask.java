package com.hereblingy.hereblingy.task;

import com.hereblingy.hereblingy.HereBlingyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spider;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class AutoDefenseTask extends BukkitRunnable {

    private final HereBlingyPlugin plugin;
    private final Player player;
    private Location expectedTeleportLocation = null;
    private int attackCooldown = 0;

    public AutoDefenseTask(HereBlingyPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public Location getExpectedTeleportLocation() {
        return expectedTeleportLocation;
    }

    public void clearExpectedTeleportLocation() {
        this.expectedTeleportLocation = null;
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }

        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }

        if (handleDefense()) {
            attackCooldown = 8; // pause auto-attacking for 8 ticks to honor weapon cooldown
        }
    }

    private boolean handleDefense() {
        double attackRadius = 4.0;
        Location loc = player.getLocation();
        org.bukkit.entity.LivingEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        if (loc.getWorld() == null) return false;

        for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, attackRadius, attackRadius, attackRadius)) {
            if (entity instanceof Monster ||
                entity instanceof Slime ||
                entity instanceof Phantom ||
                entity instanceof Spider) {

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

        if (closest == null) return false;

        // Equip best weapon
        int slot = findBestWeaponSlot();
        if (slot != -1) {
            if (player.getInventory().getHeldItemSlot() != slot) {
                player.getInventory().setHeldItemSlot(slot);
            }
        }

        // Face monster and sweep
        Location targetEye = closest.getEyeLocation();
        Location playerEye = player.getEyeLocation();
        Vector dir = targetEye.toVector().subtract(playerEye.toVector()).normalize();

        Location look = player.getLocation();
        look.setDirection(dir);

        // Mark this teleport as expected so PlayerMoveEvent doesn't deactivate auto-defense
        expectedTeleportLocation = look.clone();

        player.teleport(look);

        player.attack(closest);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        player.sendActionBar(Component.text("⚔ AFK Auto-defense: Fending off " + closest.getName() + "!").color(NamedTextColor.RED));

        return true;
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
}
