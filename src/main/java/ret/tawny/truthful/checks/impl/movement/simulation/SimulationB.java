package ret.tawny.truthful.checks.impl.movement.simulation;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.checks.impl.movement.MovementCheckSupport;
import ret.tawny.truthful.data.ExemptionType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.BlockPropertyRegistry;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'B', type = CheckType.SIMULATION)
public final class SimulationB extends Check {

    private static final double ELYTRA_DRAG_XZ = 0.99D;
    private static final double ELYTRA_DRAG_Y  = 0.98D;
    private static final double GRAVITY        = 0.08D;
    private static final double LIFT_FACTOR    = 0.1D;
    private static final double THRUST_FACTOR  = 0.04D;
    private static final double THRUST_Y_MULT  = 3.2D;
    private static final double STEER_FACTOR   = 0.1D;

    private static final double FIREWORK_IMPULSE    = 0.1D;
    private static final double FIREWORK_TARGET     = 1.5D;
    private static final double FIREWORK_CORRECTION = 0.5D;

    private static final float SHARP_TURN_YAW_THRESHOLD = 25.0F;
    private static final float PITCH_SNAP_THRESHOLD     = 18.0F;

    private static final double H_TOLERANCE   = 0.06D;
    private static final double V_TOLERANCE   = 0.04D;

    private static final double MAX_NATURAL_XZ = 3.8D;
    private static final double MAX_BOOST_XZ   = 5.8D;

    private enum Tag {
        FIREWORK_BOOST, LEVEL_FLIGHT, CLIMBING, DIVING,
        LIFT_ACTIVE, SHARP_TURN,
        GROUND_TOUCH, WALL_TOUCH, LIQUID_TOUCH, UNDER_BLOCK,
        ILLEGAL_HOVER, PREDICT_H, PREDICT_V, SPEED_CAP
    }

    private static final class State {
        float buffer;
        int hoverTicks;
        int hViolTicks;
        int vViolTicks;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;

        final PlayerData data = wrapper.getPlayerData();
        if (data == null) return;

        final State st = states.computeIfAbsent(data.getPlayer().getUniqueId(), k -> new State());

        if (!data.isGliding()) {
            st.buffer = Math.max(0f, st.buffer - 0.5f);
            st.hoverTicks = 0;
            return;
        }

        if (MovementCheckSupport.skipForPrediction(data)) return;

        final double deltaXZ     = data.getDeltaXZ();
        final double lastDeltaXZ = data.getLastDeltaXZ();
        final double deltaY      = data.getDeltaY();
        final double lastDeltaY  = data.getLastDeltaY();
        final float pitch        = data.getPitch();
        final float yaw          = data.getYaw();
        final int ticksNow       = data.getTicksTracked();
        final int ticksSinceFirework = ticksNow - data.getLastFireworkTick();

        final EnumSet<Tag> tags = buildTags(data, ticksNow, pitch, ticksSinceFirework, lastDeltaY);

        double cap = tags.contains(Tag.FIREWORK_BOOST) ? MAX_BOOST_XZ : MAX_NATURAL_XZ;
        if (deltaXZ > cap) {
            double dev = deltaXZ - cap;
            flag(data, String.format("Area Fail (ElytraSpeed) XZ=%.3f cap=%.3f", deltaXZ, cap), 2.0 + dev * 10.0);
            return;
        }

        if (!tags.contains(Tag.FIREWORK_BOOST) && tags.contains(Tag.LEVEL_FLIGHT) && deltaY >= -0.001D && deltaXZ > 0.3D) {
            st.hoverTicks++;
            if (st.hoverTicks >= 20) {
                flag(data, String.format("Area Fail (ElytraHover) Maintaining level height Y=%.4f pitch=%.1f hoverTicks=%d",
                        deltaY, pitch, st.hoverTicks), 3.0 + st.hoverTicks);
                st.hoverTicks = 10;
                return;
            }
        } else {
            st.hoverTicks = Math.max(0, st.hoverTicks - 1);
        }

        if (canPredict(tags)) {
            PredictionResult pred = runExactPrediction(data, yaw, pitch, lastDeltaXZ, lastDeltaY,
                    data.getLastDeltaX(), data.getLastDeltaZ(), tags, ticksSinceFirework);

            double hTol = H_TOLERANCE + (tags.contains(Tag.SHARP_TURN) ? 0.25D : 0.08D);
            double vTol = V_TOLERANCE + (tags.contains(Tag.SHARP_TURN) ? 0.20D : 0.06D);

            if (pred.hDiff > hTol) {
                st.hViolTicks++;
                if (st.hViolTicks >= 4) {
                    flag(data, String.format("Area Fail (ElytraH) XZ=%.3f predXZ=%.3f hDiff=%.3f", deltaXZ, pred.predictedH, pred.hDiff), 2.0 + pred.hDiff * 8.0);
                    st.hViolTicks = 2;
                }
            } else {
                st.hViolTicks = Math.max(0, st.hViolTicks - 1);
            }

            if (pred.vDiff > vTol && Math.abs(deltaY) > 0.05D) {
                st.vViolTicks++;
                if (st.vViolTicks >= 4) {
                    flag(data, String.format("Area Fail (ElytraV) Y=%.3f predY=%.3f vDiff=%.3f", deltaY, pred.predictedV, pred.vDiff), 2.0 + pred.vDiff * 8.0);
                    st.vViolTicks = 2;
                }
            } else {
                st.vViolTicks = Math.max(0, st.vViolTicks - 1);
            }
        }
    }

    private PredictionResult runExactPrediction(
            PlayerData data, float yaw, float pitch,
            double lastXZ, double lastDeltaY,
            double lastMotionX, double lastMotionZ,
            EnumSet<Tag> tags, int ticksSinceFirework) {

        double pitchRad = Math.toRadians(pitch);
        double yawRad   = Math.toRadians(yaw);
        double sinYaw   = Math.sin(yawRad);
        double cosYaw   = Math.cos(yawRad);
        double sinPitch = Math.sin(pitchRad);
        double cosPitch = Math.cos(pitchRad);

        double lookX = -sinYaw * cosPitch;
        double lookY = -sinPitch;
        double lookZ =  cosYaw * cosPitch;
        double horizLook = Math.hypot(lookX, lookZ);

        double mX = lastMotionX;
        double mY = lastDeltaY;
        double mZ = lastMotionZ;

        mY -= GRAVITY;

        if (mY < 0.0D && horizLook > 0.0D) {
            double cosPitchSq = cosPitch * cosPitch;
            double lift = mY * -LIFT_FACTOR * cosPitchSq;
            mY += lift;
            mX += (lookX * lift) / horizLook;
            mZ += (lookZ * lift) / horizLook;
        }

        if (horizLook > 0.0D) {
            if (lookY < 0.0D) {
                double dive = lastXZ * -lookY * THRUST_FACTOR;
                mY -= dive * THRUST_Y_MULT;
                mX += (lookX * dive) / horizLook;
                mZ += (lookZ * dive) / horizLook;
            } else if (lookY > 0.0D) {
                double climb = lastXZ * lookY * THRUST_FACTOR;
                mY += climb * THRUST_Y_MULT;
                mX -= (lookX * climb) / horizLook;
                mZ -= (lookZ * climb) / horizLook;
            }
        }

        if (horizLook > 0.0D) {
            mX += ((lookX / horizLook) * lastXZ - mX) * STEER_FACTOR;
            mZ += ((lookZ / horizLook) * lastXZ - mZ) * STEER_FACTOR;
        }

        if (tags.contains(Tag.FIREWORK_BOOST)) {
            mX += lookX * FIREWORK_IMPULSE + (lookX * FIREWORK_TARGET - mX) * FIREWORK_CORRECTION;
            mY += lookY * FIREWORK_IMPULSE + (lookY * FIREWORK_TARGET - mY) * FIREWORK_CORRECTION;
            mZ += lookZ * FIREWORK_IMPULSE + (lookZ * FIREWORK_TARGET - mZ) * FIREWORK_CORRECTION;
        }

        mX *= ELYTRA_DRAG_XZ;
        mY *= ELYTRA_DRAG_Y;
        mZ *= ELYTRA_DRAG_XZ;

        PredictionResult res = new PredictionResult();
        res.predictedH = Math.hypot(mX, mZ);
        res.predictedV = mY;
        res.hDiff = data.getDeltaXZ() - res.predictedH;
        res.vDiff = Math.abs(data.getDeltaY() - mY);
        return res;
    }

    private EnumSet<Tag> buildTags(PlayerData data, int ticksNow, float pitch, int ticksSinceFirework, double lastDeltaY) {
        EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);

        if (data.isExempt(ExemptionType.ELYTRA_BOOST) || (ticksSinceFirework >= 0 && ticksSinceFirework <= 20)) {
            tags.add(Tag.FIREWORK_BOOST);
        }

        if (pitch > 5.0f) tags.add(Tag.DIVING);
        else if (pitch < -15.0f) tags.add(Tag.CLIMBING);
        else tags.add(Tag.LEVEL_FLIGHT);

        if (lastDeltaY < -0.15D) tags.add(Tag.LIFT_ACTIVE);
        if (Math.abs(data.getDeltaYaw()) > SHARP_TURN_YAW_THRESHOLD) tags.add(Tag.SHARP_TURN);

        if (data.isServerGround() || data.isClientGround()) tags.add(Tag.GROUND_TOUCH);
        if (data.isUnderBlock()) tags.add(Tag.UNDER_BLOCK);
        if (data.isInLiquid()) tags.add(Tag.LIQUID_TOUCH);
        if (isNearHorizontalCollision(data)) tags.add(Tag.WALL_TOUCH);

        return tags;
    }

    private boolean canPredict(EnumSet<Tag> tags) {
        return !tags.contains(Tag.WALL_TOUCH) && !tags.contains(Tag.GROUND_TOUCH)
                && !tags.contains(Tag.LIQUID_TOUCH) && !tags.contains(Tag.UNDER_BLOCK);
    }

    private boolean isNearHorizontalCollision(PlayerData data) {
        double x = data.getX(), y = data.getY(), z = data.getZ(), r = 0.36D;
        return hasSolid(data, x + r, y + 0.20D, z) || hasSolid(data, x - r, y + 0.20D, z)
                || hasSolid(data, x, y + 0.20D, z + r) || hasSolid(data, x, y + 0.20D, z - r);
    }

    private boolean hasSolid(PlayerData data, double x, double y, double z) {
        return BlockPropertyRegistry.isSolid(data.getWorldCache().getBlockState((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
    }

    private static final class PredictionResult {
        double predictedH, predictedV, hDiff, vDiff;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }
}