package ret.tawny.truthful.utils.world;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.PlayerData;


public final class PhysicsUtils {

    private PhysicsUtils() {
    }

    public static final double GRAVITY = 0.08D;
    public static final double AIR_DRAG = 0.9800000190734863D;
    public static final double SLOW_FALLING_GRAVITY = 0.01D;
    public static final double HORIZONTAL_AIR_FRICTION = 0.91D;

    public static final float FRICTION_ICE = 0.98f;
    public static final float FRICTION_SLIME = 0.8f;
    public static final float FRICTION_BLUE_ICE = 0.989f;
    public static final float FRICTION_DEFAULT = 0.6f;

    public static double getEffectiveHorizontalFriction(Player player, PlayerData data) {
        if (data == null)
            return HORIZONTAL_AIR_FRICTION;

        int blockX = (int) Math.floor(data.getX());
        int blockY = (int) Math.floor(data.getY() - 0.5000001D);
        int blockZ = (int) Math.floor(data.getZ());

        WrappedBlockState state = data.getWorldCache().getBlockState(blockX, blockY, blockZ);
        float friction = BlockPropertyRegistry.getFriction(state);

        return friction * HORIZONTAL_AIR_FRICTION;
    }

    public static double getEffectiveMaxGroundSpeed(Player player, PlayerData data, float rawBaseSpeed) {
        double maxSpeed = 0.32;

        int speedAmp = getPotionLevel(data, PotionEffectType.SPEED);
        if (speedAmp > 0) {
            maxSpeed += speedAmp * 0.062;
        }

        int slowAmp = getPotionLevel(data, PotionEffectType.SLOWNESS);
        if (slowAmp > 0) {
            maxSpeed *= Math.max(0.0, 1.0 - (0.15 * slowAmp));
        }

        if (data != null) {
            maxSpeed *= (data.getWalkSpeed() / 0.1);
        }

        double friction = getEffectiveHorizontalFriction(player, data);
        if (friction > 0.6) {
            maxSpeed += 0.28;
        }

        return maxSpeed;
    }

    public static double getBaseSpeed(Player player, PlayerData data, float baseSpeed) {
        double current = baseSpeed;

        if (data != null) {
            current *= (data.getWalkSpeed() / 0.1);
        }

        int speedAmp = getPotionLevel(data, PotionEffectType.SPEED);
        if (speedAmp > 0) {
            current *= 1.0 + (0.2 * speedAmp);
        }

        int slowAmp = getPotionLevel(data, PotionEffectType.SLOWNESS);
        if (slowAmp > 0) {
            current *= Math.max(0.0, 1.0 - (0.15 * slowAmp));
        }

        return current;
    }

    public static double getBaseSpeed(Player player, float baseSpeed) {
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        return getBaseSpeed(player, data, baseSpeed);
    }

    public static boolean shouldSlowDown(ItemStack item, Player player) {
        if (item == null || item.getType() == Material.AIR)
            return false;
        Material mat = item.getType();
        if (mat.name().contains("MACE"))
            return false;

        if (mat.isEdible() || mat == Material.MILK_BUCKET || mat == Material.POTION || mat == Material.HONEY_BOTTLE) {
            if (player != null) {
                PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
                int foodLevel = (data != null) ? data.getFoodLevel() : player.getFoodLevel();
                return foodLevel < 20 || mat == Material.GOLDEN_APPLE || mat == Material.ENCHANTED_GOLDEN_APPLE
                        || mat == Material.CHORUS_FRUIT;
            }
            return true;
        }

        return mat == Material.BOW || mat == Material.CROSSBOW || mat == Material.TRIDENT || mat == Material.SHIELD
                || mat == Material.SPYGLASS;
    }

    public static float getFriction(Block block) {
        if (block == null || block.getType().isAir())
            return FRICTION_DEFAULT;
        return getFrictionFromMaterial(block.getType());
    }

    public static float getFriction(WrappedBlockState state) {
        if (state == null || state.getType().isAir())
            return FRICTION_DEFAULT;
        return BlockPropertyRegistry.getFriction(state);
    }

    private static float getFrictionFromMaterial(Material mat) {
        if (Tag.ICE.isTagged(mat)) {
            if (mat == Material.BLUE_ICE)
                return FRICTION_BLUE_ICE;
            return FRICTION_ICE;
        }
        if (mat == Material.SLIME_BLOCK)
            return FRICTION_SLIME;
        if (mat == Material.HONEY_BLOCK || mat == Material.SOUL_SAND || mat == Material.SOUL_SOIL)
            return 0.4f;
        return FRICTION_DEFAULT;
    }

    public static int getPotionLevel(PlayerData data, PotionEffectType type) {
        if (data != null)
            return data.getPotionLevel(type);
        return 0;
    }

    public static int getPotionLevel(Player player, PotionEffectType type) {
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data != null)
            return data.getPotionLevel(type);

        if (!Bukkit.isPrimaryThread())
            return 0;
        PotionEffect effect = player.getPotionEffect(type);
        return (effect != null) ? effect.getAmplifier() + 1 : 0;
    }

    public static int getPotionLevel(org.bukkit.entity.LivingEntity entity, PotionEffectType type) {
        if (entity instanceof Player player) {
            return getPotionLevel(player, type);
        }
        if (!Bukkit.isPrimaryThread())
            return 0;
        PotionEffect effect = entity.getPotionEffect(type);
        return (effect != null) ? effect.getAmplifier() + 1 : 0;
    }

    public static boolean hasEffect(PlayerData data, PotionEffectType type) {
        if (data != null)
            return data.hasPotionEffect(type);
        return false;
    }

    public static boolean hasEffect(Player player, PotionEffectType type) {
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data != null)
            return data.hasPotionEffect(type);
        if (!Bukkit.isPrimaryThread())
            return false;
        return player.hasPotionEffect(type);
    }

    public static int getSoulSpeedLevel(Player player) {
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        return (data != null) ? data.getEnchantLevel("soul_speed") : 0;
    }

    public static int getDepthStriderLevel(Player player) {
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        return (data != null) ? data.getEnchantLevel("depth_strider") : 0;
    }

    public static int getSwiftSneakLevel(Player player) {
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        return (data != null) ? data.getEnchantLevel("swift_sneak") : 0;
    }
}
