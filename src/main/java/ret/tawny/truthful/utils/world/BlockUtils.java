package ret.tawny.truthful.utils.world;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class BlockUtils {
    private BlockUtils() {}

    private static final Set<Material> WHOLE_BLOCKS = EnumSet.noneOf(Material.class);

    static {
        for (final Material material : Material.values()) {
            if (!material.isBlock()) {
                continue;
            }
            try {
                if (material.isOccluding()) {
                    WHOLE_BLOCKS.add(material);
                }
            } catch (NoSuchMethodError e) {
                if (material.isSolid()) {
                    WHOLE_BLOCKS.add(material);
                }
            }
        }
    }

    public static Block getRelativeBlock(final Block anchor, final BlockFace face) {
        return face == null ? anchor : anchor.getRelative(face);
    }

    public static Block getFirstBlockInSight(final Player player, final double length) {
        BlockIterator iterator = new BlockIterator(player.getEyeLocation(), 0, (int) Math.ceil(length));
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (block.getType().isSolid()) {
                return block;
            }
        }
        return null;
    }

    public static boolean isWholeBlock(final Material material) {
        return WHOLE_BLOCKS.contains(material);
    }

    public static boolean isAbnormal(final Material material) {
        return !isWholeBlock(material);
    }

    public static boolean isLowFriction(final Material material) {
        final String name = material.name().toUpperCase(Locale.ROOT);
        return name.contains("ICE") || name.contains("SLIME_BLOCK");
    }

    public static boolean isClimbable(final Material material) {
        try {
            if (Tag.CLIMBABLE.isTagged(material)) {
                return true;
            }
        } catch (NoClassDefFoundError | NoSuchMethodError ignored) {
            final String name = material.name();
            return name.equals("LADDER") || name.equals("VINE") || name.equals("SCAFFOLDING");
        }
        return false;
    }
}