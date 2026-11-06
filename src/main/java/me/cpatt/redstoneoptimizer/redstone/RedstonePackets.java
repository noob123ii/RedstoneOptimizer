package me.cpatt.redstoneoptimizer.redstone;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import me.cpatt.redstoneoptimizer.Options;
import me.cpatt.redstoneoptimizer.util.Channels;
import me.cpatt.redstoneoptimizer.util.PacketTrust;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class RedstonePackets implements Listener {

    private static final String HANDLER = "redstoneoptimizer";
    private static final String BYPASS = "redstoneoptimizer.bypass.redstone";

    private final Plugin plugin;
    private final Options options;
    private final BlockPackets blockPackets;
    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
    private final AtomicLong heldPackets = new AtomicLong();
    private final double rangeSq;
    private boolean running;

    public RedstonePackets(Plugin plugin, Options options) {
        this.plugin = plugin;
        this.options = options;
        this.blockPackets = new BlockPackets(plugin);
        this.blockPackets.pistonEvents(options.pistonEvents);
        this.rangeSq = (double) options.farBlockDistance * options.farBlockDistance;
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
    }

    public void stop() {
        running = false;
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

    private void attach(Player player) {
        if (!running || player.hasPermission(BYPASS)) {
            return;
        }
        if (viewers.containsKey(player.getUniqueId())) {
            return;
        }
        try {
            Channel channel = Channels.of(player);
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

    private void detach(Player player) {
        Viewer viewer = viewers.remove(player.getUniqueId());
        if (viewer == null) {
            return;
        }
        try {
            Channel channel = viewer.channel;
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

        private final UUID playerId;
        private final Channel channel;
        private final BlockRules rules;
        private volatile boolean active = true;

        private Viewer(Player player, Channel channel) {
            this.playerId = player.getUniqueId();
            this.channel = channel;
            this.rules = new BlockRules() {
                @Override
                public boolean redstone(Material material) {
                    return options.isRedstonePart(material);
                }

                @Override
                public boolean far(int blockX, int blockY, int blockZ) {
                    double dx = blockX + 0.5D - player.getX();
                    double dy = blockY + 0.5D - player.getY();
                    double dz = blockZ + 0.5D - player.getZ();
                    return dx * dx + dy * dy + dz * dz > rangeSq;
                }

                @Override
                public boolean insideSection(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                    return distanceSq(player, minX, minY, minZ) <= rangeSq
                            && distanceSq(player, minX, minY, maxZ) <= rangeSq
                            && distanceSq(player, minX, maxY, minZ) <= rangeSq
                            && distanceSq(player, minX, maxY, maxZ) <= rangeSq
                            && distanceSq(player, maxX, minY, minZ) <= rangeSq
                            && distanceSq(player, maxX, minY, maxZ) <= rangeSq
                            && distanceSq(player, maxX, maxY, minZ) <= rangeSq
                            && distanceSq(player, maxX, maxY, maxZ) <= rangeSq;
                }
            };
        }

        private double distanceSq(Player player, int blockX, int blockY, int blockZ) {
            double dx = blockX + 0.5D - player.getX();
            double dy = blockY + 0.5D - player.getY();
            double dz = blockZ + 0.5D - player.getZ();
            return dx * dx + dy * dy + dz * dz;
        }

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            if (!active || PacketTrust.trusted(playerId)) {
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
