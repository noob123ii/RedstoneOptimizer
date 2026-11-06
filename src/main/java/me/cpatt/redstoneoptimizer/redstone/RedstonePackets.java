package me.cpatt.redstoneoptimizer.redstone;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import me.cpatt.redstoneoptimizer.Options;
import me.cpatt.redstoneoptimizer.util.Channels;
import me.cpatt.redstoneoptimizer.util.PacketTrust;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class RedstonePackets implements Listener {

    private static final String HANDLER = "redstoneoptimizer";
    private static final String BYPASS = "redstoneoptimizer.bypass.redstone";

    private final Plugin plugin;
    private final Options options;
    private final BlockPackets blockPackets;
    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
    private final AtomicBoolean repairing = new AtomicBoolean();
    private final AtomicLong heldPackets = new AtomicLong();
    private BukkitTask task;
    private boolean running;

    public RedstonePackets(Plugin plugin, Options options) {
        this.plugin = plugin;
        this.options = options;
        this.blockPackets = new BlockPackets(plugin);
        this.blockPackets.pistonEvents(options.pistonEvents);
    }

    public void start() {
        if (!options.redstoneOn || !blockPackets.usable()) {
            return;
        }
        running = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            attach(player);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::repairAll, options.repairEvery, options.repairEvery);
    }

    public void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            detach(player);
        }
        viewers.clear();
        HandlerList.unregisterAll(this);
    }

    public int viewerCount() {
        return viewers.size();
    }

    public long heldCount() {
        return heldPackets.get();
    }

    public int staleCount() {
        int total = 0;
        for (Viewer viewer : viewers.values()) {
            total += viewer.stale.count();
        }
        return total;
    }

    void attach(Player player) {
        if (!running || player.hasPermission(BYPASS)) {
            return;
        }
        Viewer current = viewers.get(player.getUniqueId());
        if (current != null) {
            current.movedTo(player.getLocation());
            return;
        }
        try {
            Channel channel = channelOf(player);
            if (channel == null) {
                return;
            }
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(HANDLER) != null) {
                pipeline.remove(HANDLER);
            }
            if (pipeline.get("packet_handler") == null) {
                return;
            }
            Viewer viewer = new Viewer(player, channel);
            pipeline.addBefore("packet_handler", HANDLER, viewer);
            viewers.put(player.getUniqueId(), viewer);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not watch redstone packets for " + player.getName() + ": " + throwable.getMessage());
        }
    }

    void detach(Player player) {
        Viewer viewer = viewers.remove(player.getUniqueId());
        if (viewer == null) {
            return;
        }
        try {
            Channel channel = channelOf(player);
            if (channel == null) {
                return;
            }
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(HANDLER) != null) {
                pipeline.remove(HANDLER);
            }
        } catch (Throwable ignored) {
        }
    }

    private void repairAll() {
        if (!running || !repairing.compareAndSet(false, true)) {
            return;
        }
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Viewer viewer = viewers.get(player.getUniqueId());
                if (viewer == null) {
                    attach(player);
                    continue;
                }
                viewer.repair(player);
            }
        } finally {
            repairing.set(false);
        }
    }

    private Channel channelOf(Player player) throws Exception {
        return Channels.of(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer != null) {
            viewer.movedTo(to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer == null || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            viewer.clearStale();
        }
        viewer.movedTo(event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer == null) {
            return;
        }
        viewer.clearStale();
        viewer.movedTo(event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer != null && event.getRespawnLocation() != null) {
            viewer.movedTo(event.getRespawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        attach(event.getPlayer());
        if (!viewers.containsKey(event.getPlayer().getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> attach(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        detach(event.getPlayer());
    }

    private final class Viewer extends ChannelDuplexHandler {

        private final StaleBlocks stale;
        private final BlockRules rules;
        private final Channel channel;
        private final UUID playerId;
        private volatile double x;
        private volatile double y;
        private volatile double z;
        private volatile double rangeSq;
        private volatile double repairRangeSq;
        private volatile boolean active;
        private volatile boolean refreshing;

        private Viewer(Player player, Channel channel) {
            this.stale = new StaleBlocks(options.staleLimit);
            this.channel = channel;
            this.playerId = player.getUniqueId();
            int viewRange = options.redstoneRange();
            int repairRange = Math.max(viewRange, options.repairChunks * 16);
            this.rangeSq = (double) viewRange * viewRange;
            this.repairRangeSq = (double) repairRange * repairRange;
            this.active = true;
            movedTo(player.getLocation());
            this.rules = new BlockRules() {
                @Override
                public boolean redstone(Material material) {
                    return options.isRedstonePart(material);
                }

                @Override
                public boolean far(int blockX, int blockY, int blockZ) {
                    if (rangeSq <= 0.0D) {
                        return true;
                    }
                    return distanceSq(blockX, blockY, blockZ) > rangeSq;
                }

                @Override
                public boolean insideSection(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                    if (rangeSq <= 0.0D) {
                        return false;
                    }
                    return distanceSq(minX, minY, minZ) <= rangeSq
                            && distanceSq(minX, minY, maxZ) <= rangeSq
                            && distanceSq(minX, maxY, minZ) <= rangeSq
                            && distanceSq(minX, maxY, maxZ) <= rangeSq
                            && distanceSq(maxX, minY, minZ) <= rangeSq
                            && distanceSq(maxX, minY, maxZ) <= rangeSq
                            && distanceSq(maxX, maxY, minZ) <= rangeSq
                            && distanceSq(maxX, maxY, maxZ) <= rangeSq;
                }

                @Override
                public void remember(int blockX, int blockY, int blockZ) {
                    stale.remember(blockX, blockY, blockZ);
                }
            };
        }

        private double distanceSq(int blockX, int blockY, int blockZ) {
            double dx = blockX + 0.5D - x;
            double dy = blockY + 0.5D - y;
            double dz = blockZ + 0.5D - z;
            return dx * dx + dy * dy + dz * dz;
        }

        private void movedTo(Location location) {
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
        }

        private void clearStale() {
            stale.forgetAll();
        }

        private void repair(Player player) {
            if (!active || !player.isOnline()) {
                return;
            }
            if (player.hasPermission(BYPASS)) {
                active = false;
                stale.forgetAll();
                return;
            }
            World world = player.getWorld();
            List<BlockState> states = new ArrayList<>();
            List<Long> keys = new ArrayList<>();
            for (Long key : stale.positions()) {
                if (states.size() >= options.repairBatch) {
                    break;
                }
                int blockX = StaleBlocks.xOf(key);
                int blockY = StaleBlocks.yOf(key);
                int blockZ = StaleBlocks.zOf(key);
                if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                    stale.forget(key);
                    continue;
                }
                if (distanceSq(blockX, blockY, blockZ) > repairRangeSq) {
                    continue;
                }
                Block block = world.getBlockAt(blockX, blockY, blockZ);
                states.add(block.getState());
                keys.add(key);
            }
            if (states.isEmpty()) {
                return;
            }
            refreshing = true;
            try {
                player.sendBlockChanges(states);
                for (Long key : keys) {
                    stale.forget(key);
                }
                channel.eventLoop().execute(() -> refreshing = false);
            } catch (Throwable throwable) {
                refreshing = false;
                plugin.getLogger().warning("Could not refresh stale redstone: " + throwable.getMessage());
            }
        }

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            if (!active || refreshing || PacketTrust.trusted(playerId)) {
                super.write(context, message, promise);
                return;
            }
            Object outgoing;
            try {
                outgoing = blockPackets.screen(message, rules);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Redstone packet trimming failed: " + throwable.getMessage());
                outgoing = message;
            }
            if (outgoing == null) {
                heldPackets.incrementAndGet();
                if (promise != null) {
                    promise.setSuccess();
                }
                return;
            }
            super.write(context, outgoing, promise);
        }
    }
}
