package ret.tawny.truthful.utils.world;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.data.world.CompensatedWorld;

import java.util.EnumSet;
import java.util.Set;

public final class WorldUtils {
    private WorldUtils() {
    }

    private static final Set<Material> COMPLEX_BLOCKS = EnumSet.noneOf(Material.class);

    static {
        for (Material mat : Material.values()) {
            if (!mat.isBlock()) continue;
            String name = mat.name();
            if (name.contains("LECTERN") || name.contains("CHEST") || name.contains("ANVIL") ||
                    name.contains("HOPPER") || name.contains("CAULDRON") || name.contains("STONECUTTER") ||
                    name.contains("GRINDSTONE") || name.contains("BELL") || name.contains("CAMPFIRE") ||
                    name.contains("ENCHANTING_TABLE") || name.contains("DRAGON_EGG") ||
                    name.contains("PORTAL_FRAME") || name.contains("CACTUS") ||
                    name.contains("REPEATER") || name.contains("COMPARATOR") ||
                    name.contains("TRAPDOOR") || name.contains("LILY") ||
                    name.contains("SKULL") || name.contains("HEAD") || name.contains("PATH") ||
                    name.contains("STAIR") || name.contains("SLAB") || name.contains("SNOW") ||
                    name.contains("FENCE") || name.contains("WALL") || name.contains("PANE")) {
                COMPLEX_BLOCKS.add(mat);
            }
        }
    }

    public static boolean safeGround(final Location loc, final PlayerData data) {
        if (data == null) return true;
        return (loc == null) ? safeGround(data.getX(), data.getY(), data.getZ(), data)
                : safeGround(loc.getX(), loc.getY(), loc.getZ(), data);
    }

    public static boolean safeGround(double x, double y, double z, final PlayerData data) {
        if (data == null) return true;
        if (safeGround(x, y, z, data.getWorldCache())) return true;
        if (data.isNearVehicle() || data.isNearEntity()) return true;
        return false;
    }

    public static boolean isGroundBelow(final PlayerData data, double distance) {
        CompensatedWorld cache = data.getWorldCache();
        double x = data.getX();
        double y = data.getY();
        double z = data.getZ();

        for (double dy = 0.0; dy <= distance; dy += 0.2) {
            if (checkLayer(cache, x, y - dy, z, false)) return true;
        }
        return false;
    }

    public static boolean safeGround(final Location loc, final CompensatedWorld cache) {
        if (loc == null) return true;
        return safeGround(loc.getX(), loc.getY(), loc.getZ(), cache);
    }

    public static boolean safeGround(double x, double y, double z, final CompensatedWorld cache) {
        if (checkLayer(cache, x, y - 0.03, z, false)) return true;
        if (checkLayer(cache, x, y - 0.20, z, false)) return true;
        if (checkLayer(cache, x, y - 0.55, z, true)) return true;
        return false;
    }

    private static boolean checkLayer(CompensatedWorld cache, double x, double y, double z, boolean requireTall) {
        int floorY = (int) Math.floor(y);
        for (double ox = -0.3; ox <= 0.3; ox += 0.3) {
            for (double oz = -0.3; oz <= 0.3; oz += 0.3) {
                if (checkBlock(cache, x + ox, floorY, z + oz, requireTall)) return true;
            }
        }
        return false;
    }

    private static boolean checkBlock(CompensatedWorld cache, double x, int y, double z, boolean requireTall) {
        WrappedBlockState state = cache.getBlockState((int) Math.floor(x), y, (int) Math.floor(z));
        if (requireTall) {
            return isTall(state);
        }
        return BlockPropertyRegistry.isSolid(state);
    }

    private static boolean isTall(WrappedBlockState state) {
        if (state == null || state.getType().isAir()) return false;
        String name = state.getType().getName().toUpperCase();
        return name.contains("FENCE") || name.endsWith("WALL");
    }

    public static boolean isLiquid(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        return isLiquid(data.getLocation(), data);
    }

    public static boolean isLiquid(Location loc, PlayerData data) {
        if (data == null) return false;
        CompensatedWorld cache = data.getWorldCache();
        if (BlockPropertyRegistry.isLiquid(cache.getBlockState(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()))) return true;
        if (BlockPropertyRegistry.isLiquid(cache.getBlockState(loc.getBlockX(), (int) Math.floor(loc.getY() + 0.5), loc.getBlockZ()))) return true;
        return BlockPropertyRegistry.isLiquid(cache.getBlockState(loc.getBlockX(), (int) Math.floor(loc.getY() - 0.2), loc.getBlockZ()));
    }

    public static boolean isNearLiquid(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        Location loc = data.getLocation();
        CompensatedWorld cache = data.getWorldCache();
        int bX = loc.getBlockX();
        int bY = loc.getBlockY();
        int bZ = loc.getBlockZ();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (BlockPropertyRegistry.isLiquid(cache.getBlockState(bX + x, bY + y, bZ + z))) return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the player is near a bubble column block.
     * Bubble columns produce significantly higher vertical velocities than regular water
     * (up to 0.7 upward or -0.49 downward), so they need distinct handling.
     */
    public static boolean isNearBubbleColumn(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        Location loc = data.getLocation();
        CompensatedWorld cache = data.getWorldCache();
        int bX = loc.getBlockX();
        int bY = loc.getBlockY();
        int bZ = loc.getBlockZ();

        for (int x = -1; x <= 1; x++) {
            for (int y = -2; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    WrappedBlockState state = cache.getBlockState(bX + x, bY + y, bZ + z);
                    if (state != null && state.getType().getName().toUpperCase().contains("BUBBLE_COLUMN")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasClimbableNearby(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        CompensatedWorld cache = data.getWorldCache();
        int bX = (int) Math.floor(data.getX());
        int bY = (int) Math.floor(data.getY());
        int bZ = (int) Math.floor(data.getZ());
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (BlockPropertyRegistry.isClimbable(cache.getBlockState(bX + x, bY, bZ + z))) return true;
                if (BlockPropertyRegistry.isClimbable(cache.getBlockState(bX + x, bY + 1, bZ + z))) return true;
            }
        }
        return false;
    }

    public static boolean isScaffoldingNearby(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        CompensatedWorld cache = data.getWorldCache();
        int bX = (int) Math.floor(data.getX());
        int bY = (int) Math.floor(data.getY());
        int bZ = (int) Math.floor(data.getZ());
        if (cache.getBlockState(bX, bY, bZ).getType().getName().contains("SCAFFOLDING")) return true;
        if (cache.getBlockState(bX, bY - 1, bZ).getType().getName().contains("SCAFFOLDING")) return true;
        return false;
    }

    public static boolean isInWeb(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        CompensatedWorld cache = data.getWorldCache();
        int bX = (int) Math.floor(data.getX());
        int bY = (int) Math.floor(data.getY());
        int bZ = (int) Math.floor(data.getZ());
        if (cache.getBlockState(bX, bY, bZ).getType().getName().contains("COBWEB")) return true;
        if (cache.getBlockState(bX, bY + 1, bZ).getType().getName().contains("COBWEB")) return true;
        if (cache.getBlockState(bX, bY - 1, bZ).getType().getName().contains("COBWEB")) return true;
        return false;
    }

    /**
     * FIX: Edge Head-Hit Detection
     * Now checks the 4 corners of the player's 0.6x0.6 bounding box at head height.
     */
    public static boolean isHeadHitter(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        CompensatedWorld cache = data.getWorldCache();

        double x = data.getX();
        double y = data.getY() + 1.8; // Approximate Head Height
        double z = data.getZ();

        double w = 0.3; // Half-width of player bounding box
        int floorY = (int) Math.floor(y);

        if (BlockPropertyRegistry.isSolid(cache.getBlockState((int) Math.floor(x - w), floorY, (int) Math.floor(z - w)))) return true;
        if (BlockPropertyRegistry.isSolid(cache.getBlockState((int) Math.floor(x + w), floorY, (int) Math.floor(z - w)))) return true;
        if (BlockPropertyRegistry.isSolid(cache.getBlockState((int) Math.floor(x - w), floorY, (int) Math.floor(z + w)))) return true;
        if (BlockPropertyRegistry.isSolid(cache.getBlockState((int) Math.floor(x + w), floorY, (int) Math.floor(z + w)))) return true;

        return false;
    }

    public static boolean isNearIce(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        CompensatedWorld cache = data.getWorldCache();
        return BlockPropertyRegistry.isIce(cache.getBlockState((int) Math.floor(data.getX()), (int) Math.floor(data.getY() - 0.5), (int) Math.floor(data.getZ())));
    }

    /**
     * FIX: Wide ice detection for vehicles.
     * Boats are 1.375 blocks wide and can straddle ice/non-ice boundaries,
     * causing isNearIce() to return false while the boat is still on ice.
     * This checks a wider area below and around the player.
     */
    public static boolean isNearIceWide(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        CompensatedWorld cache = data.getWorldCache();
        int bX = (int) Math.floor(data.getX());
        int bZ = (int) Math.floor(data.getZ());
        for (int dy = -1; dy <= 0; dy++) {
            int bY = (int) Math.floor(data.getY() + dy);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (BlockPropertyRegistry.isIce(cache.getBlockState(bX + dx, bY, bZ + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * FIX: Detect blue ice specifically for vehicle speed calculations.
     * Blue ice has friction 0.989 vs regular ice at 0.98, which allows boats
     * to reach significantly higher speeds (~8-9 blocks/tick vs ~4 on regular ice).
     */
    public static boolean isNearBlueIce(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        CompensatedWorld cache = data.getWorldCache();
        int bX = (int) Math.floor(data.getX());
        int bZ = (int) Math.floor(data.getZ());
        for (int dy = -1; dy <= 0; dy++) {
            int bY = (int) Math.floor(data.getY() + dy);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    WrappedBlockState state = cache.getBlockState(bX + dx, bY, bZ + dz);
                    if (state != null && state.getType().getName().toUpperCase().contains("BLUE_ICE")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isBouncy(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        WrappedBlockState state = data.getWorldCache().getBlockState(
                (int) Math.floor(data.getX()),
                (int) Math.floor(data.getY() - 0.1),
                (int) Math.floor(data.getZ())
        );
        String name = state.getType().getName().toLowerCase();
        return name.contains("slime_block") || name.contains("honey_block") || name.contains("bed");
    }

    public static boolean isNearMaterial(Player player, Material material) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        CompensatedWorld cache = data.getWorldCache();
        int bX = (int) Math.floor(data.getX());
        int bY = (int) Math.floor(data.getY());
        int bZ = (int) Math.floor(data.getZ());
        String matName = material.name();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 1; y++) {
                    WrappedBlockState state = cache.getBlockState(bX + x, bY + y, bZ + z);
                    if (state.getType().getName().equalsIgnoreCase(matName)) return true;
                }
            }
        }
        return false;
    }

    public static boolean isNearStairOrSlab(Player player) {
        PlayerData data = ret.tawny.truthful.Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return false;
        CompensatedWorld cache = data.getWorldCache();
        int bX = (int) Math.floor(data.getX());
        int bY = (int) Math.floor(data.getY());
        int bZ = (int) Math.floor(data.getZ());
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 1; y++) {
                    WrappedBlockState state = cache.getBlockState(bX + x, bY + y, bZ + z);
                    String name = state.getType().getName().toLowerCase();
                    if (name.contains("stair") || name.contains("slab")) return true;
                }
            }
        }
        return false;
    }

    public static double getBlockHeight(Block block) {
        if (block == null || block.getType().isAir()) return 0.0;
        if (!Bukkit.isPrimaryThread()) return 1.0;

        try {
            VoxelShape shape = block.getCollisionShape();
            if (shape.getBoundingBoxes().isEmpty()) return 0.0;
            double maxY = 0.0;
            for (BoundingBox box : shape.getBoundingBoxes()) {
                if (box.getMaxY() > maxY) maxY = box.getMaxY();
            }
            return maxY;
        } catch (Throwable ignored) {
            Material type = block.getType();
            if (Tag.SLABS.isTagged(type) || Tag.STAIRS.isTagged(type)) return 0.5;
            if (Tag.FENCES.isTagged(type) || Tag.WALLS.isTagged(type)) return 1.5;
            return block.getType().isSolid() ? 1.0 : 0.0;
        }
    }
}