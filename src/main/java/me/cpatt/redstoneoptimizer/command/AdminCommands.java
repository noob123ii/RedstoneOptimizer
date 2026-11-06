package me.cpatt.redstoneoptimizer.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.cpatt.redstoneoptimizer.Options;
import me.cpatt.redstoneoptimizer.RedstoneOptimizer;
import me.cpatt.redstoneoptimizer.hoppers.HopperGuard;
import me.cpatt.redstoneoptimizer.items.ItemPiles;
import me.cpatt.redstoneoptimizer.items.ItemTracking;
import me.cpatt.redstoneoptimizer.lag.LagWatch;
import me.cpatt.redstoneoptimizer.redstone.RedstonePackets;
import me.cpatt.redstoneoptimizer.render.FarBlocks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class AdminCommands implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "redstoneoptimizer.admin";

    private final RedstoneOptimizer plugin;

    public AdminCommands(RedstoneOptimizer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            showStatus(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            sender.sendMessage(Component.text("RedstoneOptimizer reloaded.", NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /" + label + " status", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Usage: /" + label + " reload", NamedTextColor.GRAY));
        return true;
    }

    private void showStatus(CommandSender sender) {
        Options options = plugin.options();
        LagWatch lagWatch = plugin.lagWatch();
        ItemTracking itemTracking = plugin.itemTracking();
        ItemPiles itemPiles = plugin.itemPiles();
        RedstonePackets redstone = plugin.redstonePackets();
        String tps = lagWatch == null ? "0.00" : String.format(Locale.ROOT, "%.2f", lagWatch.tps());
        String load = lagWatch == null || !options.lagWatchOn ? "off" : lagWatch.underPressure() ? "throttled" : "normal";

        sender.sendMessage(Component.text("RedstoneOptimizer", NamedTextColor.AQUA));
        sender.sendMessage(row("TPS", tps + " (" + load + ")"));
        if (itemTracking == null || !itemTracking.usable()) {
            sender.sendMessage(row("Items", options.itemsOn ? "unsupported on this build" : "off"));
        } else {
            sender.sendMessage(row("Items", options.itemsOn
                    ? options.itemChunks + " chunks, " + itemTracking.changed() + " range changes"
                    : "off"));
        }
        if (itemPiles == null || !options.mergeOn) {
            sender.sendMessage(row("Piles", "off"));
        } else {
            double range = lagWatch != null && lagWatch.underPressure() ? options.mergeRangeWhenLagging : options.mergeRange;
            sender.sendMessage(row("Piles", String.format(Locale.ROOT, "%.2f blocks, %d combined", range, itemPiles.combined())));
        }
        if (redstone == null || !options.redstoneOn) {
            sender.sendMessage(row("Redstone", "off"));
        } else {
            String redstoneRange = options.quietAll || options.redstoneChunks == 0 ? "all quiet" : options.redstoneChunks + " chunks";
            sender.sendMessage(row("Redstone", redstoneRange + ", "
                    + redstone.viewerCount() + " players, "
                    + redstone.heldCount() + " packets held, "
                    + redstone.staleCount() + " stale"));
        }
        HopperGuard hoppers = plugin.hopperGuard();
        if (hoppers == null || !options.hoppersOn) {
            sender.sendMessage(row("Hoppers", "off"));
        } else if (sender instanceof Player player) {
            sender.sendMessage(row("Hoppers", hoppers.countNear(player) + "/" + hoppers.cap()
                    + " in " + hoppers.radius() + " chunks"));
        } else {
            sender.sendMessage(row("Hoppers", hoppers.cap() + " per " + hoppers.radius() + " chunks"));
        }
        FarBlocks farBlocks = plugin.farBlocks();
        if (farBlocks == null || !options.farBlocksOn) {
            sender.sendMessage(row("Far blocks", "off"));
        } else if (sender instanceof Player player) {
            sender.sendMessage(row("Far blocks", farBlocks.hideChunks() + "/" + farBlocks.restoreChunks()
                    + " chunks, " + farBlocks.hiddenCount(player) + " hidden"));
        } else {
            sender.sendMessage(row("Far blocks", farBlocks.hideChunks() + " hide, " + farBlocks.restoreChunks() + " restore"));
        }
    }

    private Component row(String key, String value) {
        return Component.text(key + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN) || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if ("status".startsWith(prefix)) {
            options.add("status");
        }
        if ("reload".startsWith(prefix)) {
            options.add("reload");
        }
        return options;
    }
}
