package org.voitac.anticheat.utils.world;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;

import java.util.ArrayList;
import java.util.List;

public final class BlockUtils {
    private BlockUtils() {}

    // I was unable to find a method in bukkit that allows you to easily check a blocks bounds so fuck it
    private static final List<Material> NORMAL_BOUNDS = new ArrayList<>();

    /**
     *
     * @return Iterates through each block in line of site and distance until a block that meets our qualifications are found
     * If no block is found we return null
     */
    public static Block getFirstBlockInSight(final Player player, final double length, final Material...materials) {
        BlockIterator bit = new BlockIterator(player.getLocation(), length);
        final Block blockOut;

        iterator: {
            // Iterates through each Block in until the block meets our specifications
            // If no blocks meet we will return null
            while (bit.hasNext()) {
                final Block block = bit.next();
                final Material mat = block.getType();
                for (final Material material : materials)
                    if (mat.equals(material))
                        continue;

                blockOut = bit.next();
                break iterator;
            }
            blockOut = null;
        }
        return blockOut;
    }

    public static boolean isWholeBlock(final Block block) {
        return NORMAL_BOUNDS.contains(block.getType());
    }

    public static boolean isAbnormal(final Block block) {
        return !NORMAL_BOUNDS.contains(block.getType());
    }

    static {
        NORMAL_BOUNDS.add(Material.BEACON);
        NORMAL_BOUNDS.add(Material.COBBLESTONE);
        NORMAL_BOUNDS.add(Material.GLOWSTONE);
        NORMAL_BOUNDS.add(Material.FURNACE);
        NORMAL_BOUNDS.add(Material.SANDSTONE);
        NORMAL_BOUNDS.add(Material.SAND);
        NORMAL_BOUNDS.add(Material.SPONGE);
        NORMAL_BOUNDS.add(Material.ENDER_STONE);
        NORMAL_BOUNDS.add(Material.GOLD_ORE);
        NORMAL_BOUNDS.add(Material.DIAMOND_ORE);
        NORMAL_BOUNDS.add(Material.COAL_ORE);
        NORMAL_BOUNDS.add(Material.IRON_ORE);
        NORMAL_BOUNDS.add(Material.LAPIS_ORE);
        NORMAL_BOUNDS.add(Material.EMERALD_ORE);
        NORMAL_BOUNDS.add(Material.REDSTONE_ORE);
        NORMAL_BOUNDS.add(Material.PACKED_ICE);
        NORMAL_BOUNDS.add(Material.PUMPKIN);
        NORMAL_BOUNDS.add(Material.QUARTZ_ORE);
        NORMAL_BOUNDS.add(Material.MOSSY_COBBLESTONE);
        NORMAL_BOUNDS.add(Material.WOOD);
        NORMAL_BOUNDS.add(Material.RED_SANDSTONE);
        NORMAL_BOUNDS.add(Material.BEDROCK);
        NORMAL_BOUNDS.add(Material.CLAY);
        NORMAL_BOUNDS.add(Material.GLOWING_REDSTONE_ORE);
        NORMAL_BOUNDS.add(Material.BOOKSHELF);
        NORMAL_BOUNDS.add(Material.STAINED_GLASS);
        NORMAL_BOUNDS.add(Material.BRICK);
        NORMAL_BOUNDS.add(Material.COMMAND);
        NORMAL_BOUNDS.add(Material.DIRT);
        NORMAL_BOUNDS.add(Material.DROPPER);
        NORMAL_BOUNDS.add(Material.EMERALD_BLOCK);
        NORMAL_BOUNDS.add(Material.GOLD_BLOCK);
        NORMAL_BOUNDS.add(Material.GRAVEL);
        NORMAL_BOUNDS.add(Material.IRON_BLOCK);
        NORMAL_BOUNDS.add(Material.LAPIS_BLOCK);
        NORMAL_BOUNDS.add(Material.HARD_CLAY);
        NORMAL_BOUNDS.add(Material.HAY_BLOCK);
        NORMAL_BOUNDS.add(Material.LEAVES);
        NORMAL_BOUNDS.add(Material.LEAVES_2);
        NORMAL_BOUNDS.add(Material.MELON_BLOCK);
        NORMAL_BOUNDS.add(Material.MOB_SPAWNER);
        NORMAL_BOUNDS.add(Material.MYCEL);
        NORMAL_BOUNDS.add(Material.NETHER_BRICK);
        NORMAL_BOUNDS.add(Material.NETHERRACK);
        NORMAL_BOUNDS.add(Material.OBSIDIAN);
        NORMAL_BOUNDS.add(Material.QUARTZ_BLOCK);
        NORMAL_BOUNDS.add(Material.REDSTONE_BLOCK);
        NORMAL_BOUNDS.add(Material.REDSTONE_LAMP_OFF);
        NORMAL_BOUNDS.add(Material.REDSTONE_LAMP_ON);
        NORMAL_BOUNDS.add(Material.SLIME_BLOCK);
        NORMAL_BOUNDS.add(Material.SNOW_BLOCK);
        NORMAL_BOUNDS.add(Material.SOUL_SAND);
        NORMAL_BOUNDS.add(Material.STAINED_CLAY);
        NORMAL_BOUNDS.add(Material.WORKBENCH);
        NORMAL_BOUNDS.add(Material.STONE);
        NORMAL_BOUNDS.add(Material.GLASS);
        NORMAL_BOUNDS.add(Material.COAL_BLOCK);
    }
}
