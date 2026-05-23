package com.hereblingy.hereblingy;

import com.hereblingy.hereblingy.auraskills.AuraSkillsHelper;
import com.hereblingy.hereblingy.command.HereBlingyCommand;
import com.hereblingy.hereblingy.command.SetupWizardCommand;
import com.hereblingy.hereblingy.config.MiningConfigManager;
import com.hereblingy.hereblingy.config.MiningConfigUI;
import com.hereblingy.hereblingy.config.MiningConfigListener;
import com.hereblingy.hereblingy.listener.MineListener;
import com.hereblingy.hereblingy.listener.SetupWizardListener;
import com.hereblingy.hereblingy.map.ScanManager;
import com.hereblingy.hereblingy.selection.SelectionManager;
import com.hereblingy.hereblingy.setup.SetupManager;
import com.hereblingy.hereblingy.task.MineTaskManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class HereBlingyPlugin extends JavaPlugin {

    private static HereBlingyPlugin instance;
    private SelectionManager selectionManager;
    private final MineTaskManager mineTaskManager = new MineTaskManager();
    private final AuraSkillsHelper auraSkillsHelper = new AuraSkillsHelper();
    private ScanManager scanManager;
    private SetupManager setupManager;
    private MiningConfigManager miningConfigManager;
    private MiningConfigUI miningConfigUI;
    private SetupWizardCommand setupWizardCommand;

    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize managers
        this.selectionManager = new SelectionManager(this);
        this.scanManager = new ScanManager(this);
        this.setupManager = new SetupManager(this);
        this.miningConfigManager = new MiningConfigManager(this);
        this.miningConfigUI = new MiningConfigUI(miningConfigManager);
        
        auraSkillsHelper.init();
        
        // Create setup wizard command
        setupWizardCommand = new SetupWizardCommand(setupManager, this);
        
        // Register main command with all subcommands
        getCommand("hereblingy").setExecutor(new HereBlingyCommand(selectionManager, mineTaskManager, auraSkillsHelper, scanManager, setupWizardCommand, miningConfigUI));
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new MineListener(selectionManager, mineTaskManager, scanManager, setupManager, miningConfigManager), this);
        getServer().getPluginManager().registerEvents(new SetupWizardListener(setupManager), this);
        getServer().getPluginManager().registerEvents(setupWizardCommand, this);
        getServer().getPluginManager().registerEvents(new MiningConfigListener(miningConfigUI, miningConfigManager), this);
        
        // Start setup timeout checker
        new BukkitRunnable() {
            @Override
            public void run() {
                setupWizardCommand.checkTimeouts();
            }
        }.runTaskTimer(this, 0, 20); // Check every second
        
        getLogger().info("HereBlingy enabled!");
    }

    @Override
    public void onDisable() {
        mineTaskManager.stopAllTasks();
        getLogger().info("HereBlingy disabled!");
    }

    public static HereBlingyPlugin getInstance() {
        return instance;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public MineTaskManager getMineTaskManager() {
        return mineTaskManager;
    }

    public AuraSkillsHelper getAuraSkillsHelper() {
        return auraSkillsHelper;
    }

    public SetupManager getSetupManager() {
        return setupManager;
    }

    public MiningConfigManager getMiningConfigManager() {
        return miningConfigManager;
    }

    public MiningConfigUI getMiningConfigUI() {
        return miningConfigUI;
    }

    public ScanManager getScanManager() {
        return scanManager;
    }
}
