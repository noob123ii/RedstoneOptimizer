package me.cpatt.redstoneoptimizer;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import me.cpatt.redstoneoptimizer.command.AdminCommands;
import me.cpatt.redstoneoptimizer.hoppers.HopperGuard;
import me.cpatt.redstoneoptimizer.items.ItemPiles;
import me.cpatt.redstoneoptimizer.items.ItemTracking;
import me.cpatt.redstoneoptimizer.lag.LagWatch;
import me.cpatt.redstoneoptimizer.redstone.RedstonePackets;
import me.cpatt.redstoneoptimizer.render.FarBlocks;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class RedstoneOptimizer extends JavaPlugin implements Listener {

    private Options options;
    private LagWatch lagWatch;
    private ItemTracking itemTracking;
    private ItemPiles itemPiles;
    private RedstonePackets redstonePackets;
    private HopperGuard hopperGuard;
    private FarBlocks farBlocks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        options = Options.read(getConfig());
        getServer().getPluginManager().registerEvents(this, this);
        startModules();
        PluginCommand command = getCommand("redstoneoptimizer");
        if (command != null) {
            AdminCommands executor = new AdminCommands(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getLogger().info("Items " + options.itemChunks + " chunks, redstone " + options.redstoneChunks
                + " chunks, hopper cap " + options.hopperCap + " per " + options.hopperChunks + " chunks");
    }

    @Override
    public void onDisable() {
        stopModules();
        HandlerList.unregisterAll((Listener) this);
    }

    private void startModules() {
        lagWatch = new LagWatch(this, options);
        itemTracking = new ItemTracking(this, options);
        itemPiles = new ItemPiles(this, options, lagWatch);
        redstonePackets = new RedstonePackets(this, options);
        hopperGuard = new HopperGuard(this, options, lagWatch);
        farBlocks = new FarBlocks(this, options);
        lagWatch.start();
        itemTracking.start();
        itemPiles.start();
        redstonePackets.start();
        hopperGuard.start();
        farBlocks.start();
    }

    private void stopModules() {
        if (farBlocks != null) {
            farBlocks.stop();
            farBlocks = null;
        }
        if (hopperGuard != null) {
            hopperGuard.stop();
            hopperGuard = null;
        }
        if (redstonePackets != null) {
            redstonePackets.stop();
            redstonePackets = null;
        }
        if (itemPiles != null) {
            itemPiles.stop();
            itemPiles = null;
        }
        if (itemTracking != null) {
            itemTracking.stop();
            itemTracking = null;
        }
        if (lagWatch != null) {
            lagWatch.stop();
            lagWatch = null;
        }
    }

    public boolean reloadAll() {
        Options previous = options;
        stopModules();
        try {
            reloadConfig();
            options = Options.read(getConfig());
            startModules();
            return true;
        } catch (Throwable throwable) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not reload the config, keeping the old one", throwable);
            options = previous;
            startModules();
            return false;
        }
    }

    public boolean resetConfig() {
        Options previous = options;
        stopModules();
        try {
            saveResource("config.yml", true);
            reloadConfig();
            options = Options.read(getConfig());
            startModules();
            return true;
        } catch (Throwable throwable) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not reset the config, keeping the old one", throwable);
            options = previous;
            startModules();
            return false;
        }
    }

    @EventHandler
    public void onItemAdded(EntityAddToWorldEvent event) {
        if (itemTracking != null && event.getEntity() instanceof Item item) {
            itemTracking.limit(item);
        }
    }

    @EventHandler
    public void onWorldLoaded(WorldLoadEvent event) {
        if (itemTracking != null) {
            itemTracking.visitWorld(event.getWorld());
        }
    }

    public Options options() {
        return options;
    }

    public LagWatch lagWatch() {
        return lagWatch;
    }

    public ItemTracking itemTracking() {
        return itemTracking;
    }

    public ItemPiles itemPiles() {
        return itemPiles;
    }

    public RedstonePackets redstonePackets() {
        return redstonePackets;
    }

    public HopperGuard hopperGuard() {
        return hopperGuard;
    }

    public FarBlocks farBlocks() {
        return farBlocks;
    }
}
