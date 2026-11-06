package me.cpatt.redstoneoptimizer.render;

import io.netty.channel.Channel;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import me.cpatt.redstoneoptimizer.Options;
import me.cpatt.redstoneoptimizer.util.Channels;
import me.cpatt.redstoneoptimizer.util.PacketTrust;
import me.cpatt.redstoneoptimizer.util.Reflection;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class FarBlocks implements Listener {

    private static final String BYPASS = "redstoneoptimizer.bypass.farblocks";
    private static final BlockData AIR = Material.AIR.createBlockData();
    private static final int SEND_BATCH = 4096;

    private final Plugin plugin;
    private final Options options;
    private final Map<ChunkKey, Set<BlockPosition>> cache = new HashMap<>();
    private final Set<ChunkKey> queued = new HashSet<>();
    private final Deque<ChunkKey> scanQueue = new ArrayDeque<>();
    private final Map<UUID, Set<BlockPosition>> hidden = new HashMap<>();
    private final AtomicBoolean warned = new AtomicBoolean();
    private final Method chunkHandle;
    private final Object fullStatus;
    private final Method chunkSections;
    private final Method sectionMaybe;
    private final Method sectionState;
    private final Method stateMaterial;
    private final boolean available;
    private BukkitTask task;
    private int ticks;

    public FarBlocks(Plugin plugin, Options options) {
        this.plugin = plugin;
        this.options = options;
        boolean ready = false;
        Method handle = null;
        Object full = null;
        Method sections = null;
        Method maybe = null;
        Method state = null;
        Method material = null;
        try {
            Class<?> craftChunk = Class.forName("org.bukkit.craftbukkit.CraftChunk");
            Class<?> chunkStatus = Class.forName("net.minecraft.world.level.chunk.status.ChunkStatus");
            Class<?> levelChunk = Class.forName("net.minecraft.world.level.chunk.LevelChunk");
            Class<?> section = Class.forName("net.minecraft.world.level.chunk.LevelChunkSection");
            Class<?> blockState = Class.forName("net.minecraft.world.level.block.state.BlockState");
            handle = Reflection.method(craftChunk, "getHandle", chunkStatus);
            full = Reflection.field(chunkStatus, "FULL").get(null);
            sections = Reflection.method(levelChunk, "getSections");
            maybe = Reflection.method(section, "maybeHas", Predicate.class);
            state = Reflection.method(section, "getBlockState", int.class, int.class, int.class);
            material = Reflection.method(blockState, "getBukkitMaterial");
            ready = true;
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Far block hiding is not available on this server build", throwable);
        }
        this.chunkHandle = handle;
        this.fullStatus = full;
        this.chunkSections = sections;
        this.sectionMaybe = maybe;
        this.sectionState = state;
        this.stateMaterial = material;
        this.available = ready;
    }

    public void start() {
        if (!available || !options.farBlocksOn) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        restoreAll();
        cache.clear();
        queued.clear();
        scanQueue.clear();
        hidden.clear();
        HandlerList.unregisterAll(this);
    }

    public int hiddenCount(Player player) {
        Set<BlockPosition> positions = hidden.get(player.getUniqueId());
        return positions == null ? 0 : positions.size();
    }

    public int distance() {
        return options.farBlockDistance;
    }

    private void tick() {
        for (int i = 0; i < options.scanBudget; i++) {
            ChunkKey key = scanQueue.poll();
            if (key == null) {
                break;
            }
            queued.remove(key);
            scan(key);
        }
        ticks++;
        if (ticks % Math.min(10, options.hideEvery) == 0) {
            hidePass();
        }
    }

    private void hidePass() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(BYPASS)) {
                restorePlayer(player);
                continue;
            }
            hideFor(player);
        }
    }

    private void hideFor(Player player) {
        World world = player.getWorld();
        Chunk center = player.getChunk();
        int distance = options.farBlockDistance;
        int view = player.getSendViewDistance();
        if (view <= 0) {
            view = player.getViewDistance();
        }
        int scanRadius = Math.max((distance + 15) / 16 + 1, view);
        double hideSq = (double) distance * distance;
        double restoreSq = hideSq;

        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int z = -scanRadius; z <= scanRadius; z++) {
                int chunkX = center.getX() + x;
                int chunkZ = center.getZ() + z;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                ChunkKey key = new ChunkKey(world.getUID(), chunkX, chunkZ);
                if (!cache.containsKey(key) && queued.add(key)) {
                    scanQueue.add(key);
                }
            }
        }

        Set<BlockPosition> hiddenPositions = hidden.computeIfAbsent(player.getUniqueId(), key -> new HashSet<>());
        Map<Position, BlockData> restore = new HashMap<>();
        Iterator<BlockPosition> iterator = hiddenPositions.iterator();
        while (iterator.hasNext() && restore.size() < SEND_BATCH) {
            BlockPosition position = iterator.next();
            if (distanceSq(player, position) > restoreSq) {
                continue;
            }
            iterator.remove();
            if (!world.isChunkLoaded(position.blockX() >> 4, position.blockZ() >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(position.blockX(), position.blockY(), position.blockZ());
            restore.put(position, block.getBlockData());
        }

        Map<Position, BlockData> hide = new HashMap<>();
        hopper:
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int z = -scanRadius; z <= scanRadius; z++) {
                int chunkX = center.getX() + x;
                int chunkZ = center.getZ() + z;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                if (cache.containsKey(new ChunkKey(world.getUID(), chunkX, chunkZ))) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof Hopper)) {
                        continue;
                    }
                    if (hide.size() >= SEND_BATCH) {
                        break hopper;
                    }
                    BlockPosition position = Position.block(state.getX(), state.getY(), state.getZ());
                    if (distanceSq(player, position) <= hideSq) {
                        continue;
                    }
                    if (hiddenPositions.add(position)) {
                        hide.put(position, AIR);
                    }
                }
            }
        }

        outer:
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int z = -scanRadius; z <= scanRadius; z++) {
                ChunkKey key = new ChunkKey(world.getUID(), center.getX() + x, center.getZ() + z);
                Set<BlockPosition> positions = cache.get(key);
                if (positions == null) {
                    continue;
                }
                for (BlockPosition position : positions) {
                    if (hide.size() >= SEND_BATCH) {
                        break outer;
                    }
                    if (distanceSq(player, position) <= hideSq) {
                        continue;
                    }
                    if (hiddenPositions.add(position)) {
                        hide.put(position, AIR);
                    }
                }
            }
        }

        send(player, restore);
        send(player, hide);
    }

    private void restorePlayer(Player player) {
        Set<BlockPosition> positions = hidden.remove(player.getUniqueId());
        if (positions == null || positions.isEmpty()) {
            return;
        }
        World world = player.getWorld();
        Map<Position, BlockData> restore = new HashMap<>();
        for (BlockPosition position : positions) {
            if (restore.size() >= SEND_BATCH) {
                break;
            }
            if (!world.isChunkLoaded(position.blockX() >> 4, position.blockZ() >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(position.blockX(), position.blockY(), position.blockZ());
            restore.put(position, block.getBlockData());
        }
        send(player, restore);
    }

    private void restoreAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restorePlayer(player);
        }
    }

    private void send(Player player, Map<Position, BlockData> changes) {
        if (changes.isEmpty()) {
            return;
        }
        try {
            Channel channel = Channels.of(player);
            PacketTrust.mark(player);
            try {
                player.sendMultiBlockChange(changes);
            } finally {
                PacketTrust.releaseSoon(player, channel);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not update hidden blocks for " + player.getName() + ": " + throwable.getMessage());
        }
    }

    private void scan(ChunkKey key) {
        World world = Bukkit.getWorld(key.world());
        if (world == null || !world.isChunkLoaded(key.x(), key.z())) {
            return;
        }
        try {
            Chunk chunk = world.getChunkAt(key.x(), key.z());
            Object[] sections = (Object[]) chunkSections.invoke(chunkHandle.invoke(chunk, fullStatus));
            int minY = world.getMinHeight();
            Set<BlockPosition> found = new HashSet<>();
            Predicate<Object> test = state -> {
                try {
                    return hideable((Material) stateMaterial.invoke(state));
                } catch (Throwable ignored) {
                    return false;
                }
            };
            for (int index = 0; index < sections.length; index++) {
                Object section = sections[index];
                if (section == null || !(Boolean) sectionMaybe.invoke(section, test)) {
                    continue;
                }
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            Object state = sectionState.invoke(section, x, y, z);
                            Material material = (Material) stateMaterial.invoke(state);
                            if (hideable(material)) {
                                found.add(Position.block(key.x() * 16 + x, minY + index * 16 + y, key.z() * 16 + z));
                            }
                        }
                    }
                }
            }
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Hopper) {
                    found.add(Position.block(state.getX(), state.getY(), state.getZ()));
                }
            }
            cache.put(key, found);
        } catch (Throwable throwable) {
            if (warned.compareAndSet(false, true)) {
                plugin.getLogger().log(Level.WARNING, "Could not scan a chunk for far block hiding", throwable);
            }
        }
    }

    private boolean hideable(Material material) {
        return material == Material.HOPPER || options.isRedstonePart(material);
    }

    private double distanceSq(Player player, BlockPosition position) {
        double dx = position.blockX() + 0.5D - player.getX();
        double dy = position.blockY() + 0.5D - player.getY();
        double dz = position.blockZ() + 0.5D - player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (hideable(block.getType())) {
            add(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        remove(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeAll(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeAll(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (cache.containsKey(key) && queued.add(key)) {
            scanQueue.add(key);
        }
        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        Bukkit.getScheduler().runTaskLater(plugin, () -> rehideIn(world, chunkX, chunkZ), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        queued.remove(key);
        scanQueue.remove(key);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        UUID worldId = event.getWorld().getUID();
        cache.keySet().removeIf(key -> key.world().equals(worldId));
        queued.removeIf(key -> key.world().equals(worldId));
        scanQueue.removeIf(key -> key.world().equals(worldId));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        hidden.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        hidden.remove(event.getPlayer().getUniqueId());
    }

    private void add(Block block) {
        Set<BlockPosition> positions = cache.get(new ChunkKey(block.getWorld().getUID(), block.getChunk().getX(), block.getChunk().getZ()));
        if (positions != null) {
            positions.add(Position.block(block.getX(), block.getY(), block.getZ()));
        }
    }

    private void remove(Block block) {
        Set<BlockPosition> positions = cache.get(new ChunkKey(block.getWorld().getUID(), block.getChunk().getX(), block.getChunk().getZ()));
        if (positions != null) {
            positions.remove(Position.block(block.getX(), block.getY(), block.getZ()));
        }
    }

    private void removeAll(List<Block> blocks) {
        for (Block block : blocks) {
            if (hideable(block.getType())) {
                remove(block);
            }
        }
    }

    private void rehideIn(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(world)) {
                continue;
            }
            Set<BlockPosition> positions = hidden.get(player.getUniqueId());
            if (positions == null || positions.isEmpty()) {
                continue;
            }
            Map<Position, BlockData> air = new HashMap<>();
            for (BlockPosition position : positions) {
                if (air.size() >= SEND_BATCH) {
                    break;
                }
                if (position.blockX() >> 4 == chunkX && position.blockZ() >> 4 == chunkZ) {
                    air.put(position, AIR);
                }
            }
            send(player, air);
        }
    }

    private record ChunkKey(UUID world, int x, int z) {
    }
}
