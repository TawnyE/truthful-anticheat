package ret.tawny.truthful.checks.impl.movement.simulation;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.Truthful;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@CheckData(order = 'B', type = CheckType.SIMULATION)
public final class SimulationB extends Check {

    // ─── Elytra physics constants (vanilla-accurate) ──────────────────────────
    private static final double ELYTRA_DRAG_XZ      = 0.99D;   // per-tick horizontal drag
    private static final double ELYTRA_DRAG_Y        = 0.98D;   // per-tick vertical drag
    private static final double GRAVITY              = 0.08D;
    // Lift: converts downward Y into forward XZ (only when falling, looking horizontally)
    private static final double LIFT_FACTOR          = 0.1D;
    // Thrust: converts XZ speed into vertical change when diving/climbing
    private static final double THRUST_FACTOR        = 0.04D;
    private static final double THRUST_Y_MULT        = 3.2D;
    // Steering: how strongly look direction bends the current XZ vector
    private static final double STEER_FACTOR         = 0.1D;
    // Firework boost: directional impulse each tick it is active
    private static final double FIREWORK_IMPULSE     = 0.1D;
    private static final double FIREWORK_TARGET      = 1.5D;
    private static final double FIREWORK_CORRECTION  = 0.5D;

    // ─── Detection thresholds ─────────────────────────────────────────────────
    // Horizontal/vertical deviation tolerance BEFORE it counts as suspicious
    private static final double H_TOLERANCE          = 0.065D;
    private static final double V_TOLERANCE          = 0.055D;
    private static final double DIR_TOLERANCE        = 0.12D;
    // Additional tolerance for known-noisy situations
    private static final double SHARP_TURN_H_GRACE   = 0.12D;
    private static final double SHARP_TURN_DIR_GRACE = 0.18D;
    private static final double PITCH_SNAP_V_GRACE   = 0.12D;
    private static final double PITCH_SNAP_DIR_GRACE = 0.10D;
    private static final double SLOW_FALL_V_GRACE    = 0.15D;

    // ─── VL / buffer system ───────────────────────────────────────────────────
    // Gate 1: physicsVL — per-tick violation level (decays ~20 ticks to halve from 100)
    private static final double VL_DECAY_MULT        = 0.990D;
    private static final double VL_DECAY_CONST       = 0.012D;
    private static final double VL_FLAG_THRESHOLD    = 50.0D;   // must exceed before flag
    // Gate 2: driftScore — cumulative drift (physicsOffset equivalent)
    private static final double DRIFT_FLAG_THRESHOLD = 0.50D;   // must also exceed
    private static final double DRIFT_DECAY          = 0.002D;  // per-tick slow decay

    // ─── Speed / energy caps ──────────────────────────────────────────────────
    private static final double MAX_NATURAL_XZ_SPEED = 3.8D;
    private static final double MAX_BOOST_XZ_SPEED   = 6.0D;
    private static final double MAX_NATURAL_GAIN     = 0.20D;
    private static final double SHARP_TURN_YAW_THOLD = 25.0D;
    private static final double TURN_GAIN_GRACE      = 0.30D;
    private static final double STALL_PITCH          = -25.0D;
    private static final double STALL_SPEED_THOLD    = 1.8D;
    private static final int    ENERGY_WINDOW        = 20;
    private static final double MAX_ENERGY_RATE      = 0.85D;

    // ─── Grace windows ────────────────────────────────────────────────────────
    private static final int BOOST_GRACE_TICKS       = 36;
    private static final int VELOCITY_GRACE_TICKS    = 8;

    // ─── Tags (all original tags preserved + new prediction tags) ────────────
    private enum Tag {
        FIREWORK_BOOST, BOOST_START, BOOST_ACTIVE, BOOST_GRACE,
        SLOW_FALLING, RIPTIDE_LAUNCH, VELOCITY, GLIDE_INIT, TELEPORT_GRACE,
        DIVING, CLIMBING, LEVEL_FLIGHT, LIFT_ACTIVE, SHARP_TURN, PITCH_SNAP,
        GROUND_TOUCH, WALL_TOUCH, LIQUID_TOUCH, UNDER_BLOCK,
        SPEED_CAP, STALL, ENERGY, SPURIOUS_GAIN, LOW_DRAG,
        PREDICT_H, PREDICT_V, DIRECTION,
        ILLEGAL_AIR_JUMP
    }

    // ─── Per-player state ─────────────────────────────────────────────────────
    private static final class State {
        // Original counters (semantics preserved)
        int   stallTicks;
        int   lowDragTicks;
        int   predictHTicks;
        int   predictVTicks;
        int   directionTicks;
        // Energy sampling (original)
        final Deque<Double> energySamples = new ArrayDeque<>(ENERGY_WINDOW + 1);

        // NEW: Intave-style dual-gate violation tracking
        double physicsVL;    // Gate 1: per-tick scaled violation
        double driftScore;   // Gate 2: cumulative drift accumulator

        // Consecutive violation tick counters for each sub-check
        int hViolTicks;
        int vViolTicks;
        int dirViolTicks;
    }

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    // ─── Entry point ──────────────────────────────────────────────────────────
    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;
        final PlayerData data = wrapper.getPlayerData();
        if (data == null) return;

        final State st = states.computeIfAbsent(data.getPlayer().getUniqueId(), k -> new State());

        if (!data.isGliding()) {
            // Decay both gates when not gliding
            st.physicsVL  = Math.max(0, st.physicsVL * VL_DECAY_MULT - VL_DECAY_CONST);
            st.driftScore = Math.max(0, st.driftScore - DRIFT_DECAY);
            st.energySamples.clear();
            resetTransientCounters(st);
            return;
        }

        if (data.isTeleportPending() || data.getTicksSinceTeleport() < 3) {
            st.physicsVL  = Math.max(0, st.physicsVL  * 0.5);
            st.driftScore = Math.max(0, st.driftScore * 0.5);
            st.energySamples.clear();
            resetTransientCounters(st);
            return;
        }

        if (MovementCheckSupport.skipForPrediction(data)) return;
        if (MovementCheckSupport.isInGraceWindow(data)) return;

        final double deltaXZ     = data.getDeltaXZ();
        final double lastDeltaXZ = data.getLastDeltaXZ();
        final double deltaY      = data.getDeltaY();
        final double lastDeltaY  = data.getLastDeltaY();
        final float  pitch       = data.getPitch();
        final float  yaw         = data.getYaw();
        final int    glideTicks  = data.getMovementState().getGlideTicks();
        final int    ticksNow    = data.getTicksTracked();
        final float  deltaYaw    = data.getPositionTracker().getDeltaYaw();
        final float  deltaPitch  = data.getPositionTracker().getDeltaPitch();
        final int    ticksSinceFirework = ticksNow - data.getLastFireworkTick();
        final int    ticksSinceVelocity = ticksNow - data.getLastVelocityTick();

        final EnumSet<Tag> tags = buildTags(data, ticksNow, glideTicks, pitch, deltaYaw, deltaPitch,
                ticksSinceFirework, ticksSinceVelocity, lastDeltaY);

        // Full grace: reset both gates and return
        if (tags.contains(Tag.GLIDE_INIT) || tags.contains(Tag.TELEPORT_GRACE)
                || tags.contains(Tag.RIPTIDE_LAUNCH) || tags.contains(Tag.VELOCITY)) {
            st.physicsVL  = Math.max(0, st.physicsVL * 0.7);
            st.driftScore = Math.max(0, st.driftScore * 0.5);
            st.energySamples.clear();
            resetTransientCounters(st);
            return;
        }

        // ── Check 1: Illegal air jump (instant flag, no gate) ─────────────────
        if (canStrictlyPredict(tags) && !tags.contains(Tag.LIFT_ACTIVE)
                && deltaY > 0.0D && lastDeltaY <= 0.0D && pitch >= -10.0f) {
            tags.add(Tag.ILLEGAL_AIR_JUMP);
            flag(data, String.format("Area Fail (ElytraJump) Y=%.4f lastY=%.4f pitch=%.1f tags=%s",
                    deltaY, lastDeltaY, pitch, tags));
            return;
        }

        // ── Check 2: Hard speed cap ───────────────────────────────────────────
        double cap = tags.contains(Tag.FIREWORK_BOOST) ? MAX_BOOST_XZ_SPEED : MAX_NATURAL_XZ_SPEED;
        if (tags.contains(Tag.SLOW_FALLING)) cap = Math.min(cap, 1.2D);
        if (tags.contains(Tag.WALL_TOUCH) || tags.contains(Tag.GROUND_TOUCH)) cap += 0.35D;

        if (deltaXZ > cap + 0.05D) {
            tags.add(Tag.SPEED_CAP);
            double severity = 2.0D + (deltaXZ - cap) * 7.0D;
            // Speed cap is serious; direct flag without dual-gate
            flag(data, String.format("Area Fail (SpeedCap) XZ=%.3f cap=%.3f tags=%s", deltaXZ, cap, tags));
            return;
        }

        // ── Check 3: Spurious XZ gain (original SpuriousGain, tightened) ─────
        if (canStrictlyPredict(tags) && glideTicks > 10) {
            double effectiveMaxGain = MAX_NATURAL_GAIN;
            if (tags.contains(Tag.SHARP_TURN)) {
                double turnFactor = Math.min(1.0D, (deltaYaw - SHARP_TURN_YAW_THOLD) / 65.0D);
                effectiveMaxGain += TURN_GAIN_GRACE * turnFactor;
            }
            double gain = deltaXZ - (lastDeltaXZ * ELYTRA_DRAG_XZ);
            if (gain > effectiveMaxGain) {
                tags.add(Tag.SPURIOUS_GAIN);
                addViolation(st, 1.5D + (gain - effectiveMaxGain) * 8.0D, gain - effectiveMaxGain);
            }
        }

        // ── Check 4: Low drag (level flight XZ not decaying correctly) ────────
        if (canStrictlyPredict(tags) && !tags.contains(Tag.LIFT_ACTIVE) && !tags.contains(Tag.SHARP_TURN)
                && tags.contains(Tag.LEVEL_FLIGHT)) {
            double expectedMax = lastDeltaXZ * ELYTRA_DRAG_XZ + 0.05D;
            if (deltaXZ > expectedMax + 0.06D) {
                st.lowDragTicks++;
                if (st.lowDragTicks >= 4) {
                    tags.add(Tag.LOW_DRAG);
                    addViolation(st, 1.0D + (deltaXZ - expectedMax) * 5.0D, deltaXZ - expectedMax);
                }
            } else {
                st.lowDragTicks = 0;
            }
        } else {
            st.lowDragTicks = 0;
        }

        // ── Check 5: Stall (climbing too fast) ────────────────────────────────
        if (canStrictlyPredict(tags) && tags.contains(Tag.CLIMBING) && deltaXZ > STALL_SPEED_THOLD) {
            st.stallTicks++;
            if (st.stallTicks >= 3) {
                tags.add(Tag.STALL);
                addViolation(st, 1.5D + (deltaXZ - STALL_SPEED_THOLD) * 4.0D, deltaXZ - STALL_SPEED_THOLD);
            }
        } else {
            st.stallTicks = 0;
        }

        // ── Check 6: Energy rate ──────────────────────────────────────────────
        if (canStrictlyPredict(tags)) {
            double speedSq = deltaXZ * deltaXZ + deltaY * deltaY;
            st.energySamples.addLast(speedSq);
            if (st.energySamples.size() > ENERGY_WINDOW) st.energySamples.pollFirst();

            if (st.energySamples.size() == ENERGY_WINDOW) {
                double energyRate = (speedSq - st.energySamples.peekFirst()) / ENERGY_WINDOW;
                if (energyRate > MAX_ENERGY_RATE) {
                    tags.add(Tag.ENERGY);
                    addViolation(st, 1.0D + (energyRate - MAX_ENERGY_RATE) * 3.0D,
                            energyRate - MAX_ENERGY_RATE);
                }
            }
        } else {
            st.energySamples.clear();
        }

        // ── Check 7: Exact vector prediction ───────────────────
        if (canStrictlyPredict(tags)) {
            PredictionResult pred = runExactPrediction(data, yaw, pitch, lastDeltaXZ, lastDeltaY,
                    data.getLastDeltaX(), data.getLastDeltaZ(), tags, ticksSinceFirework);

            double hTol   = H_TOLERANCE;
            double vTol   = V_TOLERANCE;
            double dirTol = DIR_TOLERANCE;
            if (tags.contains(Tag.SHARP_TURN)) { hTol += SHARP_TURN_H_GRACE;   dirTol += SHARP_TURN_DIR_GRACE; }
            if (tags.contains(Tag.PITCH_SNAP)) { vTol += PITCH_SNAP_V_GRACE;   dirTol += PITCH_SNAP_DIR_GRACE; }
            if (tags.contains(Tag.SLOW_FALLING)) vTol += SLOW_FALL_V_GRACE;

            // Horizontal deviation
            if (pred.hDiff > hTol) {
                st.hViolTicks++;
                if (st.hViolTicks >= 3) {
                    tags.add(Tag.PREDICT_H);
                    addViolation(st, 1.0D + pred.hDiff * 5.0D, pred.hDiff);
                }
            } else {
                st.hViolTicks = Math.max(0, st.hViolTicks - 1);
            }

            // Vertical deviation
            if (pred.vDiff > vTol && Math.abs(deltaY) > 0.03D) {
                st.vViolTicks++;
                if (st.vViolTicks >= 3) {
                    tags.add(Tag.PREDICT_V);
                    addViolation(st, 1.0D + pred.vDiff * 5.0D, pred.vDiff);
                }
            } else {
                st.vViolTicks = Math.max(0, st.vViolTicks - 1);
            }

            // Directional vector deviation
            if (pred.vecDiff > dirTol && deltaXZ > 0.45D && pred.predictedH > 0.25D) {
                st.dirViolTicks++;
                if (st.dirViolTicks >= 4) {
                    tags.add(Tag.DIRECTION);
                    addViolation(st, 1.0D + pred.vecDiff * 3.0D, pred.vecDiff);
                }
            } else {
                st.dirViolTicks = Math.max(0, st.dirViolTicks - 1);
            }

            // ── Dual-gate flag decision ──────────────────────────────────────
            // Both physicsVL > threshold AND driftScore > threshold must be true.
            // Mirrors Intave: physicsVL > highPitchLimit && physicsOffset > 0.7
            if (st.physicsVL > VL_FLAG_THRESHOLD && st.driftScore > DRIFT_FLAG_THRESHOLD) {
                String reason = tags.contains(Tag.PREDICT_H)    ? "ElytraPredictH"
                        : tags.contains(Tag.PREDICT_V)    ? "ElytraPredictV"
                        : tags.contains(Tag.DIRECTION)    ? "ElytraDirection"
                        : tags.contains(Tag.SPURIOUS_GAIN) ? "SpuriousGain"
                        : tags.contains(Tag.LOW_DRAG)     ? "LowDrag"
                        : tags.contains(Tag.STALL)        ? "Stall"
                        : tags.contains(Tag.ENERGY)       ? "Energy"
                        : "Generic";

                flag(data, String.format(
                        "Area Fail (%s) XZ=%.3f predXZ=%.3f Y=%.3f predY=%.3f hDiff=%.3f vDiff=%.3f vecDiff=%.3f vl=%.1f drift=%.3f tags=%s",
                        reason, deltaXZ, pred.predictedH, deltaY, pred.predictedV,
                        pred.hDiff, pred.vDiff, pred.vecDiff,
                        st.physicsVL, st.driftScore, tags));

                // Reset: cap VL to avoid cascading, reduce drift
                st.physicsVL  = Math.min(st.physicsVL, VL_FLAG_THRESHOLD * 0.6);
                st.driftScore = Math.min(st.driftScore, DRIFT_FLAG_THRESHOLD * 0.4);
                return;
            }
        } else {
            resetTransientCounters(st);
        }

        // ── Valid tick: decay both gates ──────────────────────────────────────
        st.physicsVL  = Math.max(0, st.physicsVL  * VL_DECAY_MULT - VL_DECAY_CONST);
        st.driftScore = Math.max(0, st.driftScore - DRIFT_DECAY);
    }


    private void addViolation(final State st, final double vlIncrease, final double rawDeviation) {
        st.physicsVL  = Math.min(200.0D, st.physicsVL + vlIncrease);
        st.driftScore = Math.min(1.0D, st.driftScore + rawDeviation * 0.4D);
    }


    private PredictionResult runExactPrediction(
            final PlayerData data,
            final float yaw, final float pitch,
            final double lastXZ, final double lastDeltaY,
            final double lastMotionX, final double lastMotionZ,
            final EnumSet<Tag> tags, final int ticksSinceFirework) {

        double pitchRad = Math.toRadians(pitch);
        double yawRad   = Math.toRadians(yaw);

        double sinYaw  = Math.sin(yawRad);
        double cosYaw  = Math.cos(yawRad);
        double sinPitch = Math.sin(pitchRad);
        double cosPitch = Math.cos(pitchRad);

        // Exact look vector
        double lookX = -sinYaw * cosPitch;
        double lookY = -sinPitch;
        double lookZ =  cosYaw * cosPitch;
        double horizLook = Math.sqrt(lookX * lookX + lookZ * lookZ);

        double mX = lastMotionX;
        double mY = lastDeltaY;
        double mZ = lastMotionZ;

        // Step 1: Gravity
        mY -= GRAVITY;

        // Step 2: Lift — falling converts into forward thrust
        if (mY < 0.0D && horizLook > 0.0D) {
            double cosPitchSq = cosPitch * cosPitch;
            double lift = mY * -LIFT_FACTOR * cosPitchSq;
            mY += lift;
            mX += (lookX * lift) / horizLook;
            mZ += (lookZ * lift) / horizLook;
        }

        // Step 3: Dive thrust / climb drag — XZ speed converts to vertical
        if (horizLook > 0.0D) {
            if (lookY < 0.0D) {
                // Diving: gain downward speed
                double dive = lastXZ * -lookY * THRUST_FACTOR;
                mY -= dive * THRUST_Y_MULT;
                mX += (lookX * dive) / horizLook;
                mZ += (lookZ * dive) / horizLook;
            } else if (lookY > 0.0D) {
                // Climbing: lose XZ speed, gain height
                double climb = lastXZ * lookY * THRUST_FACTOR;
                mY += climb * THRUST_Y_MULT;
                mX -= (lookX * climb) / horizLook;
                mZ -= (lookZ * climb) / horizLook;
            }
        }

        // Step 4: Aerodynamic steering — bend current vector toward look direction
        if (horizLook > 0.0D) {
            mX += ((lookX / horizLook) * lastXZ - mX) * STEER_FACTOR;
            mZ += ((lookZ / horizLook) * lastXZ - mZ) * STEER_FACTOR;
        }

        // Step 5: Firework boost (directional impulse for up to 5 ticks post-launch)
        if (ticksSinceFirework >= 0 && ticksSinceFirework <= 5) {
            mX += lookX * FIREWORK_IMPULSE + (lookX * FIREWORK_TARGET - mX) * FIREWORK_CORRECTION;
            mY += lookY * FIREWORK_IMPULSE + (lookY * FIREWORK_TARGET - mY) * FIREWORK_CORRECTION;
            mZ += lookZ * FIREWORK_IMPULSE + (lookZ * FIREWORK_TARGET - mZ) * FIREWORK_CORRECTION;
        }

        // Step 6: Drag
        mX *= ELYTRA_DRAG_XZ;
        mY *= ELYTRA_DRAG_Y;
        mZ *= ELYTRA_DRAG_XZ;

        double predictedH = Math.hypot(mX, mZ);

        PredictionResult result = new PredictionResult();
        result.predictedH = predictedH;
        result.predictedV = mY;
        result.hDiff      = data.getDeltaXZ() - predictedH;
        result.vDiff      = Math.abs(data.getDeltaY() - mY);
        result.vecDiff    = Math.hypot(data.getDeltaX() - mX, data.getDeltaZ() - mZ);
        return result;
    }

    // ─── Tag builder ─────────────────────────────────────────────────────────
    private EnumSet<Tag> buildTags(final PlayerData data, final int ticksNow, final int glideTicks,
                                   final float pitch, final float deltaYaw, final float deltaPitch,
                                   final int ticksSinceFirework, final int ticksSinceVelocity,
                                   final double lastDeltaY) {
        final EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);

        if (glideTicks < 8) tags.add(Tag.GLIDE_INIT);
        if (data.getTicksSinceTeleport() < 5 || ticksNow - data.getLastVehicleExitTick() < 10) tags.add(Tag.TELEPORT_GRACE);

        if (data.isExempt(ExemptionType.ELYTRA_BOOST) || (ticksSinceFirework >= 0 && ticksSinceFirework <= BOOST_GRACE_TICKS)) {
            tags.add(Tag.FIREWORK_BOOST);
            if (ticksSinceFirework <= 3)        tags.add(Tag.BOOST_START);
            else if (ticksSinceFirework <= 24)  tags.add(Tag.BOOST_ACTIVE);
            else                                 tags.add(Tag.BOOST_GRACE);
        }

        if (data.getPotionLevel(PotionEffectType.SLOW_FALLING) > 0) tags.add(Tag.SLOW_FALLING);
        if (data.isExempt(ExemptionType.RIPTIDE) || ticksNow - data.getLastRiptideTick() < 15) tags.add(Tag.RIPTIDE_LAUNCH);
        if (data.hasVelocity() || (ticksSinceVelocity >= 0 && ticksSinceVelocity <= VELOCITY_GRACE_TICKS)) tags.add(Tag.VELOCITY);

        if (pitch > 5.0f)                 tags.add(Tag.DIVING);
        else if (pitch < (float) STALL_PITCH) tags.add(Tag.CLIMBING);
        else                               tags.add(Tag.LEVEL_FLIGHT);

        if (lastDeltaY < -0.2D) tags.add(Tag.LIFT_ACTIVE);
        if (deltaYaw > SHARP_TURN_YAW_THOLD) tags.add(Tag.SHARP_TURN);
        if (deltaPitch > 18.0f)              tags.add(Tag.PITCH_SNAP);

        if (data.isServerGround() || data.isClientGround()) tags.add(Tag.GROUND_TOUCH);
        if (data.isUnderBlock())    tags.add(Tag.UNDER_BLOCK);
        if (data.isInLiquid())      tags.add(Tag.LIQUID_TOUCH);
        if (isNearHorizontalCollision(data)) tags.add(Tag.WALL_TOUCH);

        return tags;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    /**
     * Returns true if the current state is clean enough for strict vector prediction.
     * Mirrors Intave's canStrictlyPredict logic.
     */
    private boolean canStrictlyPredict(final EnumSet<Tag> tags) {
        return !tags.contains(Tag.FIREWORK_BOOST)
                && !tags.contains(Tag.VELOCITY)
                && !tags.contains(Tag.WALL_TOUCH)
                && !tags.contains(Tag.GROUND_TOUCH)
                && !tags.contains(Tag.LIQUID_TOUCH)
                && !tags.contains(Tag.UNDER_BLOCK);
    }

    private void resetTransientCounters(final State st) {
        st.stallTicks     = 0;
        st.lowDragTicks   = 0;
        st.hViolTicks     = 0;
        st.vViolTicks     = 0;
        st.dirViolTicks   = 0;
    }

    private boolean isNearHorizontalCollision(final PlayerData data) {
        final double x = data.getX(), y = data.getY(), z = data.getZ(), r = 0.36D;
        return hasSolid(data, x + r, y + 0.20D, z) || hasSolid(data, x - r, y + 0.20D, z)
                || hasSolid(data, x, y + 0.20D, z + r) || hasSolid(data, x, y + 0.20D, z - r)
                || hasSolid(data, x + r, y + 0.85D, z) || hasSolid(data, x - r, y + 0.85D, z)
                || hasSolid(data, x, y + 0.85D, z + r) || hasSolid(data, x, y + 0.85D, z - r);
    }

    private boolean hasSolid(final PlayerData data, final double x, final double y, final double z) {
        return BlockPropertyRegistry.isSolid(data.getWorldCache().getBlockState(
                floor(x), floor(y), floor(z)));
    }

    private int floor(final double v) { int i = (int) v; return v < i ? i - 1 : i; }

    // ─── Result container ─────────────────────────────────────────────────────
    private static final class PredictionResult {
        double predictedH;
        double predictedV;
        double hDiff;
        double vDiff;
        double vecDiff;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }
}
