package com.hereblingy.hereblingy.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MiningConfigUI {

    private final MiningConfigManager configManager;

    public MiningConfigUI(MiningConfigManager configManager) {
        this.configManager = configManager;
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§0HereBlingy - Mining Config");

        // Fill background with black stained glass panes
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ", new String[0]);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Category buttons
        inv.setItem(11, createGuiItem(Material.DIAMOND, "§6§lTreasures Submenu", new String[]{
                "§7Configure ore drops, gems, raw ores, and valuable resources.",
                "",
                "§eClick to open!"
        }));

        inv.setItem(13, createGuiItem(Material.STONE, "§6§lStandard Blocks Submenu", new String[]{
                "§7Configure stone, deepslate, netherrack, and dense rock.",
                "",
                "§eClick to open!"
        }));

        inv.setItem(15, createGuiItem(Material.GRAVEL, "§6§lDebris & Soils Submenu", new String[]{
                "§7Configure dirt, gravel, sand, mud, and soft materials.",
                "",
                "§eClick to open!"
        }));

        PlayerMiningConfig config = configManager.getPlayerConfig(player.getUniqueId());
        PlayerMiningConfig.MiningMode mode = config.getMiningMode();
        
        Material modeIcon = (mode == PlayerMiningConfig.MiningMode.STRIP_MINING) ? Material.DIAMOND_PICKAXE : Material.NETHERITE_SHOVEL;
        String modeName = (mode == PlayerMiningConfig.MiningMode.STRIP_MINING) ? "§bStrip Mining" : "§eTerraforming";
        
        inv.setItem(22, createGuiItem(modeIcon, "§6§lActive Job: §b" + modeName, new String[]{
                "§7Toggle between our two automations:",
                "",
                "§e§lStrip Mining§7: Infinite 2x2 tunnel & 2x1 branches straight ahead.",
                "§d§lTerraforming§7: Excavates a selected 3D region completely (ignores trees).",
                "",
                "§d§nClick§7 to switch active job!"
        }));

        player.openInventory(inv);
    }

    public void openTreasuresMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, "§0HereBlingy - Treasures");
        fillSubmenu(player, inv, new Material[]{
                Material.DIAMOND, Material.EMERALD, Material.RAW_IRON, Material.RAW_GOLD,
                Material.RAW_COPPER, Material.REDSTONE, Material.LAPIS_LAZULI, Material.COAL,
                Material.QUARTZ, Material.ANCIENT_DEBRIS, Material.GOLD_NUGGET, Material.NETHERITE_INGOT,
                Material.GILDED_BLACKSTONE, Material.GLOWSTONE, Material.FLINT
        });
        player.openInventory(inv);
    }

    public void openStandardMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, "§0HereBlingy - Standard Blocks");
        fillSubmenu(player, inv, new Material[]{
                Material.STONE, Material.DEEPSLATE, Material.NETHERRACK, Material.BLACKSTONE,
                Material.BASALT, Material.SANDSTONE, Material.OBSIDIAN, Material.TUFF,
                Material.CALCITE
        });
        player.openInventory(inv);
    }

    public void openDebrisMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, "§0HereBlingy - Debris & Soils");
        fillSubmenu(player, inv, new Material[]{
                Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.GRAVEL,
                Material.SAND, Material.RED_SAND, Material.MUD, Material.CLAY,
                Material.CLAY_BALL, Material.SOUL_SAND, Material.SOUL_SOIL, Material.MAGMA_BLOCK
        });
        player.openInventory(inv);
    }

    private void fillSubmenu(Player player, Inventory inv, Material[] materials) {
        UUID uuid = player.getUniqueId();

        // Fill background
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ", new String[0]);
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, filler);
        }

        // Set materials
        int slot = 9;
        for (Material mat : materials) {
            boolean mined = configManager.isMineEnabled(uuid, mat);
            MiningSettings.Destination dest = configManager.getDestination(uuid, mat);

            String statusStr = mined ? "§aEnabled" : "§cDisabled";
            String destStr = switch (dest) {
                case KEEP_CHEST -> "§6Treasures (Keep Chest)";
                case TRASH_CHEST -> "§cDebris (Trash Chest)";
                case INVENTORY -> "§bInventory (Keep on Person)";
            };

            inv.setItem(slot++, createGuiItem(mat, "§e§l" + formatName(mat.name()), new String[]{
                    "§7Mining Break: " + statusStr,
                    "§7Destination: " + destStr,
                    "",
                    "§8§l[Left-Click] §8Toggle Mining Break",
                    "§8§l[Right-Click] §8Cycle Destination"
            }));
        }

        // Back button
        inv.setItem(31, createGuiItem(Material.ARROW, "§c§lBack to Main Menu", new String[]{
                "§7Return to category selection."
        }));
    }

    private ItemStack createGuiItem(Material material, String name, String[] lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> list = new ArrayList<>();
            for (String s : lore) {
                list.add(s);
            }
            meta.setLore(list);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatName(String raw) {
        String[] split = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String s : split) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return sb.toString();
    }
}
