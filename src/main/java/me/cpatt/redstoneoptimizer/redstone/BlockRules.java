package me.cpatt.redstoneoptimizer.redstone;

import org.bukkit.Material;

interface BlockRules {

    boolean redstone(Material material);

    boolean far(int x, int y, int z);

    boolean insideSection(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
}
