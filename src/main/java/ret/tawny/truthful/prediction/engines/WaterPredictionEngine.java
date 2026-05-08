package ret.tawny.truthful.prediction.engines;

import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.PhysicsConstants;

/**
 * WaterPredictionEngine - Simulates vanilla Minecraft water/swimming physics.
 *
 * Vanilla water movement (1.13+):
 * - Water drag: 0.8 per tick (much higher than air's 0.91)
 * - Gravity in water: 0.04 (half of normal 0.08)
 * - Swimming forward: 0.02 * effectiveSpeed * 1.2 (swimming boost)
 * - Drowning players sink faster
 * - Depth Strider enchantment reduces water drag
 * - Dolphin's Grace gives massive water speed boost
 *
 * Pre-1.13 water physics differs (no swimming animation).
 */
public final class WaterPredictionEngine {

    private static final double WATER_DRAG_XZ = 0.8D;
    private static final double WATER_GRAVITY = 0.04D;
    private static final double WATER_DRAG_Y = 0.9800000190734863D;
    private static final double SWIMMING_INPUT_MULT = 1.2D;
    private static final double DOLPHINS_GRACE_MULT = 2.5D;
    private static final double MIN_WATER_MOTION = 0.003D;

    private final PlayerData data;

    private double predictedHorizontal;
    private double predictedVertical;
    private double predictedDeltaX;
    private double predictedDeltaZ;

    public WaterPredictionEngine(PlayerData data) {
        this.data = data;
    }

    public void predict(double lastHorizontal, double lastDeltaY) {
        double effectiveSpeed = getEffectiveSpeed();
        double drag = resolveWaterDrag();

        // Horizontal prediction in water
        double coastingSpeed = lastHorizontal * drag;

        // Swimming input force
        double inputForce = 0.0;
        if (data.isSprinting() || data.isSwimming()) {
            double swimMult = data.isSwimming() ? SWIMMING_INPUT_MULT : 1.0D;
            inputForce = 0.02D * effectiveSpeed * swimMult;
        }

        // Dolphin's Grace check
        if (data.hasPotionEffect(org.bukkit.potion.PotionEffectType.DOLPHINS_GRACE)) {
            inputForce *= DOLPHINS_GRACE_MULT;
        }

        predictedHorizontal = (coastingSpeed + inputForce) * 0.98D;

        // Compute predicted deltas proportionally
        if (lastHorizontal > 0.001D) {
            double ratio = predictedHorizontal / lastHorizontal;
            predictedDeltaX = data.getLastDeltaX() * ratio;
            predictedDeltaZ = data.getLastDeltaZ() * ratio;
        } else {
            predictedDeltaX = 0.0D;
            predictedDeltaZ = 0.0D;
        }

        // Vertical prediction in water
        double waterGravity = WATER_GRAVITY;

        // Swimming upward when sprinting + looking up
        if ((data.isSprinting() || data.isSwimming()) && data.getPitch() < -30.0F) {
            predictedVertical = 0.04D;
        } else {
            // Sinking
            double verticalDrag = WATER_DRAG_Y;
            predictedVertical = (lastDeltaY - waterGravity) * verticalDrag;
        }

        // Add active velocity
        predictedVertical += data.getVelocities().getQueuedVelocityVector().getY();

        // Terminal velocity in water
        if (predictedVertical < -0.5D) {
            predictedVertical = -0.5D;
        }
    }

    private double getEffectiveSpeed() {
        double attrSpeed = data.getWalkSpeed();
        if (attrSpeed <= 0.0D) attrSpeed = 0.1D;

        double potionMult = 1.0D;
        int speedLevel = data.getPotionLevel(org.bukkit.potion.PotionEffectType.SPEED);
        if (speedLevel > 0) potionMult *= (1.0D + 0.2D * speedLevel);

        int slownessLevel = data.getPotionLevel(org.bukkit.potion.PotionEffectType.SLOWNESS);
        if (slownessLevel > 0) potionMult *= (1.0D - 0.15D * slownessLevel);

        return attrSpeed * potionMult;
    }

    private double resolveWaterDrag() {
        double drag = WATER_DRAG_XZ;

        // Depth Strider reduces water drag
        int depthStrider = data.getEnchantLevel("depth_strider");
        if (depthStrider > 0) {
            drag += 0.05D * depthStrider;
            if (drag > 0.98D) drag = 0.98D;
        }

        return drag;
    }

    public double getPredictedHorizontal() { return predictedHorizontal; }
    public double getPredictedVertical() { return predictedVertical; }
    public double getPredictedDeltaX() { return predictedDeltaX; }
    public double getPredictedDeltaZ() { return predictedDeltaZ; }
}
