package me.cpatt.redstoneoptimizer.util;

import io.netty.channel.Channel;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;

public final class PacketTrust {

    private static final Map<UUID, AtomicInteger> DEPTH = new ConcurrentHashMap<>();

    private PacketTrust() {
    }

    public static void mark(Player player) {
        DEPTH.computeIfAbsent(player.getUniqueId(), key -> new AtomicInteger()).incrementAndGet();
    }

    public static void releaseSoon(Player player, Channel channel) {
        UUID id = player.getUniqueId();
        try {
            channel.eventLoop().execute(() -> release(id));
        } catch (Throwable ignored) {
            release(id);
        }
    }

    public static boolean trusted(UUID id) {
        AtomicInteger depth = DEPTH.get(id);
        return depth != null && depth.get() > 0;
    }

    private static void release(UUID id) {
        DEPTH.computeIfPresent(id, (key, depth) -> depth.decrementAndGet() <= 0 ? null : depth);
    }
}
