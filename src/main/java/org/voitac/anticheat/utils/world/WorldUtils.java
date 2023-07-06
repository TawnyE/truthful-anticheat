package org.voitac.anticheat.utils.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.voitac.anticheat.utils.SafeLocation;

import java.util.HashSet;
import java.util.List;

public final class WorldUtils {
    private WorldUtils(){}

    /**
     * Dumb, going to change this to how I do it in my client in the future
     */
    public static final SafeLocation[] OFFSETS = {
            new SafeLocation(null, 0, 0, 1), //north
            new SafeLocation(null, 0, 0, -1), //east
            new SafeLocation(null, 1, 0, 0), //south
            new SafeLocation(null, -1, 0, 0), //west

            new SafeLocation(null, 0, 0, 0), //north corner
            new SafeLocation(null, 1, 0, -1), //east corner
            new SafeLocation(null, 1, 0, 1), //south corner
            new SafeLocation(null, -1, 0, 1) //west corner
    };

    /**
     *
     * @return Broad search range if any block within 1 block from the player is valid ground, should only be used if you know the player is on an edge
     */
    public static boolean safeGround(final Player player) {
        final SafeLocation location = new SafeLocation(player.getLocation()).add(new SafeLocation(null, 0, -0.1, 0));

        if(ground(player.getWorld().getBlockAt(location)))
            return true;

        for(final SafeLocation safeLocation : OFFSETS)
            if (ground(player.getWorld().getBlockAt(location.add(safeLocation))))
                return true;

        return false;
    }

    /**
     *
     * @return Block Solidity
     */
    public static boolean ground(final Block block) {
        return ground(block.getType());
    }

    public static boolean ground(final Player player, final double y) {
        final Material material = player.getWorld().getBlockAt(player.getLocation().add(0, y, 0)).getType();

        final Location location = new Location(player.getWorld(), player.getLocation().getX(), player.getLocation().getY() - y, player.getLocation().getZ());

        if(location.getBlock().isLiquid())
            return false;

        return ground(material);
    }

    /**
     *
     * @return Predicted block facing for player
     */
    public static BlockFace getBlockFace(final Player player) {
        final List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks((HashSet<Byte>) null, 100);
        return lastTwoTargetBlocks.get(1).getFace(lastTwoTargetBlocks.get(0));
    }

    public static boolean nearBlock(final Player player) {
        final SafeLocation location = new SafeLocation(player.getLocation()).add(new SafeLocation(null, 0, -0.1, 0));
        if(ground(player.getWorld().getBlockAt(location).getType()))
            return true;

        for(final SafeLocation safeLocation : OFFSETS) {
            for(int i = -1; i < 0; ++i) {
                if (ground(player.getWorld().getBlockAt(location.add(safeLocation))))
                    return true;
            }
        }
        return false;
    }
    public static boolean nearBlockFlat(final Player player) {
        for(int i = -1; i < 1; ++i) {
            for(int j = -1; j < 1; ++j) {
                final boolean water = player.getWorld().getBlockAt(player.getLocation().add(i, -0.1, j)).isLiquid();
                if(water)
                    continue;
                final Material material = player.getWorld().getBlockAt(player.getLocation().add(i, -0.1, j)).getType();
                if(ground(material)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean ground(final Material material) {
        return (material != Material.AIR && material != Material.LAVA && material != Material.WATER);
    }

    // TODO
    public static float getSlippinessMultiplier(final double x, final double y, final double z, final Player player) {
        //return player.getWorld().getBlockAt(player.getLocation().add(x, y, z)).
        return 1;
    }

    public static int getWorldTicks(final World world) {
        return (int) world.getFullTime();
    }
}
