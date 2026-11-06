package me.cpatt.redstoneoptimizer.items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import me.cpatt.redstoneoptimizer.Options;
import me.cpatt.redstoneoptimizer.util.Reflection;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ItemTracking {

    private final Plugin plugin;
    private final Options options;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final AtomicLong rangeChanges = new AtomicLong();
    private final Map<UUID, SavedRange> oldRanges = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> oldWorldRanges = new ConcurrentHashMap<>();
    private final Method itemGetHandle;
    private final Method itemTracker;
    private final Field itemRange;
    private final Method worldGetHandle;
    private final Field spigotConfig;
    private final Field worldRange;
    private final boolean itemUsable;
    private final boolean worldUsable;
    private final boolean usable;
    private BukkitTask task;

    public ItemTracking(Plugin plugin, Options options) {
        this.plugin = plugin;
        this.options = options;
        boolean itemReady = false;
        boolean worldReady = false;
        Method itemHandle = null;
        Method tracker = null;
        Field range = null;
        Method worldHandle = null;
        Field spigot = null;
        Field misc = null;
        try {
            Class<?> craftItem = Class.forName("org.bukkit.craftbukkit.entity.CraftItem");
            Class<?> itemEntity = Class.forName("net.minecraft.world.entity.item.ItemEntity");
            Class<?> tracked = Class.forName("net.minecraft.server.level.ChunkMap$TrackedEntity");
            itemHandle = Reflection.method(craftItem, "getHandle");
            tracker = Reflection.method(itemEntity, "moonrise$getTrackedEntity");
            range = Reflection.field(tracked, "range");
            itemReady = true;
        } catch (Throwable ignored) {
        }
        try {
            Class<?> craftWorld = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> spigotWorld = Class.forName("org.spigotmc.SpigotWorldConfig");
            worldHandle = Reflection.method(craftWorld, "getHandle");
            spigot = Reflection.field(serverLevel, "spigotConfig");
            misc = Reflection.field(spigotWorld, "miscTrackingRange");
            worldReady = true;
        } catch (Throwable ignored) {
        }
        if (!itemReady && !worldReady) {
            plugin.getLogger().log(Level.WARNING, "Item tracking control is not available on this server build");
        }
        this.itemGetHandle = itemHandle;
        this.itemTracker = tracker;
        this.itemRange = range;
        this.worldGetHandle = worldHandle;
        this.spigotConfig = spigot;
        this.worldRange = misc;
        this.itemUsable = itemReady;
        this.worldUsable = worldReady;
        this.usable = itemReady || worldReady;
    }

    public void start() {
        if (!usable || !options.itemsOn) {
            return;
        }
        if (!itemUsable) {
            applyWorldRange();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::visitAll, 1L, options.itemSweepEvery);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (usable) {
            restoreRanges();
        }
    }

    public void limit(Item item) {
        if (!itemUsable || !options.itemsOn || item == null || !item.isValid()) {
            return;
        }
        try {
            Object tracker = trackerOf(item);
            if (tracker == null) {
                return;
            }
            int target = options.itemRange();
            int current = itemRange.getInt(tracker);
            SavedRange saved = oldRanges.get(item.getUniqueId());
            if (current == target) {
                if (saved != null) {
                    oldRanges.put(item.getUniqueId(), new SavedRange(saved.original, System.currentTimeMillis()));
                }
                return;
            }
            if (saved == null) {
                oldRanges.put(item.getUniqueId(), new SavedRange(current, System.currentTimeMillis()));
            } else {
                oldRanges.put(item.getUniqueId(), new SavedRange(saved.original, System.currentTimeMillis()));
            }
            itemRange.setInt(tracker, target);
            rangeChanges.incrementAndGet();
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Could not set item tracking range", throwable);
        }
    }

    public void visitWorld(World world) {
        if (!itemUsable || !options.itemsOn || world == null) {
            return;
        }
        for (Item item : world.getEntitiesByClass(Item.class)) {
            limit(item);
        }
    }

    public boolean usable() {
        return usable;
    }

    public long changed() {
        return rangeChanges.get();
    }

    private void visitAll() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        try {
            for (World world : Bukkit.getWorlds()) {
                visitWorld(world);
            }
            long cutoff = System.currentTimeMillis() - 120000L;
            oldRanges.entrySet().removeIf(entry -> entry.getValue().seenAt < cutoff);
        } finally {
            busy.set(false);
        }
    }

    private void applyWorldRange() {
        if (!worldUsable) {
            return;
        }
        int target = options.itemRange();
        for (World world : Bukkit.getWorlds()) {
            try {
                Object level = worldGetHandle.invoke(world);
                Object config = spigotConfig.get(level);
                int current = worldRange.getInt(config);
                oldWorldRanges.putIfAbsent(world.getUID(), current);
                if (current != target) {
                    worldRange.setInt(config, target);
                }
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "Could not set item range for " + world.getName(), throwable);
            }
        }
    }

    private void restoreRanges() {
        if (itemUsable) {
            for (World world : Bukkit.getWorlds()) {
                for (Item item : world.getEntitiesByClass(Item.class)) {
                    SavedRange saved = oldRanges.get(item.getUniqueId());
                    if (saved == null) {
                        continue;
                    }
                    try {
                        Object tracker = trackerOf(item);
                        if (tracker != null) {
                            itemRange.setInt(tracker, saved.original);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            oldRanges.clear();
        }
        if (worldUsable) {
            for (World world : Bukkit.getWorlds()) {
                Integer old = oldWorldRanges.get(world.getUID());
                if (old == null) {
                    continue;
                }
                try {
                    Object level = worldGetHandle.invoke(world);
                    Object config = spigotConfig.get(level);
                    worldRange.setInt(config, old);
                } catch (Throwable ignored) {
                }
            }
            oldWorldRanges.clear();
        }
    }

    private Object trackerOf(Item item) throws Exception {
        Object handle = itemGetHandle.invoke(item);
        return itemTracker.invoke(handle);
    }

    private record SavedRange(int original, long seenAt) {
    }
}
