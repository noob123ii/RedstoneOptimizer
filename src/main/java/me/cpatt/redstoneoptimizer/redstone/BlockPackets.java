package me.cpatt.redstoneoptimizer.redstone;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.cpatt.redstoneoptimizer.util.Reflection;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

final class BlockPackets {

    private final Logger logger;
    private final boolean usable;
    private final String blockUpdate;
    private final String sectionUpdate;
    private final String blockEvent;
    private final Method updatePos;
    private final Method updateState;
    private final Method stateMaterial;
    private final Method posX;
    private final Method posY;
    private final Method posZ;
    private final Method runUpdates;
    private final Method relativePos;
    private final Field sectionPos;
    private final Method sectionMinX;
    private final Method sectionMinY;
    private final Method sectionMinZ;
    private final Method sectionMaxX;
    private final Method sectionMaxY;
    private final Method sectionMaxZ;
    private final Constructor<?> sectionCtor;
    private final Constructor<?> mapCtor;
    private final Method mapPut;
    private final Method eventPos;
    private final Method eventBlock;
    private final Method blockState;
    private final AtomicBoolean complained = new AtomicBoolean();
    private volatile boolean sendPistonEvents = true;

    BlockPackets(Plugin plugin) {
        this.logger = plugin.getLogger();
        boolean ready = false;
        String updateName = "";
        String multiName = "";
        String eventName = "";
        Method blockPos = null;
        Method blockStateMethod = null;
        Method material = null;
        Method x = null;
        Method y = null;
        Method z = null;
        Method updates = null;
        Method relative = null;
        Field section = null;
        Method minX = null;
        Method minY = null;
        Method minZ = null;
        Method maxX = null;
        Method maxY = null;
        Method maxZ = null;
        Constructor<?> sectionConstructor = null;
        Constructor<?> mapConstructor = null;
        Method put = null;
        Method eventPosition = null;
        Method eventBlockMethod = null;
        Method defaultState = null;
        try {
            Class<?> update = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket");
            Class<?> multi = Class.forName("net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket");
            Class<?> event = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockEventPacket");
            Class<?> state = Class.forName("net.minecraft.world.level.block.state.BlockState");
            Class<?> block = Class.forName("net.minecraft.world.level.block.Block");
            Class<?> position = Class.forName("net.minecraft.core.BlockPos");
            Class<?> sectionPosition = Class.forName("net.minecraft.core.SectionPos");
            Class<?> shortMap = Class.forName("it.unimi.dsi.fastutil.shorts.Short2ObjectMap");
            Class<?> shortMapImpl = Class.forName("it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap");

            updateName = update.getName();
            multiName = multi.getName();
            eventName = event.getName();
            blockPos = Reflection.method(update, "getPos");
            blockStateMethod = Reflection.method(update, "getBlockState");
            material = Reflection.method(state, "getBukkitMaterial");
            x = Reflection.method(position, "getX");
            y = Reflection.method(position, "getY");
            z = Reflection.method(position, "getZ");
            updates = Reflection.method(multi, "runUpdates", BiConsumer.class);
            relative = Reflection.method(sectionPosition, "sectionRelativePos", position);
            section = Reflection.field(multi, "sectionPos");
            minX = Reflection.method(sectionPosition, "minBlockX");
            minY = Reflection.method(sectionPosition, "minBlockY");
            minZ = Reflection.method(sectionPosition, "minBlockZ");
            maxX = Reflection.method(sectionPosition, "maxBlockX");
            maxY = Reflection.method(sectionPosition, "maxBlockY");
            maxZ = Reflection.method(sectionPosition, "maxBlockZ");
            sectionConstructor = multi.getConstructor(sectionPosition, shortMap);
            mapConstructor = shortMapImpl.getConstructor();
            put = Reflection.method(shortMapImpl, "put", short.class, Object.class);
            eventPosition = Reflection.method(event, "getPos");
            eventBlockMethod = Reflection.method(event, "getBlock");
            defaultState = Reflection.method(block, "defaultBlockState");
            ready = true;
        } catch (Throwable throwable) {
            ready = false;
            logger.log(Level.WARNING, "Block packet filtering is not available on this server build", throwable);
        }
        this.usable = ready;
        this.blockUpdate = updateName;
        this.sectionUpdate = multiName;
        this.blockEvent = eventName;
        this.updatePos = blockPos;
        this.updateState = blockStateMethod;
        this.stateMaterial = material;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.runUpdates = updates;
        this.relativePos = relative;
        this.sectionPos = section;
        this.sectionMinX = minX;
        this.sectionMinY = minY;
        this.sectionMinZ = minZ;
        this.sectionMaxX = maxX;
        this.sectionMaxY = maxY;
        this.sectionMaxZ = maxZ;
        this.sectionCtor = sectionConstructor;
        this.mapCtor = mapConstructor;
        this.mapPut = put;
        this.eventPos = eventPosition;
        this.eventBlock = eventBlockMethod;
        this.blockState = defaultState;
    }

    boolean usable() {
        return usable;
    }

    void pistonEvents(boolean value) {
        this.sendPistonEvents = value;
    }

    Object screen(Object message, BlockRules rules) {
        if (!usable) {
            return message;
        }
        String name = message.getClass().getName();
        try {
            if (name.equals(blockUpdate)) {
                return screenUpdate(message, rules);
            }
            if (name.equals(sectionUpdate)) {
                return screenSection(message, rules);
            }
            if (sendPistonEvents && name.equals(blockEvent)) {
                return screenEvent(message, rules);
            }
        } catch (Throwable throwable) {
            complain(throwable);
        }
        return message;
    }

    private Object screenUpdate(Object packet, BlockRules rules) throws Exception {
        Object state = updateState.invoke(packet);
        Material material = (Material) stateMaterial.invoke(state);
        if (!rules.redstone(material)) {
            return packet;
        }
        Object position = updatePos.invoke(packet);
        int x = (Integer) posX.invoke(position);
        int y = (Integer) posY.invoke(position);
        int z = (Integer) posZ.invoke(position);
        if (!rules.far(x, y, z)) {
            return packet;
        }
        rules.remember(x, y, z);
        return null;
    }

    private Object screenSection(Object packet, BlockRules rules) throws Exception {
        Object section = sectionPos.get(packet);
        int minX = (Integer) sectionMinX.invoke(section);
        int minY = (Integer) sectionMinY.invoke(section);
        int minZ = (Integer) sectionMinZ.invoke(section);
        int maxX = (Integer) sectionMaxX.invoke(section);
        int maxY = (Integer) sectionMaxY.invoke(section);
        int maxZ = (Integer) sectionMaxZ.invoke(section);
        if (rules.insideSection(minX, minY, minZ, maxX, maxY, maxZ)) {
            return packet;
        }

        List<BlockChange> changes = new ArrayList<>();
        boolean[] broken = new boolean[1];
        BiConsumer<Object, Object> consumer = (position, state) -> {
            try {
                Material material = (Material) stateMaterial.invoke(state);
                int x = (Integer) posX.invoke(position);
                int y = (Integer) posY.invoke(position);
                int z = (Integer) posZ.invoke(position);
                changes.add(new BlockChange(position, state, material, x, y, z));
            } catch (Throwable throwable) {
                broken[0] = true;
            }
        };
        runUpdates.invoke(packet, consumer);
        if (broken[0] || changes.isEmpty()) {
            return packet;
        }

        List<BlockChange> kept = new ArrayList<>(changes.size());
        List<BlockChange> hidden = null;
        for (BlockChange change : changes) {
            if (rules.redstone(change.material()) && rules.far(change.x(), change.y(), change.z())) {
                if (hidden == null) {
                    hidden = new ArrayList<>();
                }
                hidden.add(change);
            } else {
                kept.add(change);
            }
        }
        if (hidden == null) {
            return packet;
        }
        if (kept.isEmpty()) {
            for (BlockChange change : hidden) {
                rules.remember(change.x(), change.y(), change.z());
            }
            return null;
        }

        try {
            Object map = mapCtor.newInstance();
            for (BlockChange change : kept) {
                Object relative = relativePos.invoke(null, change.position());
                mapPut.invoke(map, relative, change.state());
            }
            Object replacement = sectionCtor.newInstance(section, map);
            for (BlockChange change : hidden) {
                rules.remember(change.x(), change.y(), change.z());
            }
            return replacement;
        } catch (Throwable throwable) {
            complain(throwable);
            return packet;
        }
    }

    private Object screenEvent(Object packet, BlockRules rules) throws Exception {
        Object block = eventBlock.invoke(packet);
        Object state = blockState.invoke(block);
        Material material = (Material) stateMaterial.invoke(state);
        if (!rules.redstone(material)) {
            return packet;
        }
        Object position = eventPos.invoke(packet);
        int x = (Integer) posX.invoke(position);
        int y = (Integer) posY.invoke(position);
        int z = (Integer) posZ.invoke(position);
        if (!rules.far(x, y, z)) {
            return packet;
        }
        return null;
    }

    private void complain(Throwable throwable) {
        if (complained.compareAndSet(false, true)) {
            logger.log(Level.WARNING, "A block packet could not be trimmed and was sent unchanged", throwable);
        }
    }

    private record BlockChange(Object position, Object state, Material material, int x, int y, int z) {
    }
}
