package ret.tawny.truthful.utils.player;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.math.VanillaMathHelper;
import ret.tawny.truthful.utils.world.WorldUtils;

public final class PredictionUtils {
    private PredictionUtils() {}

    public static final double PREDICTION_BUFFER = 1E-13;

    public static double gravity(final double motionY) {
        return (motionY - PlayerUtils.GRAVITY_ACCELERATION) * PlayerUtils.AIR_DRAG;
    }

    public static double[] nextMotion(float forward, float strafe, double motionX, double motionZ, final boolean onGround, final Player player) {
        forward *= 0.98F;
        strafe *= 0.98F;

        float mult = 0.91F;
        if (onGround) {
            mult *= WorldUtils.getSlippinessMultiplier(player);
        }

        float acceleration = 0.16277136F / (mult * mult * mult);
        float movementFactor = (float) getMovementFactor(player, onGround);

        final double[] motionXZMod = applyMotionXZ(strafe, forward, movementFactor, motionX, motionZ, player.getLocation().getYaw());
        motionX = motionXZMod[0];
        motionZ = motionXZMod[1];

        motionX *= mult;
        motionZ *= mult;

        return new double[] {motionX, motionZ};
    }

    public static double[] applyMotionXZ(float strafe, float forward, float movementFactor, double motionX, double motionZ, float rotationYaw) {
        float distance = strafe * strafe + forward * forward;

        if (distance >= 1.0E-4F) {
            distance = VanillaMathHelper.sqrt_float(distance);

            if (distance < 1.0F) {
                distance = 1.0F;
            }

            distance = movementFactor / distance;
            strafe = strafe * distance;
            forward = forward * distance;
            float sinYaw = VanillaMathHelper.sin((float) (rotationYaw * Math.PI / 180.0F));
            float cosYaw = VanillaMathHelper.cos((float) (rotationYaw * Math.PI / 180.0F));
            motionX += strafe * cosYaw - forward * sinYaw;
            motionZ += forward * cosYaw + strafe * sinYaw;
        }
        return new double[] {motionX, motionZ};
    }

    public static double getJumpMotion(final PlayerData data) {
        double result = PlayerUtils.JUMP_MOTION;
        final PotionEffect jump = PlayerUtils.getPotion(PotionEffectType.JUMP_BOOST, data);
        if (jump != null) {
            result += (float)(jump.getAmplifier() + 1) * 0.1F;
        }
        return result;
    }

    public static double getMovementFactor(final Player player, final boolean onGround) {
        float airMovementFactor = 0.02F;
        if(onGround) {
            if(player.isSprinting()) {
                return (float)(airMovementFactor + (airMovementFactor * 0.3D));
            }
            return airMovementFactor;
        }
        return airMovementFactor;
    }
}