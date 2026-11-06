package me.cpatt.redstoneoptimizer.hoppers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.cpatt.redstoneoptimizer.Options;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class HopperGuard implements Listener {

    private static final String BYPASS = "redstoneoptimizer.bypass.hopper";

    private final Plugin plugin;
    private final Options options;
    private final Map<ChunkKey, Integer> hopperCounts = new HashMap<>();

    public HopperGuard(Plugin plugin, Options options) {
        this.plugin = plugin;
        this.options = options;
    }

    public void start() {
        if (!options.hoppersOn) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        hopperCounts.clear();
        HandlerList.unregisterAll(this);
    }

    public int countNear(Player player) {
        World world = player.getWorld();
        Chunk center = player.getChunk();
        int radius = options.hopperChunks;
        int total = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int chunkX = center.getX() + x;
                int chunkZ = center.getZ() + z;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                total += countInChunk(world, chunkX, chunkZ);
                if (total >= options.hopperCap) {
                    return total;
                }
            }
        }
        return total;
    }

    public int cap() {
        return options.hopperCap;
    }

    public int radius() {
        return options.hopperChunks;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void checkPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HOPPER) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS)) {
            return;
        }
        int count = countNear(player);
        if (count < options.hopperCap) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(Component.text("Hopper limit reached in this area: "
                + count + "/" + options.hopperCap
                + " in a " + options.hopperChunks + " chunk radius.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void countPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HOPPER) {
            return;
        }
        Block block = event.getBlockPlaced();
        adjust(block.getWorld(), block.getChunk().getX(), block.getChunk().getZ(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void countBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.HOPPER) {
            return;
        }
        Block block = event.getBlock();
        adjust(block.getWorld(), block.getChunk().getX(), block.getChunk().getZ(), -1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void countEntityExplosion(EntityExplodeEvent event) {
        countExploded(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void countBlockExplosion(BlockExplodeEvent event) {
        countExploded(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void countChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        hopperCounts.put(chunkKey(chunk.getWorld(), chunk.getX(), chunk.getZ()), countInChunk(chunk));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void countChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        hopperCounts.remove(chunkKey(chunk.getWorld(), chunk.getX(), chunk.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void countWorldUnload(WorldUnloadEvent event) {
        UUID worldId = event.getWorld().getUID();
        hopperCounts.keySet().removeIf(key -> key.world().equals(worldId));
    }

    private void countExploded(List<Block> blocks) {
        for (Block block : blocks) {
            if (block.getType() == Material.HOPPER) {
                adjust(block.getWorld(), block.getChunk().getX(), block.getChunk().getZ(), -1);
            }
        }
    }

    private int countInChunk(World world, int chunkX, int chunkZ) {
        ChunkKey key = chunkKey(world, chunkX, chunkZ);
        Integer cached = hopperCounts.get(key);
        if (cached != null) {
            return cached;
        }
        int count = countInChunk(world.getChunkAt(chunkX, chunkZ));
        hopperCounts.put(key, count);
        return count;
    }

    private int countInChunk(Chunk chunk) {
        int total = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Hopper) {
                total++;
            }
        }
        return total;
    }

    private void adjust(World world, int chunkX, int chunkZ, int delta) {
        ChunkKey key = chunkKey(world, chunkX, chunkZ);
        hopperCounts.compute(key, (ignored, value) -> Math.max(0, (value == null ? 0 : value) + delta));
    }

    private ChunkKey chunkKey(World world, int chunkX, int chunkZ) {
        return new ChunkKey(world.getUID(), chunkX, chunkZ);
    }

    private record ChunkKey(UUID world, int x, int z) {
    }
}
