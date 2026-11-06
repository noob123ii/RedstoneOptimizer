package me.cpatt.redstoneoptimizer.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import me.cpatt.redstoneoptimizer.Options;
import me.cpatt.redstoneoptimizer.lag.LagWatch;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ItemPiles {

    private static final int CELL = 3;
    private static final int NEIGHBOURS = 1;

    private final Plugin plugin;
    private final Options options;
    private final LagWatch lagWatch;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final AtomicLong combined = new AtomicLong();
    private BukkitTask task;

    public ItemPiles(Plugin plugin, Options options, LagWatch lagWatch) {
        this.plugin = plugin;
        this.options = options;
        this.lagWatch = lagWatch;
    }

    public void start() {
        if (!options.mergeOn) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::combinePass, 60L, options.mergeEvery);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public long combined() {
        return combined.get();
    }

    private void combinePass() {
        if (!options.mergeOn || !busy.compareAndSet(false, true)) {
            return;
        }
        try {
            int budget = options.mergeBudget;
            for (World world : Bukkit.getWorlds()) {
                budget = combineWorld(world, budget);
                if (budget <= 0) {
                    break;
                }
            }
        } finally {
            busy.set(false);
        }
    }

    private int combineWorld(World world, int budget) {
        List<Item> items = new ArrayList<>(world.getEntitiesByClass(Item.class));
        if (items.size() < 2) {
            return budget;
        }
        double range = lagWatch.underPressure() ? options.mergeRangeWhenLagging : options.mergeRange;
        double rangeSq = range * range;
        Map<PileCell, List<Item>> grid = new HashMap<>();
        for (Item item : items) {
            if (canCombine(item)) {
                grid.computeIfAbsent(cellOf(item), key -> new ArrayList<>()).add(item);
            }
        }
        for (List<Item> bucket : grid.values()) {
            for (Item first : bucket) {
                if (first.isDead()) {
                    continue;
                }
                PileCell origin = cellOf(first);
                for (int dx = -NEIGHBOURS; dx <= NEIGHBOURS; dx++) {
                    for (int dy = -NEIGHBOURS; dy <= NEIGHBOURS; dy++) {
                        for (int dz = -NEIGHBOURS; dz <= NEIGHBOURS; dz++) {
                            List<Item> neighbours = grid.get(new PileCell(origin.x() + dx, origin.y() + dy, origin.z() + dz));
                            if (neighbours == null) {
                                continue;
                            }
                            for (Item second : neighbours) {
                                if (first == second || second.isDead()) {
                                    continue;
                                }
                                if (first.getEntityId() >= second.getEntityId()) {
                                    continue;
                                }
                                if (!canCombine(second)) {
                                    continue;
                                }
                                double x = first.getX() - second.getX();
                                double y = first.getY() - second.getY();
                                double z = first.getZ() - second.getZ();
                                if (x * x + y * y + z * z > rangeSq) {
                                    continue;
                                }
                                if (combine(first, second)) {
                                    combined.incrementAndGet();
                                    budget--;
                                    if (budget <= 0) {
                                        return 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return budget;
    }

    private boolean canCombine(Item item) {
        if (!item.isValid() || item.isDead() || !item.isOnGround()) {
            return false;
        }
        if (item.getPickupDelay() != 0) {
            return false;
        }
        if (item.getVelocity().lengthSquared() > 0.01D) {
            return false;
        }
        ItemStack stack = item.getItemStack();
        return !stack.getType().isAir() && stack.getMaxStackSize() > 1;
    }

    private boolean combine(Item first, Item second) {
        if (!first.canMobPickup() || !second.canMobPickup()) {
            return false;
        }
        if (!Objects.equals(first.getOwner(), second.getOwner())) {
            return false;
        }
        ItemStack firstStack = first.getItemStack();
        ItemStack secondStack = second.getItemStack();
        if (!firstStack.isSimilar(secondStack)) {
            return false;
        }
        int room = firstStack.getMaxStackSize() - firstStack.getAmount();
        if (room <= 0) {
            return false;
        }
        int moved = Math.min(room, secondStack.getAmount());
        firstStack.setAmount(firstStack.getAmount() + moved);
        first.setItemStack(firstStack);
        if (moved >= secondStack.getAmount()) {
            if (!first.willAge() || !second.willAge()) {
                first.setUnlimitedLifetime(true);
            }
            first.setTicksLived(Math.max(first.getTicksLived(), second.getTicksLived()));
            second.remove();
        } else {
            secondStack.setAmount(secondStack.getAmount() - moved);
            second.setItemStack(secondStack);
        }
        return true;
    }

    private PileCell cellOf(Item item) {
        return new PileCell(
                Math.floorDiv((int) Math.floor(item.getX()), CELL),
                Math.floorDiv((int) Math.floor(item.getY()), CELL),
                Math.floorDiv((int) Math.floor(item.getZ()), CELL));
    }

    private record PileCell(int x, int y, int z) {
    }
}
