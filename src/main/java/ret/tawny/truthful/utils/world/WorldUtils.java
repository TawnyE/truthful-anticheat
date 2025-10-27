package ret.tawny.truthful.utils.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import ret.tawny.truthful.utils.SafeLocation;
import java.util.List;

public final class WorldUtils {
    private WorldUtils() {}

    private static final SafeLocation[] OFFSETS = {
            new SafeLocation(null, 0, 0, 1), new SafeLocation(null, 0, 0, -1),
            new SafeLocation(null, 1, 0, 0), new SafeLocation(null, -1, 0, 0),
            new SafeLocation(null, 1, 0, 1), new SafeLocation(null, 1, 0, -1),
            new SafeLocation(null, -1, 0, 1), new SafeLocation(null, -1, 0, -1)
    };

    public static boolean safeGround(final Player player) {
        final Location loc = player.getLocation();
        if (isSolid(loc.clone().add(0, -0.1, 0).getBlock())) {
            return true;
        }
        for (final SafeLocation offset : OFFSETS) {
            if (isSolid(loc.getWorld().getBlockAt(loc.clone().add(offset.getX(), -0.1, offset.getZ())))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSolid(final Block block) {
        return block.getType().isSolid();
    }

    public static boolean ground(final Block block) {
        return isSolid(block);
    }

    public static BlockFace getBlockFace(final Player player) {
        List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, 100);
        if (lastTwoTargetBlocks.size() < 2) {
            return BlockFace.SELF;
        }
        return lastTwoTargetBlocks.get(1).getFace(lastTwoTargetBlocks.get(0));
    }

    public static float getSlippinessMultiplier(Player player) {
        Block block = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        switch (block.getType().name()) {
            case "ICE":
            case "PACKED_ICE":
            case "FROSTED_ICE":
                return 0.98F;
            case "SLIME_BLOCK":
                return 0.8F;
            default:
                return 0.6F;
        }
    }

    /**
     * Gets the total number of ticks the world has been running.
     * @param world The world to get the time from.
     * @return The full time of the world in ticks.
     */
    public static int getWorldTicks(final World world) {
        return (int) world.getFullTime();
    }

    public static boolean nearBlock(final Player player) {
        Location base = player.getLocation();
        for (double x = -1.0; x <= 1.0; ++x) {
            for (double y = -1.0; y <= 1.0; ++y) {
                for (double z = -1.0; z <= 1.0; ++z) {
                    if (isSolid(base.clone().add(x, y, z).getBlock())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isLiquid(final Player player) {
        return player.getLocation().getBlock().isLiquid() || player.getEyeLocation().getBlock().isLiquid();
    }

    public static boolean hasLowFrictionBelow(final Player player) {
        final Location base = player.getLocation();
        for (int y = -1; y <= 0; ++y) {
            final Block block = player.getWorld().getBlockAt(base.clone().add(0, y, 0));
            if (BlockUtils.isLowFriction(block.getType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasClimbableNearby(final Player player) {
        final Location base = player.getLocation();
        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    final Block block = player.getWorld().getBlockAt(base.clone().add(x, y, z));
                    if (BlockUtils.isClimbable(block.getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}