package me.cpatt.redstoneoptimizer.lag;

import me.cpatt.redstoneoptimizer.Options;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class LagWatch {

    private final Plugin plugin;
    private final Options options;
    private volatile boolean underPressure;
    private BukkitTask task;

    public LagWatch(Plugin plugin, Options options) {
        this.plugin = plugin;
        this.options = options;
    }

    public void start() {
        if (!options.lagWatchOn) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 100L, options.tpsSampleEvery);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean underPressure() {
        return underPressure;
    }

    public double tps() {
        return Bukkit.getTPS()[0];
    }

    private void sample() {
        if (!options.lagWatchOn) {
            underPressure = false;
            return;
        }
        double current = tps();
        if (current <= 0.0D) {
            return;
        }
        if (!underPressure && current < options.lowTps) {
            underPressure = true;
            plugin.getLogger().warning("Server is at " + String.format("%.2f", current) + " TPS, item merging will use a wider radius");
        } else if (underPressure && current >= options.goodTps) {
            underPressure = false;
            plugin.getLogger().info("Server is back at " + String.format("%.2f", current) + " TPS, item merging is back to normal");
        }
    }
}
