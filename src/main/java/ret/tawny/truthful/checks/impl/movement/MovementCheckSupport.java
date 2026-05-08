package ret.tawny.truthful.checks.impl.movement;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.data.ExemptionType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.prediction.UncertaintyHandler;
import ret.tawny.truthful.utils.world.BlockPropertyRegistry;

/**
 * Shared utilities for all movement checks.
 * Centralizes exemption logic, severity calculations, uncertainty evaluation,
 * and physics computations to prevent duplication across checks.
 */
public final class MovementCheckSupport {

    private MovementCheckSupport() {}

    // =====================================================
    // EXEMPTION LOGIC
    // =====================================================

    /**
     * Standard skip predicate for prediction-based movement checks.
     */
    public static boolean skipForPrediction(PlayerData data) {
        return data == null
                || data.isServerFrozen()
                || data.isTeleportTick()
                || data.isMovementExempt()
                || data.isInsideVehicle()
                || data.getTicksTracked() - data.getLastVehicleExitTick() < 10 // FIX: Vehicle exit false flag
                || data.isRiptiding()
                || data.isExempt(ExemptionType.RIPTIDE)
                || data.isExempt(ExemptionType.WIND_CHARGE)
                || data.isExempt(ExemptionType.MACE_SMASH)
                || data.getMovementContext().isEnvironmentUnsafeForPrediction();
    }

    /**
     * Skip predicate for velocity-dependent checks.
     */
    public static boolean skipForVelocity(PlayerData data) {
        return skipForPrediction(data)
                || data.isExempt(ExemptionType.VELOCITY)
                || data.isUnderBlock();
    }

    /**
     * Liquid prediction intentionally runs in water/lava, so it must not reuse
     * the generic skip that treats liquid as an unsafe environment.
     */
    public static boolean skipForLiquidPrediction(PlayerData data) {
        return data == null
                || data.isServerFrozen()
                || data.isTeleportTick()
                || data.isMovementExempt()
                || data.isInsideVehicle()
                || data.getTicksTracked() - data.getLastVehicleExitTick() < 10 // FIX: Vehicle exit false flag
                || data.isRiptiding()
                || data.isExempt(ExemptionType.RIPTIDE)
                || data.isExempt(ExemptionType.WIND_CHARGE)
                || data.isExempt(ExemptionType.MACE_SMASH)
                || data.isOnClimbable()
                || data.isInWeb()
                || data.getMovementContext().isPowderSnow()
                || data.getMovementContext().isSlimeBounce()
                || data.getMovementContext().isHoney();
    }

    // =====================================================
    // SEVERITY CALCULATION
    // =====================================================

    /**
     * Compute severity weight based on deviation magnitude.
     * Higher deviation = more weight added to the buffer per tick.
     *
     * @param deviation The measured deviation from prediction
     * @param light     Weight for small deviations
     * @param medium    Threshold for medium deviation
     * @param high      Threshold for high deviation
     */
    public static double severity(double deviation, double light, double medium, double high) {
        if (deviation >= high) return 5.0D;
        if (deviation >= medium) return 1.0D;
        return light;
    }

    /**
     * Compute a continuous severity value (not tiered) for finer granularity.
     * Maps deviation linearly: floor + (deviation / scale) capped at ceiling.
     */
    public static double continuousSeverity(double deviation, double floor, double scale, double ceiling) {
        return Math.min(ceiling, floor + (deviation / scale));
    }

    // =====================================================
    // UNCERTAINTY HANDLER INTEGRATION
    // =====================================================

    /**
     * Creates and populates an UncertaintyHandler with all relevant
     * environmental uncertainty sources for the current player state.
     * Checks should call this once, then use reduceOffset() to adjust
     * their deviation before comparing against thresholds.
     */
    public static UncertaintyHandler evaluateUncertainty(PlayerData data) {
        UncertaintyHandler handler = new UncertaintyHandler();
        handler.evaluate(data);

        // Additional uncertainty sources not covered by the base handler
        int ticksSinceDamage = data.getTicksTracked() - data.getLastDamageTick();
        if (ticksSinceDamage >= 0 && ticksSinceDamage < 6) {
            handler.add(0.04 * (6 - ticksSinceDamage), "recent_damage");
        }

        int ticksSinceHit = data.getTicksTracked() - data.getLastHitTick();
        if (ticksSinceHit >= 0 && ticksSinceHit < 4) {
            handler.add(0.03 * (4 - ticksSinceHit), "combat_knockback");
        }

        // Explosion grace uncertainty
        if (data.isInExplosionGraceWindow(2000L)) {
            handler.add(0.15, "explosion_grace");
        }

        // Glide transition uncertainty
        int ticksSinceGlide = data.getTicksTracked() - data.getLastGlideTick();
        if (ticksSinceGlide >= 0 && ticksSinceGlide < 10) {
            handler.add(0.06, "glide_transition");
        }

        // Riptide aftereffect
        int ticksSinceRiptide = data.getTicksTracked() - data.getLastRiptideTick();
        if (ticksSinceRiptide >= 0 && ticksSinceRiptide < 15) {
            handler.add(0.08, "riptide_aftereffect");
        }

        return handler;
    }

    // =====================================================
    // PHYSICS COMPUTATIONS
    // =====================================================

    /**
     * Compute the ground friction for the block below the player.
     * Returns blockFriction * 0.91 (the effective friction applied to movement).
     */
    public static double computeGroundFriction(PlayerData data) {
        double maxFriction = 0.6F;
        double startX = data.getX() - 0.3D;
        double startZ = data.getZ() - 0.3D;
        int blockY = floor(data.getY() - 0.2D);

        for (double sampleX = startX; sampleX <= startX + 0.6D; sampleX += 0.3D) {
            for (double sampleZ = startZ; sampleZ <= startZ + 0.6D; sampleZ += 0.3D) {
                maxFriction = Math.max(maxFriction, sampleGroundFriction(data, sampleX, sampleZ, blockY));
            }
        }

        return maxFriction * 0.91D;
    }

    /**
     * Compute the absolute maximum horizontal speed the player CAN legally achieve
     * given their current attributes, potions, enchantments, and sprint state.
     *
     * This is the hard-cap sanity check — even if prediction has a bug,
     * this value cannot be exceeded without cheats.
     */
    public static double computeMaxHorizontalSpeed(PlayerData data) {
        double walkSpeed = data.getWalkSpeed(); // generic.movement_speed attribute
        double base = walkSpeed;

        // Speed potion: +20% per level
        int speedLevel = data.getPotionLevel(PotionEffectType.SPEED);
        if (speedLevel > 0) {
            base *= 1.0 + (0.2 * speedLevel);
        }

        // Slowness: -15% per level
        int slownessLevel = data.getPotionLevel(PotionEffectType.SLOWNESS);
        if (slownessLevel > 0) {
            base *= Math.max(0.0, 1.0 - (0.15 * slownessLevel));
        }

        // Sprint: +30%
        if (data.isSprinting()) {
            base *= 1.3;
        }

        // Ground acceleration formula: base * (0.1 * (0.216 / friction^3))
        // Maximum ground speed is when this acceleration balances friction
        // Terminal velocity on ground ≈ accel / (1 - friction)
        double friction = data.isServerGround() ? computeGroundFriction(data) : 0.91D;
        double f3 = friction * friction * friction;
        double accel = base * (0.16277136D / f3);

        // Terminal velocity = accel / (1 - friction)
        double maxSpeed = accel / (1.0D - friction);

        // Soul speed enchantment bonus
        int soulSpeed = data.getEnchantLevel("soul_speed");
        if (soulSpeed > 0 && data.getTicksTracked() - data.getLastSoulSandTick() < 8) {
            maxSpeed += 0.04 * soulSpeed;
        }

        // Depth strider
        int depthStrider = data.getEnchantLevel("depth_strider");
        if (depthStrider > 0 && data.isInLiquid()) {
            maxSpeed += 0.033 * depthStrider;
        }

        // Sprint-jump boost adds ~0.2 forward velocity
        if (data.isSprinting() && data.getAirTicks() < 6) {
            maxSpeed += 0.22D;
        }

        return maxSpeed;
    }

    /**
     * Compute the expected jump impulse for the player.
     */
    public static double computeMaxJump(PlayerData data) {
        int jumpBoost = data.getPotionLevel(PotionEffectType.JUMP_BOOST);
        return 0.42D + (jumpBoost * 0.1D);
    }

    /**
     * Check if the current tick is within a teleport/velocity grace window.
     */
    public static boolean isInGraceWindow(PlayerData data) {
        int ticksTracked = data.getTicksTracked();
        // Landing grace: server and client ground state desync by ~1 tick at landing
        if (data.getAirTicks() == 0 && data.getPositionTracker().getLastAirTicks() > 0) {
            return true; // Just landed this tick
        }
        if (data.getAirTicks() == 0 && data.getTicksTracked() - data.getPositionTracker().getLastLandTick() < 3) {
            return true;
        }

        return ticksTracked - data.getMovementState().getLastTeleportTick() < 5
                || ticksTracked - data.getLastVelocityTick() < 4
                || ticksTracked - data.getLastDamageTick() < 6;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static double sampleGroundFriction(PlayerData data, double sampleX, double sampleZ, int blockY) {
        int blockX = floor(sampleX);
        int blockZ = floor(sampleZ);

        WrappedBlockState state = data.getWorldCache().getBlockState(blockX, blockY, blockZ);
        if (state.getType().isAir()) {
            state = data.getWorldCache().getBlockState(blockX, blockY - 1, blockZ);
        }
        if (state.getType().isAir()) {
            state = data.getWorldCache().getBlockState(blockX, blockY - 2, blockZ);
        }

        return BlockPropertyRegistry.getFriction(state);
    }
}