package me.cpatt.redstoneoptimizer.redstone;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class StaleBlocks {

    private final int limit;
    private final ConcurrentHashMap<Long, Boolean> blocks = new ConcurrentHashMap<>();

    StaleBlocks(int limit) {
        this.limit = limit;
    }

    void remember(int x, int y, int z) {
        if (blocks.size() >= limit) {
            blocks.clear();
        }
        blocks.put(key(x, y, z), Boolean.TRUE);
    }

    void forget(long key) {
        blocks.remove(key);
    }

    void forgetAll() {
        blocks.clear();
    }

    int count() {
        return blocks.size();
    }

    Set<Long> positions() {
        return blocks.keySet();
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (y & 0xFFF) << 26)
                | (z & 0x3FFFFFF);
    }

    static int xOf(long key) {
        int value = (int) ((key >>> 38) & 0x3FFFFFF);
        return value >= 0x2000000 ? value - 0x4000000 : value;
    }

    static int yOf(long key) {
        int value = (int) ((key >>> 26) & 0xFFF);
        return value >= 0x800 ? value - 0x1000 : value;
    }

    static int zOf(long key) {
        int value = (int) (key & 0x3FFFFFF);
        return value >= 0x2000000 ? value - 0x4000000 : value;
    }
}
