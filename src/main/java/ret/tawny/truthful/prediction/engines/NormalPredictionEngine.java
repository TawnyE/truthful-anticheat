package ret.tawny.truthful.prediction.engines;

import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.PhysicsConstants;


public final class NormalPredictionEngine {

    private static final double JUMP_BOOST_PER_LEVEL = 0.1D;
    private static final double HEAD_HIT_MIN_Y = -PhysicsConstants.GRAVITY * PhysicsConstants.AIR_DRAG_Y;
    private static final double SLOW_FALLING_MIN_Y = -0.01D * PhysicsConstants.AIR_DRAG_Y;
    private static final double CLIMB_MAX_UP = 0.2D;
    private static final double CLIMB_MAX_DOWN = -0.15D;
    private static final double WEB_CLAMP = 0.05D;
    private static final double HONEY_WALL_MAX_FALL = -0.05D;
    private static final double BUBBLE_UP = 0.7D;
    private static final double BUBBLE_DOWN = -0.49D;
    private static final double EPSILON = 1.0E-4D;

    private final PlayerData data;

    private double predictedVertical;
    private double minVertical;
    private double maxVertical;

    private double predictedHorizontal;
    private double predictedDeltaX;
    private double predictedDeltaZ;

    public NormalPredictionEngine(PlayerData data) {
        this.data = data;
    }

    public void predict(double lastHorizontal, double lastDeltaY) {
        this.minVertical = Double.POSITIVE_INFINITY;
        this.maxVertical = Double.NEGATIVE_INFINITY;

        boolean onGround = data.isServerGround() || data.isClientGround();
        boolean inLiquid = data.isInLiquid();
        boolean onClimbable = data.isOnClimbable();
        boolean inWeb = data.isInWeb();
        boolean underBlock = data.isUnderBlock();

        int levitation = data.getPotionLevel(PotionEffectType.LEVITATION);
        int slowFalling = data.getPotionLevel(PotionEffectType.SLOW_FALLING);
        int jumpBoost = data.getPotionLevel(PotionEffectType.JUMP_BOOST);

        double freeFall = (lastDeltaY - PhysicsConstants.GRAVITY) * PhysicsConstants.AIR_DRAG_Y;
        allow(freeFall);

        if (slowFalling > 0) {
            double slowFall = (lastDeltaY - PhysicsConstants.SLOW_FALLING_GRAVITY) * PhysicsConstants.AIR_DRAG_Y;
            allow(slowFall);
            allow(Math.max(slowFall, SLOW_FALLING_MIN_Y));
        }

        if (levitation > 0) {
            double target = 0.05D * levitation;
            double levY = lastDeltaY + (target - lastDeltaY) * 0.2D;
            allow(levY * PhysicsConstants.AIR_DRAG_Y);
        }


        int airTicks = data.getAirTicks();
        int lastAirTicks = data.getPositionTracker().getLastAirTicks();
        boolean wasRecentlyOnGround = onGround
                || airTicks <= 1
                || (lastAirTicks == 0 && airTicks <= 2);

        if (wasRecentlyOnGround) {
            allow(0.0D);
            double jumpImpulse = PhysicsConstants.JUMP_IMPULSE + jumpBoost * JUMP_BOOST_PER_LEVEL;
            allow(jumpImpulse);
        }

        int ticksSinceSlime = data.getTicksTracked() - data.getLastSlimeTick();
        if (ticksSinceSlime >= 0 && ticksSinceSlime <= 3) {
            allow(Math.abs(lastDeltaY));
            allow(0.0D);
        }

        if (underBlock && lastDeltaY > 0.0D) {
            allow(0.0D);
            allow(HEAD_HIT_MIN_Y);
        }

        if (onClimbable) {
            allow(CLIMB_MAX_UP);
            allow(CLIMB_MAX_DOWN);
            allow(0.0D);
        }

        if (inWeb) {
            allow(freeFall * 0.05D);
            allow(WEB_CLAMP);
            allow(-WEB_CLAMP);
        }

        if (inLiquid) {
            double waterFall = (lastDeltaY - 0.04D) * PhysicsConstants.AIR_DRAG_Y;
            double waterJump = (lastDeltaY + 0.04D) * PhysicsConstants.AIR_DRAG_Y;
            allow(waterFall);
            allow(waterJump);
            allow(0.04D);
            allow(-0.5D);
            allow(BUBBLE_UP);
            allow(BUBBLE_DOWN);
        }

        Vector queued = data.getVelocities().getQueuedVelocityVector();
        if (queued != null && queued.getY() != 0.0D) {
            allow(queued.getY());
            allow(freeFall + queued.getY());
        }

        if (lastDeltaY < 0.0D && lastDeltaY > HONEY_WALL_MAX_FALL - EPSILON) {
            allow(HONEY_WALL_MAX_FALL);
        }

        if (minVertical < PhysicsConstants.TERMINAL_VELOCITY) {
            minVertical = PhysicsConstants.TERMINAL_VELOCITY;
        }

        double expand = inLiquid || inWeb || onClimbable ? 0.02D : 0.006D;
        minVertical -= expand;
        maxVertical += expand;

        predictedVertical = freeFall;
        if (predictedVertical < minVertical) predictedVertical = minVertical;
        else if (predictedVertical > maxVertical) predictedVertical = maxVertical;

        double drag = onGround ? 0.546D : PhysicsConstants.AIR_DRAG_XZ;
        predictedHorizontal = lastHorizontal * drag;
        if (lastHorizontal > 0.001D) {
            double ratio = predictedHorizontal / lastHorizontal;
            predictedDeltaX = data.getLastDeltaX() * ratio;
            predictedDeltaZ = data.getLastDeltaZ() * ratio;
        } else {
            predictedDeltaX = 0.0D;
            predictedDeltaZ = 0.0D;
        }
    }

    private void allow(double candidate) {
        if (candidate < minVertical) minVertical = candidate;
        if (candidate > maxVertical) maxVertical = candidate;
    }

    public double verticalDeviation(double actualDeltaY) {
        if (actualDeltaY < minVertical) return minVertical - actualDeltaY;
        if (actualDeltaY > maxVertical) return actualDeltaY - maxVertical;
        return 0.0D;
    }

    public boolean verticalInRange(double actualDeltaY) {
        return actualDeltaY >= minVertical && actualDeltaY <= maxVertical;
    }

    public double getMinVertical() { return minVertical; }
    public double getMaxVertical() { return maxVertical; }
    public double getPredictedVertical() { return predictedVertical; }
    public double getPredictedHorizontal() { return predictedHorizontal; }
    public double getPredictedDeltaX() { return predictedDeltaX; }
    public double getPredictedDeltaZ() { return predictedDeltaZ; }
}
