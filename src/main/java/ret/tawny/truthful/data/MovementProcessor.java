package ret.tawny.truthful.data;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ret.tawny.truthful.sync.VelocityQueue;
import ret.tawny.truthful.utils.world.BlockPropertyRegistry;
import ret.tawny.truthful.utils.world.PhysicsConstants;

/**
 * Enterprise-grade Movement Prediction Engine.
 *
 * Performs a multi-input brute-force simulation across all plausible
 * input permutations (forward/backward/none × left/right/none × sprint × sneak)
 * to compute the BEST-CASE valid movement for the player. This is the
 * technique used by top-tier anti-cheats (Grim, Polar) to achieve
 * near-zero false positives while maintaining strict enforcement.
 *
 * The engine also maintains a single-path "legacy" prediction for
 * backward compatibility with checks that don't need the brute-force layer.
 */
public final class MovementProcessor {

    // --- Minecraft Physics Constants ---
    private static final double AIR_FRICTION_XZ = 0.91D;
    private static final double VERTICAL_DRAG = 0.9800000190734863D;
    private static final double STEP_HEIGHT = 0.6D;
    private static final double MIN_MOTION = 0.003D;
    private static final double SPRINT_BONUS_MULTIPLIER = 1.3D;
    private static final double SPRINT_JUMP_BOOST = 0.2D;
    private static final double SNEAK_MULTIPLIER = 0.3D;

    // Input directions for brute-force simulation
    private static final double[][] INPUT_VECTORS = {
            {0.0, 0.0},    // Stationary
            {0.0, 1.0},    // Forward
            {0.0, -1.0},   // Backward
            {1.0, 0.0},    // Right
            {-1.0, 0.0},   // Left
            {1.0, 1.0},    // Forward+Right (normalized below)
            {-1.0, 1.0},   // Forward+Left
            {1.0, -1.0},   // Backward+Right
            {-1.0, -1.0},  // Backward+Left
    };

    private final PlayerData data;

    // --- Legacy single-path prediction (backward compat) ---
    private double predictedHorizontal;
    private double predictedVertical;
    private double predictedDeltaX;
    private double predictedDeltaZ;

    // --- Multi-input brute-force results ---
    private double bestCaseHorizontal, bestCaseVertical;
    private double minHorizontalDeviation, minVerticalDeviation, minVectorDeviation;
    private double predictedAcceleration, maxValidAcceleration;
    private double groundFriction;

    // --- Velocity sampling ---
    private double sampledVelocityX;
    private double sampledVelocityY;
    private double sampledVelocityZ;

    private int ticksSinceStop;

    public MovementProcessor(PlayerData data) {
        this.data = data;
        reset();
    }

    public void reset() {
        predictedHorizontal = 0.0D;
        predictedVertical = 0.0D;
        predictedDeltaX = 0.0D;
        predictedDeltaZ = 0.0D;
        bestCaseHorizontal = 0.0D;
        bestCaseVertical = 0.0D;
        this.minHorizontalDeviation = 100.0D;
        this.minVerticalDeviation = 100.0D;
        this.minVectorDeviation = 100.0D;
        this.bestCaseHorizontal = 0.0D;
        maxValidAcceleration = 0.0D;
        groundFriction = 0.6D * AIR_FRICTION_XZ;
        sampledVelocityX = 0.0D;
        sampledVelocityY = 0.0D;
        sampledVelocityZ = 0.0D;
        ticksSinceStop = 0;
    }

    public void handleTeleport() {
        reset();
        ticksSinceStop = 8;
    }

    /**
     * Full prediction pass: single-path + multi-input brute-force.
     * Called once per movement tick from PlayerData.update().
     */
    public void predict() {
        final double deltaX = data.getDeltaX();
        final double deltaY = data.getDeltaY();
        final double deltaZ = data.getDeltaZ();
        final double lastDeltaY = data.getLastDeltaY();
        final double lastHorizontal = data.getLastDeltaXZ();
        final double actualXZ = data.getDeltaXZ();

        this.bestCaseHorizontal = 0.0D;
        this.bestCaseVertical = 0.0D;
        this.minHorizontalDeviation = Double.MAX_VALUE;
        this.minVerticalDeviation = Double.MAX_VALUE;
        this.minVectorDeviation = Double.MAX_VALUE;
        this.maxValidAcceleration = 0.0D;

        if (Math.abs(deltaX) < MIN_MOTION && Math.abs(deltaZ) < MIN_MOTION) ticksSinceStop++;
        else ticksSinceStop = 0;

        // --- Compute ground friction ---
        final boolean onGround = data.isServerGround();
        this.groundFriction = onGround ? resolveGroundFriction() : AIR_FRICTION_XZ;

        // --- Sample velocity ---
        Vector activeVelocity = data.getVelocities().getQueuedVelocityVector();
        sampledVelocityX = activeVelocity.getX();
        sampledVelocityY = activeVelocity.getY();
        sampledVelocityZ = activeVelocity.getZ();

        // ======================================================
        // LEGACY SINGLE-PATH PREDICTION (backward compatibility)
        // ======================================================
        predictedDeltaX = data.getLastDeltaX() * groundFriction + sampledVelocityX;
        predictedDeltaZ = data.getLastDeltaZ() * groundFriction + sampledVelocityZ;
        predictedHorizontal = (lastHorizontal * groundFriction) + Math.hypot(sampledVelocityX, sampledVelocityZ);

        // Determine if jumping is plausible this tick.
        // Jump detection: was on ground last tick AND (moving up OR still on ground)
        // More lenient jump detection to catch edge cases
        boolean jumpStart = data.isLastGround() && (deltaY > 0.0D || onGround);
        boolean onGroundStaying = onGround && Math.abs(deltaY) <= STEP_HEIGHT;
        
        double gravity = resolveGravity();

        if (onGroundStaying) {
            // Player is staying on ground - no vertical movement expected
            predictedVertical = sampledVelocityY;
        } else if (jumpStart && deltaY > 0.0D) {
            // Player jumped - use jump impulse
            predictedVertical = PhysicsConstants.JUMP_IMPULSE + (data.getPotionLevel(PotionEffectType.JUMP_BOOST) * PhysicsConstants.JUMP_BOOST_MODIFIER) + sampledVelocityY;
        } else {
            // Player is falling or in air - apply gravity
            predictedVertical = (lastDeltaY - gravity) * VERTICAL_DRAG + sampledVelocityY;
        }

        // ======================================================
        // MULTI-INPUT BRUTE-FORCE SIMULATION
        // ======================================================
        predictMultiInput(actualXZ, deltaY, lastHorizontal, lastDeltaY, onGround, jumpStart, gravity);

        // Apply tick friction to velocity queue AFTER prediction
        data.getVelocities().applyTickFriction(groundFriction, VERTICAL_DRAG);
    }

    /**
     * Brute-force simulation across all plausible input combinations.
     * Finds the input permutation that produces the closest-to-actual valid movement.
     */
    private void predictMultiInput(double actualXZ, double actualY,
                                   double lastHorizontal, double lastDeltaY,
                                   boolean onGround, boolean jumpStart, double gravity) {

        final double lastDeltaX = data.getLastDeltaX();
        final double lastDeltaZ = data.getLastDeltaZ();
        final float yaw = data.getYaw();

        // Compute movement input factor
        final double walkSpeed = data.getWalkSpeed(); // generic.movement_speed attribute (default 0.1)
        final int speedLevel = data.getPotionLevel(PotionEffectType.SPEED);
        final int slownessLevel = data.getPotionLevel(PotionEffectType.SLOWNESS);

        double bestHorizontal = Double.NEGATIVE_INFINITY;
        double bestVertical = Double.NEGATIVE_INFINITY;
        double closestHDev = Double.MAX_VALUE;
        double closestVDev = Double.MAX_VALUE;
        double closestVectorDev = Double.MAX_VALUE;
        double maxAccel = 0.0D;

        // --- Vertical predictions (fewer combos needed) ---
        // Evaluate all possible vertical paths: staying grounded, jumping, falling
        boolean canJump = data.isLastGround() || onGround;
        double[] verticalCandidates = computeVerticalCandidates(lastDeltaY, onGround, canJump, gravity);
        
        for (double vy : verticalCandidates) {
            double vDev = Math.abs(actualY - vy);
            if (vDev < closestVDev) {
                closestVDev = vDev;
            }
            if (vy > bestVertical) {
                bestVertical = vy;
            }
        }

        // --- Horizontal brute-force across all input combinations ---
        boolean[] sprintStates = {true, false};
        boolean sneaking = data.isSneaking();

        for (double[] input : INPUT_VECTORS) {
            double strafe = input[0];
            double forward = input[1];

            // Normalize diagonal movement (vanilla behavior)
            double mag = strafe * strafe + forward * forward;
            if (mag >= 1.0D) {
                double inv = 1.0 / Math.sqrt(mag);
                strafe *= inv;
                forward *= inv;
            }

            for (boolean sprinting : sprintStates) {
                // In vanilla, the client can be in a sprinting state regardless of current direction
                // if the packet sequence allows it. We test both to avoid desync flags.

                double accelFactor = computeAccelerationFactor(walkSpeed, speedLevel, slownessLevel, sprinting, sneaking, onGround);

                // Convert yaw + input to world-space acceleration
                double yawRad = Math.toRadians(yaw);
                double sinYaw = Math.sin(yawRad);
                double cosYaw = Math.cos(yawRad);

                double inputAccelX = (strafe * cosYaw - forward * sinYaw) * accelFactor;
                double inputAccelZ = (forward * cosYaw + strafe * sinYaw) * accelFactor;

                // Apply momentum (last velocity * friction) + input acceleration + velocity
                double simDeltaX = lastDeltaX * groundFriction + inputAccelX + sampledVelocityX;
                double simDeltaZ = lastDeltaZ * groundFriction + inputAccelZ + sampledVelocityZ;

                // Sprint-jump boost (extra forward velocity at jump start)
                // We allow the boost if the player was on ground last tick (potential jump start)
                if (sprinting && canJump && actualY > 0.0D) {
                    simDeltaX -= sinYaw * SPRINT_JUMP_BOOST;
                    simDeltaZ += cosYaw * SPRINT_JUMP_BOOST;
                }

                // Apply minimum motion threshold (vanilla: values < 0.003 snap to 0)
                if (Math.abs(simDeltaX) < MIN_MOTION) simDeltaX = 0.0D;
                if (Math.abs(simDeltaZ) < MIN_MOTION) simDeltaZ = 0.0D;

                double simHorizontal = Math.hypot(simDeltaX, simDeltaZ);

                // Track the best case (most lenient valid prediction)
                if (simHorizontal > bestHorizontal) {
                    bestHorizontal = simHorizontal;
                }

                // Track closest match to actual movement (Magnitude)
                double hDev = Math.abs(actualXZ - simHorizontal);
                if (hDev < closestHDev) {
                    closestHDev = hDev;
                }

                // Track closest match to actual movement (Vector Spatial Distance)
                double deltaXActual = data.getDeltaX();
                double deltaZActual = data.getDeltaZ();
                double vecDev = Math.hypot(deltaXActual - simDeltaX, deltaZActual - simDeltaZ);
                if (vecDev < closestVectorDev) {
                    closestVectorDev = vecDev;
                }

                // Track max valid acceleration
                double accel = simHorizontal - lastHorizontal;
                if (accel > maxAccel) {
                    maxAccel = accel;
                }
            }
        }

        // Add uncertainty buffer for soul speed, depth strider enchantments
        int soulSpeed = data.getEnchantLevel("soul_speed");
        if (soulSpeed > 0 && data.getTicksTracked() - data.getLastSoulSandTick() < 8) {
            double soulBonus = 0.04 * soulSpeed;
            bestHorizontal += soulBonus;
            closestHDev = Math.max(0, closestHDev - soulBonus);
        }

        int depthStrider = data.getEnchantLevel("depth_strider");
        if (depthStrider > 0 && data.isInLiquid()) {
            double waterBonus = 0.033 * depthStrider;
            bestHorizontal += waterBonus;
            closestHDev = Math.max(0, closestHDev - waterBonus);
        }

        this.bestCaseHorizontal = Math.max(0.0D, bestHorizontal);
        this.bestCaseVertical = bestVertical;
        this.minHorizontalDeviation = closestHDev;
        this.minVerticalDeviation = closestVDev;
        this.minVectorDeviation = closestVectorDev;
        this.predictedAcceleration = actualXZ - lastHorizontal;
        this.maxValidAcceleration = maxAccel;
    }

    /**
     * Compute all valid vertical predictions.
     * Returns array of possible vertical velocities for the current tick.
     */
    private double[] computeVerticalCandidates(double lastDeltaY, boolean onGround,
                                                boolean canJump, double gravity) {
        int jumpBoost = data.getPotionLevel(PotionEffectType.JUMP_BOOST);

        // Normal gravity continuation (falling with drag)
        double gravityPred = (lastDeltaY - gravity) * VERTICAL_DRAG + sampledVelocityY;

        // Jump from ground
        double jumpPred = PhysicsConstants.JUMP_IMPULSE + (jumpBoost * PhysicsConstants.JUMP_BOOST_MODIFIER) + sampledVelocityY;

        // On ground (no vertical movement, only velocity)
        double groundPred = sampledVelocityY;

        // Step up (within step height)
        double stepPred = STEP_HEIGHT;

        // Terminal velocity floor
        double terminalPred = PhysicsConstants.TERMINAL_VELOCITY + sampledVelocityY;

        if (onGround) {
            // On ground: could be standing, jumping, or stepping
            boolean isMovingHorizontally = data.getDeltaXZ() > 0.01D;
            if (isMovingHorizontally) {
                return new double[]{groundPred, jumpPred, stepPred, 0.0D, gravityPred};
            } else {
                return new double[]{groundPred, jumpPred, 0.0D, gravityPred};
            }
        } else if (canJump) {
            // Recently left ground: could be jump continuation or falling
            return new double[]{jumpPred, gravityPred, 0.0D, groundPred};
        } else {
            // In air: falling or with velocity
            return new double[]{gravityPred, groundPred, 0.0D, terminalPred};
        }
    }

    /**
     * Compute the acceleration factor for a given input state.
     * Mirrors vanilla's LivingEntity.travel() acceleration formula.
     */
    private double computeAccelerationFactor(double walkSpeed, int speedLevel, int slownessLevel,
                                             boolean sprinting, boolean sneaking, boolean onGround) {
        double base = walkSpeed;

        // Speed potion: +20% per level
        if (speedLevel > 0) {
            base *= 1.0 + (0.2 * speedLevel);
        }

        // Slowness potion: -15% per level
        if (slownessLevel > 0) {
            base *= Math.max(0.0, 1.0 - (0.15 * slownessLevel));
        }

        // Sprint: +30%
        if (sprinting) {
            base *= SPRINT_BONUS_MULTIPLIER;
        }

        // Sneak: movement factor 0.3
        if (sneaking) {
            base *= SNEAK_MULTIPLIER;
        }

        if (onGround) {
            // Ground acceleration = (0.1 * (0.216 / (friction^3)))
            // where friction = blockFriction * 0.91
            double f3 = groundFriction * groundFriction * groundFriction;
            return base * (0.16277136D / f3);
        } else {
            // Vanilla air acceleration: 0.02 base, 0.026 when sprinting.
            // Speed/Slowness potions affect the input vector length, not the 0.02 constant itself.
            return sprinting ? 0.026D : 0.02D;
        }
    }

    /**
     * Resolve effective gravity for the current tick.
     */
    private double resolveGravity() {
        if (data.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return PhysicsConstants.SLOW_FALLING_GRAVITY;
        if (data.hasPotionEffect(PotionEffectType.LEVITATION)) return PhysicsConstants.GRAVITY * 0.15D;
        return PhysicsConstants.GRAVITY;
    }

    /**
     * Resolve ground friction from the block below the player.
     * Uses robust sampling to handle edge cases (block boundaries, slabs, etc.)
     */
    private double resolveGroundFriction() {
        double maxFriction = 0.6F;
        double startX = data.getX() - 0.3D;
        double startZ = data.getZ() - 0.3D;
        int blockY = floor(data.getY() - 0.2D);

        for (double sampleX = startX; sampleX <= startX + 0.6D; sampleX += 0.3D) {
            for (double sampleZ = startZ; sampleZ <= startZ + 0.6D; sampleZ += 0.3D) {
                maxFriction = Math.max(maxFriction, sampleGroundFriction(sampleX, sampleZ, blockY));
            }
        }

        return maxFriction * AIR_FRICTION_XZ;
    }

    private double sampleGroundFriction(double sampleX, double sampleZ, int blockY) {
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

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    // =====================================================
    // PUBLIC GETTERS — Legacy single-path (backward compat)
    // =====================================================
    public double getPredictedHorizontal() { return predictedHorizontal; }
    public double getPredictedVertical() { return predictedVertical; }
    public double getPredictedDeltaX() { return predictedDeltaX; }
    public double getPredictedDeltaZ() { return predictedDeltaZ; }
    public double getVelocityX() { return sampledVelocityX; }
    public double getVelocityY() { return sampledVelocityY; }
    public double getVelocityZ() { return sampledVelocityZ; }
    public boolean isStationary() { return ticksSinceStop > 5; }

    // Legacy getter compatibility
    public double getPredictedDragX() { return predictedDeltaX; }
    public double getPredictedDragZ() { return predictedDeltaZ; }
    public double getSimulatedXZ() { return predictedHorizontal; }
    public double getSimulatedY() { return predictedVertical; }

    // =====================================================
    // PUBLIC GETTERS — Multi-input brute-force results
    // =====================================================

    /** The maximum horizontal speed any valid input combination can produce. */
    public double getBestCaseHorizontal() { return bestCaseHorizontal; }

    /** The maximum vertical value any valid prediction produces. */
    public double getBestCaseVertical() { return bestCaseVertical; }

    /** The smallest deviation between actual XZ and any simulated XZ. */
    public double getMinHorizontalDeviation() { return minHorizontalDeviation; }

    /** The smallest deviation between actual Y and any simulated Y. */
    public double getMinVerticalDeviation() { return minVerticalDeviation; }

    /** The smallest spatial (vector) distance between actual movement and any simulated candidate. */
    public double getMinVectorDeviation() { return minVectorDeviation; }

    /** Actual acceleration this tick (deltaXZ - lastDeltaXZ). */
    public double getPredictedAcceleration() { return predictedAcceleration; }

    /** Maximum acceleration any valid input can produce. */
    public double getMaxValidAcceleration() { return maxValidAcceleration; }

    /** Current ground friction (blockFriction * 0.91). */
    public double getGroundFriction() { return groundFriction; }

    /** Effective gravity for this tick. */
    public double getEffectiveGravity() { return resolveGravity(); }
}
