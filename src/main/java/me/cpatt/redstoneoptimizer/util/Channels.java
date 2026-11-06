package me.cpatt.redstoneoptimizer.util;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;

public final class Channels {

    private Channels() {
    }

    public static Channel of(Player player) throws Exception {
        Object handle = Reflection.method(player.getClass(), "getHandle").invoke(player);
        Object listener = Reflection.field(handle.getClass(), "connection").get(handle);
        Object connection = Reflection.field(listener.getClass(), "connection").get(listener);
        return (Channel) Reflection.field(connection.getClass(), "channel").get(connection);
    }
}
