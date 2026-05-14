package ret.tawny.truthful.checks.impl.movement.simulation;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.checks.impl.movement.MovementCheckSupport;
import ret.tawny.truthful.data.ExemptionType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.PhysicsConstants;
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'A', type = CheckType.SIMULATION)
public final class SimulationA extends Check {

    // ─── Vertical constants ──────────────────────────────────────────────────
    private static final double JUMP_BOOST_PER_LEVEL    = 0.1D;
    private static final double FREEFALL_FROM_ZERO      = -PhysicsConstants.GRAVITY * PhysicsConstants.AIR_DRAG_Y;
    private static final double SLOW_FALL_MIN_Y         = -PhysicsConstants.SLOW_FALLING_GRAVITY * PhysicsConstants.AIR_DRAG_Y;
    private static final double VERTICAL_FLAG_THRESHOLD = 0.75D;

    // ─── Horizontal / moveRelative constants ─────────────────────────────────
    private static final double AIR_DRAG_XZ              = PhysicsConstants.AIR_DRAG_XZ;
    private static final double DEFAULT_SLIPPERINESS     = PhysicsConstants.DEFAULT_SLIPPERINESS;
    private static final double SPRINT_JUMP_BOOST        = 0.2D;
    private static final double MIN_XZ_MOTION            = PhysicsConstants.MIN_MOTION;
    private static final double H_NOISE_FLOOR            = 0.002D;
    private static final float  HORIZONTAL_FLAG_THRESHOLD = 1.0f;

    // Ground speed caps (vanilla physics: accel / (1 - friction) at DEFAULT_SLIPPERINESS)
    private static final double GROUND_BASE_WALK_CAP          = 0.221D;
    private static final double GROUND_BASE_SPRINT_CAP        = 0.287D;
    private static final double GROUND_CAP_LENIENCY           = 0.008D;
    private static final double GROUND_ACCEL_LENIENCY         = 0.015D;
    private static final double GROUND_VECTOR_ACCEL_LENIENCY  = 0.020D;

    // Raw key scale Minecraft applies before normalize: 0.98
    private static final double KEY_SCALE       = 0.98D;
    // Air acceleration friction constants
    private static final double AIR_ACCEL_BASE  = 0.02D;
    private static final double AIR_ACCEL_SPRINT = 0.026D;

    // Sprint-jump momentum stays active for this many air ticks after takeoff.
    // Vanilla sprint-jump carries the 0.2 boost through the full jump arc.
    // Without this, AS and H_DRIFT false-flag mid-jump when SPRINT_JUMP tag expires.
    private static final int SPRINT_JUMP_GRACE_TICKS = 8;

    // ─── Tags ─────────────────────────────────────────────────────────────────
    private enum Tag {
        JUMP, JUMP_START, JUMPING, IN_AIR,
        LANDING, LANDING_GRACE,
        HEAD_HIT,
        STEP_Y, STEP_DOWN,
        LADDER, LIQUID, LIQUID_EXIT, BUBBLE_EXIT, WEB,
        VELOCITY, TELEPORT_GRACE,
        SLIME_BLOCK,
        LEVITATION, SLOW_FALLING,
        GRAVITY_INVALID, FLY_DIP_RESET, LAZY_GROUND,
        ICE, SOUL_SAND, HONEY, SPRINT_JUMP, SNEAKING,
        AIRBORNE_XZ, WEB_XZ, LIQUID_XZ, VELOCITY_XZ,
        USING_ITEM, BLOCK_PLACE, GHOST_BLOCK, ELYTRA_RESIDUAL,
        GROUND_XZ, GROUND_SPEED, GROUND_ACCEL, GROUND_STRAFE,
        H_PREDICT, H_DRIFT, WALL_TOUCH
    }

    // ─── Per-player state ─────────────────────────────────────────────────────
    private static final class State {
        // Vertical
        float   verticalBuffer;
        int     hoverTicks;
        int     landingTicks;
        double  slimeFallVelocity;
        boolean lastWasOnGround;
        // Horizontal — area-based
        float  horizontalBuffer;
        double lastXZSpeed;
        int    overSpeedTicks;
        int    groundSpeedTicks;
        int    groundAccelTicks;
        int    groundStrafeTicks;
        double driftAccumulator;
        int    driftViolationTicks;
        // Key inference cache
        int    lastForward;
        int    lastStrafe;
        // Sprint-jump grace tracking
        // Tracks how many ticks ago the player last performed a sprint-jump.
        // Used to extend SPRINT_JUMP tag lifetime through the full jump arc.
        int    sprintJumpTick;
    }

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    // FIX: Cache SimulationC enabled state — resolved once, never re-scanned per packet.
    private volatile boolean simCEnabled  = false;
    private volatile boolean simCResolved = false;

    // ─── Entry point ──────────────────────────────────────────────────────────
    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;
        final PlayerData data = wrapper.getPlayerData();
        if (data == null) return;

        if (data.isGliding()) return;
        if (data.isExempt(ExemptionType.RESPAWN)) {
            final State st2 = states.get(data.getPlayer().getUniqueId());
            if (st2 != null) {
                st2.verticalBuffer    = 0f;
                st2.horizontalBuffer  = 0f;
                st2.hoverTicks        = 0;
                st2.driftAccumulator  = 0;
                st2.sprintJumpTick    = -999;
            }
            return;
        }
        if (data.isTeleportPending()) return;
        if (MovementCheckSupport.skipForPrediction(data)) return;

        final State st = states.computeIfAbsent(data.getPlayer().getUniqueId(), k -> {
            State s = new State();
            s.sprintJumpTick = -999;
            return s;
        });

        final double deltaY      = data.getDeltaY();
        final double lastDeltaY  = data.getLastDeltaY();
        final double deltaXZ     = data.getDeltaXZ();
        final double lastDeltaXZ = data.getLastDeltaXZ();

        final boolean onGround  = data.isServerGround() || data.isClientGround();
        final int airTicks      = data.getAirTicks();
        final int lastAirTicks  = data.getPositionTracker().getLastAirTicks();
        final int ticksNow      = data.getTicksTracked();

        // FIX: Tightened from airTicks <= 2 to airTicks <= 1.
        // airTicks <= 2 let bhop-style cheats stay in the JUMP grace window
        // for an extra tick while already airborne.
        final boolean wasRecentlyOnGround = onGround || airTicks <= 1 || lastAirTicks == 0;

        final int levitation  = data.getPotionLevel(PotionEffectType.LEVITATION);
        final int slowFalling = data.getPotionLevel(PotionEffectType.SLOW_FALLING);
        final int jumpBoost   = data.getPotionLevel(PotionEffectType.JUMP_BOOST);
        final int speedLevel  = data.getPotionLevel(PotionEffectType.SPEED);
        final double jumpImpulse = PhysicsConstants.JUMP_IMPULSE + jumpBoost * JUMP_BOOST_PER_LEVEL;

        // Slime bounce tracker
        if (deltaY < -0.05D) st.slimeFallVelocity = Math.min(st.slimeFallVelocity, deltaY);
        if (deltaY > 0.0D)   st.slimeFallVelocity = 0.0D;

        final EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);

        // ── Build environmental tags ────────────────────────────────────────
        if (!st.lastWasOnGround && onGround) { tags.add(Tag.LANDING); st.landingTicks = 3; }
        if (st.landingTicks > 0) { tags.add(Tag.LANDING_GRACE); st.landingTicks--; }

        if (data.isInLiquid()) tags.add(Tag.LIQUID);
        else if (WorldUtils.isNearLiquid(data.getPlayer())) {
            tags.add(Tag.LIQUID_EXIT);
            if (WorldUtils.isNearBubbleColumn(data.getPlayer())) tags.add(Tag.BUBBLE_EXIT);
        }

        if (data.isInWeb())       tags.add(Tag.WEB);
        if (data.isOnClimbable()) tags.add(Tag.LADDER);
        if (data.isUnderBlock() || ticksNow - data.getLastUnderBlockTick() <= 3) tags.add(Tag.HEAD_HIT);
        if (isNearHorizontalCollision(data)) tags.add(Tag.WALL_TOUCH);

        if (data.getMovementContext().isSlimeBounce()) tags.add(Tag.SLIME_BLOCK);
        if (data.getMovementContext().isHoney()) tags.add(Tag.HONEY);
        if (levitation > 0)  tags.add(Tag.LEVITATION);
        if (slowFalling > 0) tags.add(Tag.SLOW_FALLING);

        int ticksSinceVelocity = ticksNow - data.getLastVelocityTick();
        if (data.hasVelocity() || (ticksSinceVelocity >= 0 && ticksSinceVelocity <= 5))
            tags.add(Tag.VELOCITY);

        if (data.getTicksSinceTeleport() < 5 || ticksNow - data.getLastVehicleExitTick() < 10)
            tags.add(Tag.TELEPORT_GRACE);

        boolean inAir = !onGround && !tags.contains(Tag.LADDER) && !tags.contains(Tag.LIQUID);
        if (inAir) tags.add(Tag.IN_AIR);

        if (data.isUsingItem() || ticksNow - data.getLastUseItemTick() <= 2) tags.add(Tag.USING_ITEM);
        if (ticksNow - data.getLastBlockPlaceTick() <= 3) tags.add(Tag.BLOCK_PLACE);
        if (ticksNow - data.getLastGhostBlockTick() <= 10) tags.add(Tag.GHOST_BLOCK);

        boolean isJumpMotion = wasRecentlyOnGround && deltaY > (jumpImpulse - 0.002D) && deltaY < (jumpImpulse + 0.002D);
        boolean isLikelyJump = wasRecentlyOnGround && deltaY > 0.3D;
        boolean isGhostJump  = (tags.contains(Tag.BLOCK_PLACE) || tags.contains(Tag.GHOST_BLOCK))
                && deltaY > 0.0D && Math.abs(deltaY - jumpImpulse) < 0.05D;

        if ((airTicks <= 1 && wasRecentlyOnGround) || isJumpMotion || isLikelyJump || isGhostJump) tags.add(Tag.JUMP);
        if (wasRecentlyOnGround && airTicks == 0 && deltaY > 0.0D) tags.add(Tag.JUMP_START);
        if (tags.contains(Tag.SLIME_BLOCK) && deltaY > 0.0D) tags.add(Tag.JUMP_START);
        if (isGhostJump) tags.add(Tag.JUMP_START);
        if (!onGround && airTicks < 25 && deltaY > 0.0D) tags.add(Tag.JUMPING);

        boolean isLegitStep = data.isServerGround() && deltaY > 0.0D && deltaY <= 0.601D;
        if (isLegitStep) tags.add(Tag.STEP_Y);

        if (onGround && deltaY < 0.0D)                        tags.add(Tag.STEP_DOWN);
        if (st.lastWasOnGround && !onGround && deltaY < 0.0D) tags.add(Tag.STEP_DOWN);

        boolean lazyGround = !data.isServerGround() && data.isClientGround() && Math.abs(deltaY) < 1.0E-5D;
        if (lazyGround) tags.add(Tag.LAZY_GROUND);

        // Hover / fly-dip detection
        if (tags.contains(Tag.IN_AIR) && Math.abs(deltaY) < 1.0E-7D
                && !tags.contains(Tag.LADDER) && !tags.contains(Tag.LIQUID)
                && !tags.contains(Tag.HEAD_HIT)
                && !tags.contains(Tag.VELOCITY) && !tags.contains(Tag.TELEPORT_GRACE)) {
            st.hoverTicks++;
            if (st.hoverTicks > 3) { tags.add(Tag.GRAVITY_INVALID); st.verticalBuffer += 0.5f; }
        } else { st.hoverTicks = 0; }

        if (tags.contains(Tag.IN_AIR) && Math.abs(deltaY + 0.04D) < 1.0E-4D
                && !tags.contains(Tag.HEAD_HIT)
                && !tags.contains(Tag.VELOCITY) && !tags.contains(Tag.TELEPORT_GRACE))
            tags.add(Tag.FLY_DIP_RESET);

        if (ticksNow - data.getLastGlideTick() <= 20) tags.add(Tag.ELYTRA_RESIDUAL);

        // ── Vertical simulation ─────────────────────────────────────────────
        processVertical(data, st, tags, deltaY, lastDeltaY, onGround, lazyGround,
                wasRecentlyOnGround, airTicks, levitation, slowFalling, jumpImpulse,
                ticksSinceVelocity);

        // ── Horizontal simulation ────────────────────────────────────────────
        Vector queuedVel = data.getVelocities().getQueuedVelocityVector();
        processHorizontal(data, st, tags, deltaXZ, lastDeltaXZ, onGround,
                wasRecentlyOnGround, airTicks, speedLevel, queuedVel, ticksSinceVelocity, ticksNow);

        st.lastWasOnGround = onGround;
        st.lastXZSpeed     = deltaXZ;
    }

    // ─── Vertical processing ──────────────────────────────────────────────────
    private void processVertical(
            final PlayerData data, final State st, final EnumSet<Tag> tags,
            final double deltaY, final double lastDeltaY,
            final boolean onGround, final boolean lazyGround,
            final boolean wasRecentlyOnGround, final int airTicks,
            final int levitation, final int slowFalling,
            final double jumpImpulse, final int ticksSinceVelocity) {

        double predFallY;
        if (levitation > 0) {
            double target = 0.05D * levitation;
            predFallY = (lastDeltaY + (target - lastDeltaY) * 0.2D) * PhysicsConstants.AIR_DRAG_Y;
        } else {
            predFallY = (lastDeltaY - PhysicsConstants.GRAVITY) * PhysicsConstants.AIR_DRAG_Y;
        }
        if (slowFalling > 0) predFallY = Math.max(predFallY, SLOW_FALL_MIN_Y);
        if (tags.contains(Tag.WEB)) predFallY *= 0.05D;

        double vDiff = Math.abs(deltaY - predFallY);

        if (onGround || lazyGround || tags.contains(Tag.HEAD_HIT) || tags.contains(Tag.LANDING)) {
            vDiff = Math.min(vDiff, Math.abs(deltaY));
            vDiff = Math.min(vDiff, Math.abs(deltaY - FREEFALL_FROM_ZERO));
        }
        if (tags.contains(Tag.ELYTRA_RESIDUAL)) {
            vDiff = Math.min(vDiff, Math.abs(deltaY - lastDeltaY));
            vDiff = Math.min(vDiff, Math.abs(deltaY));
        }
        if (tags.contains(Tag.JUMP) || tags.contains(Tag.JUMP_START) || wasRecentlyOnGround)
            vDiff = Math.min(vDiff, Math.abs(deltaY - jumpImpulse));
        if (tags.contains(Tag.JUMPING))
            vDiff = Math.min(vDiff, Math.abs(deltaY - FREEFALL_FROM_ZERO));
        if (tags.contains(Tag.HEAD_HIT))
            if (deltaY > 0.0D && deltaY < jumpImpulse + 0.01D) vDiff = 0.0D;
        if (tags.contains(Tag.HEAD_HIT) && lastDeltaY > 0.0D && deltaY <= 0.0D)
            vDiff = 0.0D;
        if (tags.contains(Tag.LADDER)) {
            vDiff = Math.min(vDiff, Math.abs(deltaY - 0.2D));
            vDiff = Math.min(vDiff, Math.abs(deltaY - 0.1176D));
            vDiff = Math.min(vDiff, Math.abs(deltaY));
            vDiff = Math.min(vDiff, Math.abs(deltaY + 0.15D));
        }
        if (tags.contains(Tag.LIQUID)) {
            double waterJump = (lastDeltaY + 0.04D) * 0.8D;
            double waterFall = (lastDeltaY - 0.04D) * 0.8D;
            vDiff = Math.min(vDiff, Math.abs(deltaY - waterJump));
            vDiff = Math.min(vDiff, Math.abs(deltaY - waterFall));
            vDiff = Math.min(vDiff, Math.abs(deltaY - 0.04D));
            vDiff = Math.min(vDiff, Math.abs(deltaY + 0.5D));
            vDiff = Math.min(vDiff, Math.abs(deltaY - 0.7D));
            // FIX: Was 0.49D — too generous, allowed water-based fly bypasses.
            // 0.42D matches vanilla water jump-out speed.
            vDiff = Math.min(vDiff, Math.abs(deltaY - 0.42D));
        }
        if (tags.contains(Tag.LIQUID_EXIT)) {
            double waterJump = (lastDeltaY + 0.04D) * 0.8D;
            double waterFall = (lastDeltaY - 0.04D) * 0.8D;
            vDiff = Math.min(vDiff, Math.abs(deltaY - waterJump));
            vDiff = Math.min(vDiff, Math.abs(deltaY - waterFall));
            vDiff = Math.min(vDiff, Math.abs(deltaY - lastDeltaY));
            // Bubble columns launch with velocities up to 0.7 (up) or -0.49 (down),
            // which combined with jump impulse can reach ~0.85, and even slightly higher with momentum.
            double liquidExitCap = tags.contains(Tag.BUBBLE_EXIT) ? 1.15D : 0.45D;
            if (deltaY > 0.0D && deltaY <= liquidExitCap) vDiff = 0.0D;
            // Also accept gravity-following ticks after bubble column exit
            if (tags.contains(Tag.BUBBLE_EXIT)) {
                double bubblePred = (lastDeltaY - PhysicsConstants.GRAVITY) * PhysicsConstants.AIR_DRAG_Y;
                vDiff = Math.min(vDiff, Math.abs(deltaY - bubblePred));
                // Accept downward bubble column velocities
                if (deltaY < 0.0D && deltaY >= -0.55D) vDiff = 0.0D;
            }
        }
        if (tags.contains(Tag.SLIME_BLOCK)) {
            double maxBounce = Math.abs(st.slimeFallVelocity) + 0.05D;
            if (tags.contains(Tag.JUMP_START) && deltaY > 0.0D && deltaY <= jumpImpulse + 0.02D) {
                vDiff = 0.0D;
            } else if (deltaY >= 0.0D && deltaY <= maxBounce) {
                vDiff = 0.0D;
            } else {
                vDiff = Math.min(vDiff, Math.abs(deltaY - Math.abs(st.slimeFallVelocity)));
                vDiff = Math.min(vDiff, Math.abs(deltaY));
            }
        }
        if (tags.contains(Tag.BLOCK_PLACE) || tags.contains(Tag.GHOST_BLOCK)) {
            if (deltaY <= 0.0D && deltaY >= -0.1D) vDiff = Math.min(vDiff, Math.abs(deltaY));
        }

        Vector queuedVel = data.getVelocities().getQueuedVelocityVector();
        if (tags.contains(Tag.VELOCITY) && queuedVel != null) {
            double velY = queuedVel.getY();
            vDiff = Math.min(vDiff, Math.abs(deltaY - velY));
            if (ticksSinceVelocity <= 8 && Math.abs(deltaY - velY) < 0.60D) vDiff = 0.0D;
        }

        boolean validVerticalStep = data.isServerGround() && deltaY <= 0.601D;
        if (tags.contains(Tag.GRAVITY_INVALID) || tags.contains(Tag.FLY_DIP_RESET)) validVerticalStep = false;

        if (validVerticalStep || tags.contains(Tag.TELEPORT_GRACE) || tags.contains(Tag.STEP_DOWN) || tags.contains(Tag.LAZY_GROUND)) {
            if (!tags.contains(Tag.GRAVITY_INVALID) && !tags.contains(Tag.FLY_DIP_RESET)) vDiff = 0.0D;
        }
        if (tags.contains(Tag.HEAD_HIT) && vDiff < 0.12D) {
            vDiff = 0.0D;
            st.verticalBuffer = Math.max(0, st.verticalBuffer - 0.4f);
        }
        if (tags.contains(Tag.TELEPORT_GRACE)) { vDiff = 0.0D; st.verticalBuffer = 0.0f; }
        if (tags.contains(Tag.VELOCITY) && vDiff < 0.60D) {
            vDiff = 0.0D;
            st.verticalBuffer = Math.max(0, st.verticalBuffer - 0.8f);
        }

        if (vDiff > 0.01D) {
            float mult;
            // FIX: Reduced from 40.0f to 6.0f. The hoverTicks gate (> 3 ticks) already
            // ensures only real hover/fly reaches this point. 40.0f caused an instant
            // spike far above the threshold on the first qualifying tick, bypassing
            // the buffer system entirely.
            if (tags.contains(Tag.GRAVITY_INVALID) || tags.contains(Tag.FLY_DIP_RESET)) mult = 6.0f;
            else if (tags.contains(Tag.IN_AIR) && Math.abs(deltaY) < 1.0E-5D)           mult = 25.0f;
            else if (tags.contains(Tag.IN_AIR) && vDiff > 0.05D)                         mult = 15.0f;
            else if (tags.contains(Tag.VELOCITY))                                          mult = 2.5f;
            else                                                                            mult = 5.0f;
            st.verticalBuffer += (float) (vDiff * mult);
        } else {
            float decay = tags.contains(Tag.IN_AIR) ? 0.025f : 0.1f;
            if (tags.contains(Tag.LIQUID_EXIT)) decay = 0.2f;
            if (tags.contains(Tag.BUBBLE_EXIT)) decay = 0.4f;
            if (!tags.contains(Tag.GRAVITY_INVALID) && !tags.contains(Tag.FLY_DIP_RESET))
                st.verticalBuffer = Math.max(0, st.verticalBuffer - decay);
        }

        if (st.verticalBuffer >= VERTICAL_FLAG_THRESHOLD && vDiff > 0.01D) {
            String sub = tags.contains(Tag.GRAVITY_INVALID) ? "(AirWalk)"
                    : tags.contains(Tag.FLY_DIP_RESET)      ? "(FlyDip)"
                      : tags.contains(Tag.IN_AIR)              ? "(Air)"
                        : "";
            flag(data, String.format(
                    "Area Fail (V) %s buf=%.3f vDiff=%.4f Y=%.4f lastY=%.4f air=%d tags=%s",
                    sub, st.verticalBuffer, vDiff, deltaY, lastDeltaY, airTicks, tags));
            if (Truthful.getInstance().getConfiguration().isLagbacks()) data.executeLagback();
            st.verticalBuffer = Math.min(st.verticalBuffer, 1.5f);
        }
    }

    // ─── Horizontal processing ────────────────────────────────────────────────
    private void processHorizontal(
            final PlayerData data, final State st, final EnumSet<Tag> tags,
            final double deltaXZ, final double lastDeltaXZ,
            final boolean onGround, final boolean wasRecentlyOnGround,
            final int airTicks, final int speedLevel,
            final Vector queuedVel, final int ticksSinceVelocity, final int ticksNow) {

        // FIX: Resolve SimulationC once and cache. Previously scanned all checks every packet.
        if (tags.contains(Tag.LIQUID)) {
            if (!simCResolved) {
                for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
                    if (check instanceof ret.tawny.truthful.checks.impl.movement.simulation.SimulationC) {
                        simCEnabled  = check.isEnabled();
                        simCResolved = true;
                        break;
                    }
                }
            }
            if (simCEnabled) return;
        }

        if (deltaXZ < MIN_XZ_MOTION && lastDeltaXZ < MIN_XZ_MOTION) {
            st.horizontalBuffer = Math.max(0f, st.horizontalBuffer - 0.1f);
            st.driftAccumulator *= 0.85;
            return;
        }

        if (tags.contains(Tag.VELOCITY) || tags.contains(Tag.TELEPORT_GRACE)) {
            st.horizontalBuffer = Math.max(0f, st.horizontalBuffer - 0.3f);
            st.overSpeedTicks   = 0;
            st.driftAccumulator *= 0.7;
            return;
        }

        if (tags.contains(Tag.WALL_TOUCH)) {
            st.horizontalBuffer = Math.max(0f, st.horizontalBuffer - 0.12f);
            st.overSpeedTicks   = 0;
            st.driftAccumulator *= 0.65D;
        }

        int ticksSinceIce  = ticksNow - data.getLastIceTick();
        if (ticksSinceIce >= 0 && ticksSinceIce <= 20) tags.add(Tag.ICE);

        int ticksSinceSoul = ticksNow - data.getLastSoulSandTick();
        if (ticksSinceSoul >= 0 && ticksSinceSoul <= 8) tags.add(Tag.SOUL_SAND);

        boolean isSprinting = data.isSprinting();

        // FIX: SPRINT_JUMP tag now uses a persistent tick tracker (sprintJumpTick) so
        // the tag stays active for SPRINT_JUMP_GRACE_TICKS (8) ticks of air time.
        // Previously the tag only applied on airTicks 0-1, which caused the AS check
        // and H_DRIFT to false-flag mid-jump when the sprint-jump momentum was still
        // fully present but the tag had already expired.
        if (isSprinting && (tags.contains(Tag.JUMP_START) || (airTicks > 0 && airTicks <= 1 && wasRecentlyOnGround))) {
            st.sprintJumpTick = ticksNow;
        }
        boolean isSprintJump = (ticksNow - st.sprintJumpTick) <= SPRINT_JUMP_GRACE_TICKS;
        if (isSprintJump) tags.add(Tag.SPRINT_JUMP);

        if (data.isSneaking())  tags.add(Tag.SNEAKING);
        if (!onGround)          tags.add(Tag.AIRBORNE_XZ);
        if (data.isInWeb())     tags.add(Tag.WEB_XZ);
        if (data.hasVelocity() || (ticksSinceVelocity >= 0 && ticksSinceVelocity <= 5)) tags.add(Tag.VELOCITY_XZ);

        double maxAllowed = MovementCheckSupport.computeMaxHorizontalSpeed(data);
        if (speedLevel > 0) maxAllowed += speedLevel * 0.03D;
        if (tags.contains(Tag.SPRINT_JUMP)) maxAllowed += SPRINT_JUMP_BOOST + 0.05D;
        if (tags.contains(Tag.USING_ITEM) && data.isSlowItem()) maxAllowed *= 0.35D;
        if (tags.contains(Tag.SOUL_SAND) && data.getEnchantLevel("soul_speed") == 0) maxAllowed = Math.min(maxAllowed, 0.13D);
        if (tags.contains(Tag.HONEY))    maxAllowed = Math.min(maxAllowed, 0.12D);
        if (tags.contains(Tag.WEB_XZ))   maxAllowed = Math.min(maxAllowed, 0.05D);
        if (tags.contains(Tag.LADDER))   maxAllowed = Math.min(maxAllowed, 0.15D);
        if (tags.contains(Tag.SNEAKING)) maxAllowed = Math.min(maxAllowed, 0.13D);
        if (tags.contains(Tag.SLIME_BLOCK)) maxAllowed += 0.15D;

        // Momentum preservation
        double friction = tags.contains(Tag.AIRBORNE_XZ) ? 0.91D : MovementCheckSupport.computeGroundFriction(data);
        double momentum = (lastDeltaXZ * friction) + 0.05D;
        if (tags.contains(Tag.SPRINT_JUMP)) momentum += SPRINT_JUMP_BOOST + 0.05D;
        maxAllowed = Math.max(maxAllowed, momentum);

        if (tags.contains(Tag.ELYTRA_RESIDUAL)) maxAllowed += 2.0D;
        if (queuedVel != null && tags.contains(Tag.VELOCITY_XZ)) {
            double velXZ = Math.sqrt(queuedVel.getX() * queuedVel.getX() + queuedVel.getZ() * queuedVel.getZ());
            if (velXZ > 0.0D) maxAllowed = Math.max(maxAllowed, velXZ * 1.15D + 0.4D);
        }
        if (tags.contains(Tag.LANDING) || tags.contains(Tag.LANDING_GRACE)) maxAllowed += 0.2D;
        if (tags.contains(Tag.STEP_Y)) maxAllowed += 0.1D;

        // ── Air strafe + air max ──────────────────────────────────────────────
        if (tags.contains(Tag.AIRBORNE_XZ) && !tags.contains(Tag.VELOCITY_XZ)
                && !tags.contains(Tag.ELYTRA_RESIDUAL) && !tags.contains(Tag.SLIME_BLOCK)) {

            double airAccel = isSprinting ? AIR_ACCEL_SPRINT : AIR_ACCEL_BASE;
            if (speedLevel > 0) airAccel += speedLevel * 0.006D;
            double maxAirSpeed = (lastDeltaXZ * 0.91D) + airAccel + 0.08D;
            if (tags.contains(Tag.SPRINT_JUMP)) maxAirSpeed += SPRINT_JUMP_BOOST + 0.05D;

            double[] inferredAccel = inferMoveRelativeAccel(data, tags, airAccel, st);
            double predictedVX = data.getLastDeltaX() * 0.91D + inferredAccel[0];
            double predictedVZ = data.getLastDeltaZ() * 0.91D + inferredAccel[1];

            double accelX = data.getDeltaX() - data.getLastDeltaX() * 0.91D;
            double accelZ = data.getDeltaZ() - data.getLastDeltaZ() * 0.91D;
            double airVectorAccel = Math.hypot(accelX, accelZ);

            double vecDev = Math.hypot(data.getDeltaX() - predictedVX, data.getDeltaZ() - predictedVZ);

            // FIX: Raised base drift tolerance from 0.035 to 0.055 and sprint-jump
            // bonus from 0.08 to 0.12. The logs showed legitimate jumps consistently
            // producing drift values of 0.25-0.30 at dTicks=3 because the inferred
            // key direction has natural variance mid-jump when the player changes
            // direction. The previous thresholds were too tight for normal play.
            double driftTolerance = 0.055D;
            if (tags.contains(Tag.SPRINT_JUMP) || tags.contains(Tag.JUMP) || tags.contains(Tag.JUMP_START))
                driftTolerance += 0.12D;
            if (tags.contains(Tag.WALL_TOUCH)) driftTolerance += 0.12D;
            if (ticksNow - data.getLastDamageTick() < 6) driftTolerance += 0.04D;

            double excessDrift = Math.max(0.0D, vecDev - driftTolerance);
            st.driftAccumulator = st.driftAccumulator * 0.88D + excessDrift;

            if (st.driftAccumulator > 0.18D) {
                st.driftViolationTicks++;
            } else {
                st.driftViolationTicks = Math.max(0, st.driftViolationTicks - 1);
            }

            // FIX: Raised base cap from 0.08 to 0.12, sprint-jump bonus from 0.05 to 0.08.
            // Previous values false-flagged sprint-jumps before SPRINT_JUMP tag was applied.
            double maxAirVectorAccel = airAccel + 0.12D;
            if (tags.contains(Tag.SPRINT_JUMP) || tags.contains(Tag.JUMP) || tags.contains(Tag.JUMP_START))
                maxAirVectorAccel += SPRINT_JUMP_BOOST + 0.08D;
            if (tags.contains(Tag.TELEPORT_GRACE))
                maxAirVectorAccel += 1.0D;
            else if (ticksNow - data.getLastDamageTick() < 6)
                maxAirVectorAccel += 0.35D;

            if (airVectorAccel > maxAirVectorAccel && !tags.contains(Tag.WEB_XZ)
                    && !tags.contains(Tag.LADDER) && !tags.contains(Tag.WALL_TOUCH)) {
                st.horizontalBuffer += (float) ((airVectorAccel - maxAirVectorAccel) * 15.0D);
                // FIX: airTicks > 1 gate prevents flagging the first air tick of any
                // sprint-jump where the boost burst naturally exceeds the cap.
                if (st.horizontalBuffer >= HORIZONTAL_FLAG_THRESHOLD && airTicks > 1) {
                    flag(data, String.format("Area Fail (AS) vAccel=%.4f max=%.4f tags=%s",
                            airVectorAccel, maxAirVectorAccel, tags));
                    if (Truthful.getInstance().getConfiguration().isLagbacks()) data.executeLagback();
                    st.horizontalBuffer = Math.min(st.horizontalBuffer, 2.0f);
                }
            }

            // FIX: Renamed from "Area Fail (H)" to "Area Fail (H_DRIFT)" to distinguish
            // from the generic speed cap flag below which had the same name.
            // FIX: Raised drift threshold from 0.25 to 0.35 and dTicks from 3 to 4.
            // Logs showed normal sprint-jumping reaching drift=0.26-0.29 at dTicks=3
            // consistently. Real cheats will sustain higher drift for more ticks.
            if (st.driftAccumulator > 0.35D && st.driftViolationTicks >= 4
                    && !tags.contains(Tag.WEB_XZ) && !tags.contains(Tag.LADDER)
                    && !tags.contains(Tag.WALL_TOUCH)) {
                tags.add(Tag.H_DRIFT);
                flag(data, String.format("Area Fail (H_DRIFT) drift=%.4f dTicks=%d pred=(%.4f,%.4f) got=(%.4f,%.4f) tags=%s",
                        st.driftAccumulator, st.driftViolationTicks,
                        predictedVX, predictedVZ,
                        data.getDeltaX(), data.getDeltaZ(), tags));
                if (Truthful.getInstance().getConfiguration().isLagbacks()) data.executeLagback();
                st.driftAccumulator   = 0.12D;
                st.driftViolationTicks = 0;
            }

            if (maxAirSpeed < maxAllowed && !tags.contains(Tag.WEB_XZ) && !tags.contains(Tag.LADDER))
                maxAllowed = maxAirSpeed;
        } else {
            st.driftAccumulator   *= 0.75D;
            st.driftViolationTicks = Math.max(0, st.driftViolationTicks - 1);
        }

        // ── Ground speed check ────────────────────────────────────────────────
        GroundSpeedResult groundResult = checkGroundSpeed(data, st, tags, deltaXZ, lastDeltaXZ, onGround, speedLevel, ticksNow);
        if (groundResult.applicable) {
            if (groundResult.speedDiff > H_NOISE_FLOOR) { tags.add(Tag.GROUND_SPEED); st.groundSpeedTicks++; }
            else st.groundSpeedTicks = Math.max(0, st.groundSpeedTicks - 1);

            if (groundResult.accelDiff > H_NOISE_FLOOR) { tags.add(Tag.GROUND_ACCEL); st.groundAccelTicks++; }
            else st.groundAccelTicks = Math.max(0, st.groundAccelTicks - 1);

            if (groundResult.vectorAccelDiff > H_NOISE_FLOOR) { tags.add(Tag.GROUND_STRAFE); st.groundStrafeTicks++; }
            else st.groundStrafeTicks = Math.max(0, st.groundStrafeTicks - 1);

            // FIX: Changed from >= 1 to >= 2 ticks + accel confirmation.
            // >= 1 triggered the buffer on the very first tick anything exceeded the
            // noise floor, false-flagging friction transitions and sprint-jump landings.
            if ((st.groundSpeedTicks >= 2 && st.groundAccelTicks >= 1) || groundResult.speedDiff > 0.12D) {
                double groundDiff = Math.max(groundResult.speedDiff,
                        Math.max(groundResult.accelDiff, groundResult.vectorAccelDiff));
                float groundMult = groundDiff > 0.1D ? 35.0f : 20.0f;
                st.horizontalBuffer += (float) (groundDiff * groundMult);
            } else {
                st.horizontalBuffer = Math.max(0f, st.horizontalBuffer - 0.05f);
            }

            if (st.horizontalBuffer >= HORIZONTAL_FLAG_THRESHOLD) {
                flag(data, String.format(
                        "Area Fail (G) buf=%.3f XZ=%.4f cap=%.4f accel=%.4f accelMax=%.4f vAccel=%.4f vMax=%.4f sTicks=%d aTicks=%d strafeTicks=%d tags=%s",
                        st.horizontalBuffer, deltaXZ, groundResult.speedCap, groundResult.accel,
                        groundResult.accelCap, groundResult.vectorAccel, groundResult.vectorAccelCap,
                        st.groundSpeedTicks, st.groundAccelTicks, st.groundStrafeTicks, tags));
                if (Truthful.getInstance().getConfiguration().isLagbacks()) data.executeLagback();
                st.horizontalBuffer = Math.min(st.horizontalBuffer, 2.0f);
            }
            return;
        } else {
            st.groundSpeedTicks  = 0;
            st.groundAccelTicks  = 0;
            st.groundStrafeTicks = 0;
        }

        // ── Generic area-based speed cap ─────────────────────────────────────
        double hDiff = deltaXZ - maxAllowed;

        if (hDiff <= H_NOISE_FLOOR) {
            st.overSpeedTicks = 0;
            float hDecay = tags.contains(Tag.AIRBORNE_XZ) ? 0.02f : 0.08f;
            st.horizontalBuffer = Math.max(0f, st.horizontalBuffer - hDecay);
            return;
        }

        if (hDiff > 1.25D) st.overSpeedTicks = Math.max(st.overSpeedTicks, 2);
        st.overSpeedTicks++;
        if (st.overSpeedTicks < 2) return;

        float hMult = hDiff > 0.5D ? 30.0f : hDiff > 0.15D ? 15.0f : 6.0f;
        st.horizontalBuffer += (float) (hDiff * hMult);

        if (st.horizontalBuffer >= HORIZONTAL_FLAG_THRESHOLD) {
            flag(data, String.format("Area Fail (H) buf=%.3f hDiff=%.4f XZ=%.4f maxAllow=%.4f air=%d tags=%s",
                    st.horizontalBuffer, hDiff, deltaXZ, maxAllowed, airTicks, tags));
            if (Truthful.getInstance().getConfiguration().isLagbacks()) data.executeLagback();
            st.horizontalBuffer = Math.min(st.horizontalBuffer, 2.0f);
        }
    }

    private double[] inferMoveRelativeAccel(final PlayerData data, final EnumSet<Tag> tags,
                                            final double friction, final State st) {
        double dvX  = data.getDeltaX() - data.getLastDeltaX() * 0.91D;
        double dvZ  = data.getDeltaZ() - data.getLastDeltaZ() * 0.91D;
        double dvMag = Math.hypot(dvX, dvZ);

        double yawRad = Math.toRadians(data.getYaw());
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);

        int forward = 0, strafe = 0;

        if (dvMag > 0.003D) {
            double localForward = -dvX * sinYaw + dvZ * cosYaw;
            double localStrafe  =  dvX * cosYaw + dvZ * sinYaw;
            forward = (int) Math.signum(localForward);
            strafe  = (int) Math.signum(localStrafe);
            st.lastForward = forward;
            st.lastStrafe  = strafe;
        } else {
            forward = st.lastForward;
            strafe  = st.lastStrafe;
        }

        double f   = forward * KEY_SCALE;
        double s   = strafe  * KEY_SCALE;
        double len = Math.sqrt(f * f + s * s);
        if (len < 0.0001D) return new double[]{0.0D, 0.0D};

        double scale = friction / Math.max(1.0D, len);
        f *= scale;
        s *= scale;

        return new double[]{
                s * cosYaw - f * sinYaw,
                f * cosYaw + s * sinYaw
        };
    }

    // ─── Ground speed check ───────────────────────────────────────────────────
    private GroundSpeedResult checkGroundSpeed(
            final PlayerData data, final State st, final EnumSet<Tag> tags,
            final double deltaXZ, final double lastDeltaXZ,
            final boolean onGround, final int speedLevel, final int ticksNow) {
        GroundSpeedResult result = new GroundSpeedResult();
        if (!onGround || tags.contains(Tag.IN_AIR)) return result;

        // FIX: Skip the ground speed check entirely on the landing tick of a sprint-jump.
        // The G flags in the logs all had JUMP, JUMP_START, STEP_Y, SPRINT_JUMP, WALL_TOUCH
        // and XZ values of 0.51-0.58 which is sprint-jump landing momentum, not ground speed.
        // The ground check should not fire when the player is just touching down from a jump
        // because the XZ at that tick is still airborne momentum, not a ground acceleration.
        if (tags.contains(Tag.SPRINT_JUMP) && (tags.contains(Tag.JUMP_START) || tags.contains(Tag.STEP_Y))) {
            return result;
        }

        tags.add(Tag.GROUND_XZ);
        result.applicable = true;

        double attributeScale  = Math.max(0.2D, data.getWalkSpeed() / 0.1D);
        double speedMultiplier = 1.0D;
        if (speedLevel > 0) speedMultiplier += 0.20D * speedLevel;

        int slownessLevel = data.getPotionLevel(PotionEffectType.SLOWNESS);
        if (slownessLevel > 0) speedMultiplier *= Math.max(0.0D, 1.0D - 0.15D * slownessLevel);

        double speedCap = (data.isSprinting() ? GROUND_BASE_SPRINT_CAP : GROUND_BASE_WALK_CAP)
                * attributeScale * speedMultiplier + GROUND_CAP_LENIENCY;

        if (tags.contains(Tag.SNEAKING))                                    speedCap = Math.min(speedCap, 0.15D + GROUND_CAP_LENIENCY);
        if (tags.contains(Tag.USING_ITEM) && data.isSlowItem())             speedCap = Math.min(speedCap, 0.12D + GROUND_CAP_LENIENCY);
        if (tags.contains(Tag.SOUL_SAND) && data.getEnchantLevel("soul_speed") == 0) speedCap = Math.min(speedCap, 0.13D + GROUND_CAP_LENIENCY);
        if (tags.contains(Tag.HONEY))                                       speedCap = Math.min(speedCap, 0.12D + GROUND_CAP_LENIENCY);

        double friction        = MovementCheckSupport.computeGroundFriction(data);
        double accel           = Math.max(0.0D, deltaXZ - lastDeltaXZ * friction);
        double rawAccelCap     = computeGroundAccelCap(data, speedLevel, friction, tags);
        double accelCap        = rawAccelCap + GROUND_ACCEL_LENIENCY;

        double accelX          = data.getDeltaX() - data.getLastDeltaX() * friction;
        double accelZ          = data.getDeltaZ() - data.getLastDeltaZ() * friction;
        double vectorAccel     = Math.hypot(accelX, accelZ);
        double vectorAccelCap  = rawAccelCap + GROUND_VECTOR_ACCEL_LENIENCY;

        result.speedCap        = speedCap;
        result.accelCap        = accelCap;
        result.accel           = accel;
        result.vectorAccel     = vectorAccel;
        result.vectorAccelCap  = vectorAccelCap;
        result.speedDiff       = deltaXZ - speedCap;
        result.accelDiff       = accel - accelCap;
        result.vectorAccelDiff = vectorAccel - vectorAccelCap;
        return result;
    }

    private double computeGroundAccelCap(final PlayerData data, final int speedLevel,
                                         final double friction, final EnumSet<Tag> tags) {
        double base = data.getWalkSpeed();
        if (speedLevel > 0) base *= 1.0D + 0.20D * speedLevel;
        int slownessLevel = data.getPotionLevel(PotionEffectType.SLOWNESS);
        if (slownessLevel > 0) base *= Math.max(0.0D, 1.0D - 0.15D * slownessLevel);
        if (data.isSprinting()) base *= 1.30D;
        if (tags.contains(Tag.SNEAKING)) base *= 0.30D;
        if (tags.contains(Tag.USING_ITEM) && data.isSlowItem()) base *= 0.30D;
        double f3 = friction * friction * friction;
        if (f3 <= 1.0E-5D) return 0.0D;
        return base * (0.16277136D / f3);
    }

    // ─── Collision helpers ────────────────────────────────────────────────────
    private boolean isNearHorizontalCollision(final PlayerData data) {
        final double x = data.getX(), y = data.getY(), z = data.getZ(), r = 0.36D;
        return hasSolid(data, x + r, y + 0.20D, z) || hasSolid(data, x - r, y + 0.20D, z)
                || hasSolid(data, x, y + 0.20D, z + r) || hasSolid(data, x, y + 0.20D, z - r)
                || hasSolid(data, x + r, y + 1.00D, z) || hasSolid(data, x - r, y + 1.00D, z)
                || hasSolid(data, x, y + 1.00D, z + r) || hasSolid(data, x, y + 1.00D, z - r);
    }

    private boolean hasSolid(final PlayerData data, final double x, final double y, final double z) {
        int fx = (int) Math.floor(x), fy = (int) Math.floor(y), fz = (int) Math.floor(z);
        return ret.tawny.truthful.utils.world.BlockPropertyRegistry.isSolid(
                data.getWorldCache().getBlockState(fx, fy, fz));
    }

    // ─── Result container ─────────────────────────────────────────────────────
    private static final class GroundSpeedResult {
        boolean applicable;
        double speedCap, accelCap, accel, vectorAccel;
        double speedDiff, accelDiff, vectorAccelCap, vectorAccelDiff;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }
}