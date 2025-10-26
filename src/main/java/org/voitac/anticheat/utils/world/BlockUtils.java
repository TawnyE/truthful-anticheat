package org.voitac.anticheat.utils.world;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class BlockUtils {
    private BlockUtils() {}

    // I was unable to find a method in bukkit that allows you to easily check a blocks bounds so fuck it
    private static final Set<Material> NORMAL_BOUNDS = EnumSet.noneOf(Material.class);

    /**
     *
     * @return Iterates through each block in line of site and distance until a block that meets our qualifications are found
     * If no block is found we return null
     */
    public static Block getFirstBlockInSight(final Player player, final double length, final Material... materials) {
        final Vector direction = player.getEyeLocation().getDirection();
        final BlockIterator iterator = new BlockIterator(player.getWorld(), player.getEyeLocation().toVector(), direction, 0, (int) Math.ceil(length));

        final Set<Material> filter = materials == null || materials.length == 0
                ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(materials));

        while (iterator.hasNext()) {
            final Block block = iterator.next();
            final Material type = block.getType();

            if (type == Material.AIR)
                continue;

            if (!filter.isEmpty()) {
                if (filter.contains(type)) {
                    return block;
                }
                continue;
            }

            if (isWholeBlock(type) || type.isSolid()) {
                return block;
            }
        }

        return null;
    }

    public static boolean isWholeBlock(final Block block) {
        return isWholeBlock(block.getType());
    }

    public static boolean isAbnormal(final Block block) {
        return !isWholeBlock(block.getType());
    }

    public static boolean isWholeBlock(final Material material) {
        return NORMAL_BOUNDS.contains(material);
    }

    public static boolean isLowFriction(final Material material) {
        final String name = material.name().toUpperCase(Locale.ROOT);
        return name.contains("ICE") || name.contains("SLIME");
    }

    public static boolean isClimbable(final Material material) {
        try {
            if (Tag.CLIMBABLE.isTagged(material)) {
                return true;
            }
        } catch (final NoClassDefFoundError | NoSuchMethodError ignored) {
        }

        final String name = material.name();
        return name.equals("LADDER") || name.equals("VINE") || name.equals("SCAFFOLDING")
                || name.equals("TWISTING_VINES") || name.equals("TWISTING_VINES_PLANT")
                || name.equals("WEEPING_VINES") || name.equals("WEEPING_VINES_PLANT");
    }

    public static Block getRelativeBlock(final Block anchor, final BlockFace face) {
        return face == null ? anchor : anchor.getRelative(face);
    }

    public static Collection<Material> getNormalBounds() {
        return Collections.unmodifiableSet(NORMAL_BOUNDS);
    }

    static {
        for (final Material material : Material.values()) {
            boolean legacy = false;
            try {
                legacy = material.isLegacy();
            } catch (final NoSuchMethodError ignored) {
            }

            if (legacy || !material.isBlock()) {
                continue;
            }

            boolean occluding = false;
            try {
                occluding = material.isOccluding();
            } catch (final NoSuchMethodError ignored) {
                occluding = material.isSolid();
            }

            if (occluding) {
                NORMAL_BOUNDS.add(material);
            }
        }
    }
}
