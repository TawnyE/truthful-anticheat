package ret.tawny.truthful.utils.world;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockPropertyRegistry {

    private static final Map<String, BlockProperties> PROPERTIES = new ConcurrentHashMap<>();
    private static final BlockProperties DEFAULT = new BlockProperties(0.6f, false, false, false, false, false);

    public static BlockProperties get(WrappedBlockState state) {
        if (state == null) return DEFAULT;
        String name = state.getType().getName().toUpperCase();
        return PROPERTIES.computeIfAbsent(name, BlockPropertyRegistry::compute);
    }

    private static BlockProperties compute(String name) {
        float friction = 0.6f;
        if (name.contains("BLUE_ICE")) friction = 0.989f;
        else if (name.contains("ICE")) friction = 0.98f;
        else if (name.contains("SLIME_BLOCK")) friction = 0.8f;
        else if (name.contains("HONEY_BLOCK") || name.contains("SOUL_SAND") || name.contains("SOUL_SOIL")) friction = 0.4f;

        boolean solid = !name.contains("AIR") && !name.contains("WATER") && !name.contains("LAVA") && !name.contains("BUBBLE_COLUMN");
        if (name.contains("STAIR") || name.contains("SLAB") || name.contains("CAULDRON") ||
            name.contains("HOPPER") || name.contains("ANVIL") || name.contains("SCAFFOLDING")) solid = true;

        boolean liquid = name.contains("WATER") || name.contains("LAVA") || name.contains("BUBBLE_COLUMN");
        boolean climbable = name.contains("LADDER") || name.contains("VINE") || name.contains("SCAFFOLDING") ||
                           name.contains("TWISTING_VINES") || name.contains("WEEPING_VINES") || name.contains("GLOW_BERRIES");
        boolean ice = name.contains("ICE");
        boolean thin = name.contains("CARPET") || name.contains("SNOW") || name.contains("PLATE") || name.contains("LILY_PAD");

        return new BlockProperties(friction, solid, liquid, climbable, ice, thin);
    }

    public static float getFriction(WrappedBlockState state) {
        return get(state).friction;
    }

    public static boolean isSolid(WrappedBlockState state) {
        return get(state).solid;
    }

    public static boolean isLiquid(WrappedBlockState state) {
        return get(state).liquid;
    }

    public static boolean isClimbable(WrappedBlockState state) {
        return get(state).climbable;
    }

    public static boolean isIce(WrappedBlockState state) {
        return get(state).ice;
    }

    public static boolean isThin(WrappedBlockState state) {
        return get(state).thin;
    }

    public static class BlockProperties {
        public final float friction;
        public final boolean solid;
        public final boolean liquid;
        public final boolean climbable;
        public final boolean ice;
        public final boolean thin;

        public BlockProperties(float friction, boolean solid, boolean liquid, boolean climbable, boolean ice, boolean thin) {
            this.friction = friction;
            this.solid = solid;
            this.liquid = liquid;
            this.climbable = climbable;
            this.ice = ice;
            this.thin = thin;
        }
    }
}
