package ret.tawny.truthful.utils.physics;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.PhysicsConstants;
import ret.tawny.truthful.utils.world.PhysicsUtils;

public final class MovementSimulator {

    public static final double GRAVITY = 0.08D;
    public static final double SLOW_FALLING_GRAVITY = 0.01D;
    public static final double LIQUID_GRAVITY = 0.02D;
    public static final double AIR_DRAG_Y = 0.9800000190734863D;
    public static final double AIR_DRAG_XZ = 0.91D;
    public static final double TERMINAL_VELOCITY = -3.92D;
    public static final double MAX_UPWARD_VELOCITY = 10.0D;
    public static final double JUMP_IMPULSE = 0.42D;
    public static final double JUMP_BOOST_PER_LEVEL = 0.1D;
    public static final double SPRINT_JUMP_BOOST = 0.2D;
    public static final float DEFAULT_SLIPPERINESS = 0.6F;
    public static final double BASE_WALK_ACCEL = 0.1D;
    public static final double SPRINT_MULTIPLIER = 1.3D;
    public static final double SNEAK_MULTIPLIER = 0.3D;
    public static final double AIR_ACCEL = 0.02D;
    public static final double AIR_ACCEL_SPRINT = 0.026D;
    public static final double LIQUID_MULTIPLIER = 0.125D;
    public static final double COBWEB_MULTIPLIER = 0.067D;

    private double predictedDeltaY;
    private double predictedDeltaXZ;
    private double lastPredictedY;
    private double lastPredictedXZ;

    private double divergenceY;
    private double divergenceXZ;
    private double cumulativeDivergenceY;
    private double cumulativeDivergenceXZ;

    private boolean wasOnGround;
    private boolean wasJumping;
    private int airTicks;
    private int groundTicks;
    private double lastVerticalVelocity;
    private double lastHorizontalVelocity;

    private boolean reachedTerminal;
    private int ticksAtTerminal;

    public MovementSimulator() {
        reset();
    }

    public void reset() {
        this.predictedDeltaY = 0.0;
        this.predictedDeltaXZ = 0.0;
        this.lastPredictedY = 0.0;
        this.lastPredictedXZ = 0.0;
        this.divergenceY = 0.0;
        this.divergenceXZ = 0.0;
        this.cumulativeDivergenceY = 0.0;
        this.cumulativeDivergenceXZ = 0.0;
        this.wasOnGround = true;
        this.wasJumping = false;
        this.airTicks = 0;
        this.groundTicks = 0;
        this.lastVerticalVelocity = 0.0;
        this.lastHorizontalVelocity = 0.0;
        this.reachedTerminal = false;
        this.ticksAtTerminal = 0;
    }

    public void simulate(Player player, PlayerData data,
                         double actualDeltaY, double actualDeltaXZ,
                         boolean isOnGround, boolean isServerGround) {

        this.lastPredictedY = this.predictedDeltaY;
        this.lastPredictedXZ = this.predictedDeltaXZ;

        if (isServerGround) {
            this.groundTicks++;
            this.airTicks = 0;
            this.reachedTerminal = false;
            this.ticksAtTerminal = 0;
        } else {
            this.airTicks++;
            this.groundTicks = 0;
        }

        this.predictedDeltaY = predictVertical(player, data, isOnGround, isServerGround);
        this.predictedDeltaXZ = predictHorizontal(player, data, isOnGround, isServerGround);

        this.divergenceY = actualDeltaY - this.predictedDeltaY;
        this.divergenceXZ = actualDeltaXZ - this.predictedDeltaXZ;

        this.cumulativeDivergenceY = (this.cumulativeDivergenceY * 0.9) + Math.abs(this.divergenceY);
        this.cumulativeDivergenceXZ = (this.cumulativeDivergenceXZ * 0.9) + Math.max(0, this.divergenceXZ);

        this.wasOnGround = isServerGround;
        this.wasJumping = !this.wasOnGround && actualDeltaY > 0 && data.getLastDeltaY() <= 0;
        this.lastVerticalVelocity = actualDeltaY;
        this.lastHorizontalVelocity = actualDeltaXZ;
    }

    private double predictVertical(Player player, PlayerData data, boolean isOnGround, boolean isServerGround) {
        double lastY = data.getLastDeltaY();

        if (this.wasOnGround) {
            if (!isServerGround && data.getDeltaY() > 0.0) {
                double jumpVel = JUMP_IMPULSE;
                int jumpBoost = PhysicsUtils.getPotionLevel(player, PotionEffectType.JUMP_BOOST);
                if (jumpBoost > 0) {
                    jumpVel += JUMP_BOOST_PER_LEVEL * jumpBoost;
                }
                return jumpVel;
            }
            return 0.0;
        }

        double gravity = GRAVITY;
        if (PhysicsUtils.hasEffect(player, PotionEffectType.SLOW_FALLING)) {
            gravity = SLOW_FALLING_GRAVITY;
        }

        int levitation = PhysicsUtils.getPotionLevel(player, PotionEffectType.LEVITATION);
        if (levitation > 0) {
            return (lastY + (0.05 * levitation - lastY) * 0.2);
        }

        if (data.isInLiquid()) {
            gravity = LIQUID_GRAVITY;
            if (player.isSwimming()) {
                return lastY * 0.8 - gravity;
            }
            return (lastY - gravity) * 0.8;
        }

        double predicted = (lastY - gravity) * AIR_DRAG_Y;

        if (predicted < TERMINAL_VELOCITY) {
            predicted = TERMINAL_VELOCITY;
            this.reachedTerminal = true;
            this.ticksAtTerminal++;
        }

        if (predicted > MAX_UPWARD_VELOCITY) {
            predicted = MAX_UPWARD_VELOCITY;
        }

        return predicted;
    }

    private double predictHorizontal(Player player, PlayerData data, boolean isOnGround, boolean isServerGround) {
        double lastXZ = data.getLastDeltaXZ();
        float slipperiness = getSlipperiness(player, data);

        double drag;
        double accel;

        if (this.wasOnGround) {
            drag = slipperiness * AIR_DRAG_XZ;
            double baseAccel = BASE_WALK_ACCEL;
            baseAccel = PhysicsUtils.getBaseSpeed(player, (float) baseAccel);

            if (player.isSprinting()) baseAccel *= SPRINT_MULTIPLIER;
            if (player.isSneaking()) baseAccel *= SNEAK_MULTIPLIER;

            double slipFactor = Math.pow(DEFAULT_SLIPPERINESS / slipperiness, 3);
            accel = baseAccel * 0.16277136 * slipFactor;
        } else {
            drag = AIR_DRAG_XZ;
            accel = player.isSprinting() ? AIR_ACCEL_SPRINT : AIR_ACCEL;
        }

        double predicted = lastXZ * drag + accel;

        if (this.wasOnGround && !isServerGround && data.getDeltaY() > 0.1 && player.isSprinting()) {
            predicted += SPRINT_JUMP_BOOST;
        }

        if (data.isInLiquid()) predicted *= LIQUID_MULTIPLIER;
        if (data.isInWeb()) predicted *= COBWEB_MULTIPLIER;
        if (data.isOnClimbable()) predicted = Math.min(predicted, 0.15);

        return predicted;
    }

    /**
     * UPDATED: Scans the player's footprint for the slippery-est block.
     * Prevents false positives when on the edge of Ice/Slime.
     */
    private float getSlipperiness(Player player, PlayerData data) {
        Location loc = data.getLastLocation();
        if (loc == null) return DEFAULT_SLIPPERINESS;

        // Instead of checking just one block, check 9 blocks in a 0.6x0.6 area
        // We take the MAX friction (most slippery) to benefit the player (leniency)
        float maxFriction = DEFAULT_SLIPPERINESS;

        double startX = loc.getX() - 0.3;
        double startZ = loc.getZ() - 0.3;

        // Scan 3x3 footprint
        for (double x = startX; x <= startX + 0.6; x += 0.3) {
            for (double z = startZ; z <= startZ + 0.6; z += 0.3) {
                // Check block below
                Block block = new Location(loc.getWorld(), x, loc.getY() - 1.0, z).getBlock();
                float friction = PhysicsUtils.getFriction(block);

                if (friction > maxFriction) {
                    maxFriction = friction;
                }
            }
        }

        // Soul Speed logic
        if (maxFriction < 0.5f) { // Soul sand
            int soulSpeed = PhysicsUtils.getSoulSpeedLevel(player);
            if (soulSpeed > 0) {
                maxFriction = DEFAULT_SLIPPERINESS;
            }
        }

        return maxFriction;
    }

    public boolean isHovering(double actualDeltaY, int minAirTicks) {
        return this.airTicks >= minAirTicks &&
                Math.abs(actualDeltaY) < 0.005 &&
                !this.reachedTerminal;
    }

    public boolean isAirJump(double actualDeltaY, double lastDeltaY) {
        return this.airTicks > 5 && lastDeltaY < 0.0 && actualDeltaY > 0.1;
    }

    public boolean isDefyingGravity(double actualDeltaY, double tolerance) {
        if (this.airTicks < 5) return false;
        return actualDeltaY > this.predictedDeltaY + tolerance;
    }

    public boolean isSpeedViolation(double actualDeltaXZ, double tolerance) {
        return actualDeltaXZ > this.predictedDeltaXZ + tolerance;
    }

    public double getFlyDivergenceScore() {
        double score = 0.0;
        if (this.divergenceY > 0.05) score += this.divergenceY * 10.0;
        if (this.airTicks > 20 && Math.abs(this.lastVerticalVelocity) < 0.01) score += 2.0;
        score += this.cumulativeDivergenceY * 0.5;
        return score;
    }

    public double getSpeedDivergenceScore() {
        double score = 0.0;
        if (this.divergenceXZ > 0.01) score += this.divergenceXZ * 15.0;
        score += this.cumulativeDivergenceXZ * 0.3;
        return score;
    }

    public double getPredictedDeltaY() { return predictedDeltaY; }
    public double getPredictedDeltaXZ() { return predictedDeltaXZ; }
    public double getDivergenceY() { return divergenceY; }
    public double getDivergenceXZ() { return divergenceXZ; }
    public double getCumulativeDivergenceY() { return cumulativeDivergenceY; }
    public double getCumulativeDivergenceXZ() { return cumulativeDivergenceXZ; }
    public int getAirTicks() { return airTicks; }
    public int getGroundTicks() { return groundTicks; }
    public boolean isReachedTerminal() { return reachedTerminal; }
    public int getTicksAtTerminal() { return ticksAtTerminal; }
    public boolean wasOnGround() { return wasOnGround; }
    public boolean wasJumping() { return wasJumping; }
}