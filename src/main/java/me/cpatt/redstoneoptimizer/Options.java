package me.cpatt.redstoneoptimizer;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public final class Options {

    public final boolean itemsOn;
    public final int itemChunks;
    public final int itemSweepEvery;
    public final boolean mergeOn;
    public final int mergeEvery;
    public final double mergeRange;
    public final double mergeRangeWhenLagging;
    public final int mergeBudget;
    public final boolean redstoneOn;
    public final boolean quietAll;
    public final int redstoneChunks;
    public final boolean pistonEvents;
    public final int repairChunks;
    public final int repairEvery;
    public final int repairBatch;
    public final int staleLimit;
    public final Set<Material> redstoneParts;
    public final boolean hoppersOn;
    public final int hopperChunks;
    public final int hopperCap;
    public final boolean tuneHoppers;
    public final int extraTransfer;
    public final int extraCheck;
    public final int hopperTuneEvery;
    public final boolean farBlocksOn;
    public final boolean farBlocksToView;
    public final int hideChunks;
    public final int restoreChunks;
    public final int hideEvery;
    public final int scanBudget;
    public final int maxHidden;
    public final boolean lagWatchOn;
    public final double lowTps;
    public final double goodTps;
    public final int tpsSampleEvery;

    private Options(FileConfiguration config) {
        this.itemsOn = config.getBoolean("items.enabled", true);
        this.itemChunks = clamp(config.getInt("items.tracking-chunks", 2), 1, 32);
        this.itemSweepEvery = clamp(config.getInt("items.sweep-interval-ticks", 40), 5, 1200);
        this.mergeOn = config.getBoolean("items.merge.enabled", true);
        this.mergeEvery = clamp(config.getInt("items.merge.interval-ticks", 40), 5, 1200);
        this.mergeRange = clamp(config.getDouble("items.merge.radius", 1.25D), 0.25D, 8.0D);
        this.mergeRangeWhenLagging = clamp(config.getDouble("items.merge.low-tps-radius", 2.5D), this.mergeRange, 12.0D);
        this.mergeBudget = clamp(config.getInt("items.merge.max-merges-per-pass", 2048), 1, 20000);
        this.redstoneOn = config.getBoolean("redstone.enabled", true);
        this.quietAll = config.getBoolean("redstone.quiet-all", true);
        this.redstoneChunks = clamp(config.getInt("redstone.view-chunks", 2), 0, 32);
        this.pistonEvents = config.getBoolean("redstone.block-events", true);
        this.repairChunks = clamp(config.getInt("redstone.repair-chunks", 2), 1, 32);
        this.repairEvery = clamp(config.getInt("redstone.refresh-interval-ticks", 20), 1, 200);
        this.repairBatch = clamp(config.getInt("redstone.refresh-batch", 512), 1, 8192);
        this.staleLimit = clamp(config.getInt("redstone.max-dirty-positions", 4096), 64, 65536);
        this.redstoneParts = readParts(config.getStringList("redstone.materials"));
        this.hoppersOn = config.getBoolean("hoppers.enabled", true);
        this.hopperChunks = clamp(config.getInt("hoppers.region-chunks", 8), 1, 16);
        this.hopperCap = clamp(config.getInt("hoppers.max-per-region", 4000), 1, 200000);
        this.tuneHoppers = config.getBoolean("hoppers.lag-tuning.enabled", true);
        this.extraTransfer = clamp(config.getInt("hoppers.lag-tuning.extra-transfer-ticks", 4), 0, 40);
        this.extraCheck = clamp(config.getInt("hoppers.lag-tuning.extra-check-ticks", 1), 0, 20);
        this.hopperTuneEvery = clamp(config.getInt("hoppers.lag-tuning.interval-ticks", 100), 20, 1200);
        this.farBlocksOn = config.getBoolean("far-blocks.enabled", true);
        this.farBlocksToView = config.getBoolean("far-blocks.hide-to-view", true);
        this.hideChunks = clamp(config.getInt("far-blocks.hide-chunks", 10), 2, 16);
        this.restoreChunks = clamp(config.getInt("far-blocks.restore-chunks", 8), 1, 16);
        this.hideEvery = clamp(config.getInt("far-blocks.interval-ticks", 10), 5, 200);
        this.scanBudget = clamp(config.getInt("far-blocks.scan-budget", 6), 1, 16);
        this.maxHidden = clamp(config.getInt("far-blocks.max-hidden", 20000), 1000, 200000);
        this.lagWatchOn = config.getBoolean("tps-guard.enabled", true);
        this.lowTps = clamp(config.getDouble("tps-guard.low-tps", 17.0D), 1.0D, 20.0D);
        this.goodTps = clamp(config.getDouble("tps-guard.recover-tps", 19.5D), this.lowTps, 20.0D);
        this.tpsSampleEvery = clamp(config.getInt("tps-guard.check-interval-ticks", 100), 20, 1200);
    }

    public static Options read(FileConfiguration config) {
        return new Options(config);
    }

    public boolean isRedstonePart(Material material) {
        if (material == null) {
            return false;
        }
        if (redstoneParts.contains(material)) {
            return true;
        }
        String name = material.name();
        return name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_COPPER_BULB");
    }

    public int redstoneRange() {
        return quietAll ? 0 : redstoneChunks * 16;
    }

    public int itemRange() {
        return itemChunks * 16;
    }

    private static Set<Material> readParts(List<String> names) {
        Set<Material> parts = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                parts.add(material);
            }
        }
        return parts;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
