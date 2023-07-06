package org.voitac.anticheat.utils.player;

import org.voitac.anticheat.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.voitac.anticheat.utils.world.WorldUtils;
import org.voitac.anticheat.utils.math.VanillaMathHelper;

public final class PredictionUtils {
    private PredictionUtils(){}

    public static final double PREDICTION_BUFFER = 1E-13;

    /**
     *
     * @param y motion to apply
     * @return MotionY result after gravity application.
     *
     * This should NOT be trusted as a final value, PREDICTION_BUFFER should always be used to compensated differences.
     * This does not take into account any place in the world and relies on you to calculate for it.
     */
    public static double gravity(final double y) {
        return (y - 0.08) * PlayerUtils.GRAVITY;
    }

    public static double[] nextMotion(float forward, float strafe, double motionX, double motionZ, final boolean onGround, final Player player) {
        forward *= 0.98F;
        strafe *= 0.98F;

        float mult = 0.91F;
        if (onGround)
            /* Get slipperiness 1 block below the player */
            mult *= WorldUtils.getSlippinessMultiplier(0, -1, 0, player);

        /* acceleration = (0.6*0.91)^3 / (slipperiness*0.91)^3) */
        float acceleration = 0.16277136F / (mult * mult * mult);

        float movementFactor = (float) getMovementFactor(player, onGround);

        // Player updates motion factor
        //this.updateMotionXZ(strafe, forward, movementFactor);

        final double[] motionXZMod = applyMotionXZ(strafe, forward, movementFactor, motionX, motionZ, player.getLocation().getYaw());
        motionX = motionXZMod[0];
        motionZ = motionXZMod[1];

        // Player updates movement goal
        //this.moveEntity(this.motionX, this.motionY, this.motionZ);

        motionX *= mult;
        motionZ *= mult;

        return new double[] {motionX, motionZ};
    }

    public static double[] applyMotionXZ(float strafe, float forward, float movementFactor, double motionX, double motionZ, float rotationYaw) {
        /*
         * This function is responsible for the existence of 45° strafe. The geometry doesn't seem to make sense...
         * Note that:
         *     - Sprint multiplier is contained within "movementFactor"
         *     - Sneak multiplier is contained within "strafe" and "forward"
         * This is likely because Sneaking was implemented long before Sprinting
         */
        float distance = strafe * strafe + forward * forward;

        if (distance >= 1.0E-4F) {
            distance = VanillaMathHelper.sqrt_float(distance);

            if (distance < 1.0F)
                distance = 1.0F;

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
        float result = 0.42f;
        final PotionEffect jump = PlayerUtils.getPotion(PotionEffectType.JUMP, data);
        if (jump != null)
            result += (double)((float)(jump.getAmplifier() + 1) * 0.1F);

        return (double) result;
    }

    public static double getJumpMovementFactor(final Player player) {
        float jumpMovementFactor = PlayerUtils.AIR_MOVEMENT_FACTOR;
        if(!player.isSprinting())
            return jumpMovementFactor;
        return (float)((double)jumpMovementFactor + (double)PlayerUtils.AIR_MOVEMENT_FACTOR * 0.3D);
    }

    public static double getMovementFactor(final Player player, final boolean onGround) {
        if(onGround)
            return getJumpMovementFactor(player);
        return PlayerUtils.AIR_MOVEMENT_FACTOR;
    }
}
