package ret.tawny.truthful.checks.impl.movement.simulation;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.checks.impl.movement.MovementCheckSupport;
import ret.tawny.truthful.data.ExemptionType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.BlockPropertyRegistry;
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'C', type = CheckType.SIMULATION)
public final class SimulationC extends Check {

    private static final double LIQUID_GRAVITY       = 0.04D;
    private static final double LIQUID_Y_DRAG        = 0.8D;
    private static final double LIQUID_XZ_DRAG       = 0.8D;

    private static final double MAX_ASCENT_SPEED     = 0.25D;
    private static final double MAX_DESCENT_SPEED    = -0.4D;

    private static final double BUBBLE_UP_MAX        = 0.72D;
    private static final double BUBBLE_DOWN_MAX      = -0.51D;

    private static final double MAX_WATER_XZ_BASE    = 0.20D; // Tightened base speed to catch generic Jesus (sus)
    private static final double DEPTH_STRIDER_BONUS  = 0.133D;
    private static final double MAX_LAVA_XZ          = 0.18D;

    private static final double NOISE_FLOOR          = 0.012D;
    private static final int LIQUID_ENTRY_GRACE_TICKS = 4;
    private static final double SURFACE_Y_THRESHOLD  = 0.08D;
    private static final double WALL_ASCENT_MAX      = 0.36D;
    private static final double WALL_ASCENT_CAP      = 0.12D;
    private static final float  FLAG_BUFFER_THRESHOLD = 8.0f;
    private static final float  FLAG_BUFFER_RESET     = 3.0f;

    private enum Tag {
        WATER, LAVA, BUBBLE_UP, BUBBLE_DOWN,
        SURFACE, BOBBING, SUBMERGED, SWIM_MODE, WALL_TOUCH,
        DEPTH_STRIDER, DOLPHIN_GRACE, SLOW_FALLING,
        ASCENDING, DESCENDING, SPRINTING,
        VELOCITY, TELEPORT_GRACE, LIQUID_ENTRY, RIPTIDE, CLIMBABLE,
        V_HOVER, V_CAP, V_PREDICT, H_CAP, H_DRAG,
    }

    private static final class State {
        int   hDragTicks;
        int   vHoverTicks;
        float buffer;
        boolean lastWasInLiquid;
        int     liquidEntryTick;
    }

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;
        final PlayerData data = wrapper.getPlayerData();
        if (data == null) return;

        if (!data.isInLiquid()) {
            final State st = states.get(data.getPlayer().getUniqueId());
            if (st != null) {
                st.buffer          = Math.max(0f, st.buffer - 0.15f);
                st.hDragTicks      = 0;
                st.vHoverTicks     = 0;
                st.lastWasInLiquid = false;
            }
            return;
        }

        if (MovementCheckSupport.skipForLiquidPrediction(data)) return;
        if (MovementCheckSupport.isInGraceWindow(data)) return;

        final State st     = states.computeIfAbsent(data.getPlayer().getUniqueId(), k -> new State());
        final int ticksNow = data.getTicksTracked();

        if (!st.lastWasInLiquid) {
            st.liquidEntryTick = ticksNow;
        }
        st.lastWasInLiquid = true;

        final double deltaXZ     = data.getDeltaXZ();
        final double lastDeltaXZ = data.getLastDeltaXZ();
        final double deltaY      = data.getDeltaY();
        final double lastDeltaY  = data.getLastDeltaY();
        final int    airTicks    = data.getAirTicks();

        final EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
        if (isInLava(data)) tags.add(Tag.LAVA);
        else tags.add(Tag.WATER);

        if (deltaY > 0.5D && lastDeltaY > 0.3D)    tags.add(Tag.BUBBLE_UP);
        if (deltaY < -0.4D && lastDeltaY < -0.3D)  tags.add(Tag.BUBBLE_DOWN);

        int blockX = (int) Math.floor(data.getX());
        int headY = (int) Math.floor(data.getY() + 1.62);
        int feetY = (int) Math.floor(data.getY());
        int blockZ = (int) Math.floor(data.getZ());

        boolean headInAir = !BlockPropertyRegistry.isLiquid(data.getWorldCache().getBlockState(blockX, headY, blockZ));
        boolean feetInWater = BlockPropertyRegistry.isLiquid(data.getWorldCache().getBlockState(blockX, feetY, blockZ));

        boolean atSurface = data.isServerGround() || data.isClientGround()
                || (airTicks == 0 && Math.abs(deltaY) < SURFACE_Y_THRESHOLD)
                || (headInAir && feetInWater);

        if (atSurface) {
            tags.add(Tag.SURFACE);
            if (Math.abs(deltaY) < 0.06D) tags.add(Tag.BOBBING);
        } else {
            tags.add(Tag.SUBMERGED);
        }

        if (data.isSwimming()) tags.add(Tag.SWIM_MODE);

        final int depthStriderLevel = data.getEnchantLevel("depth_strider");
        if (depthStriderLevel > 0 && tags.contains(Tag.WATER)) tags.add(Tag.DEPTH_STRIDER);

        if (data.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) tags.add(Tag.DOLPHIN_GRACE);
        if (data.getPotionLevel(PotionEffectType.SLOW_FALLING) > 0) tags.add(Tag.SLOW_FALLING);

        if (deltaY > 0.01D)  tags.add(Tag.ASCENDING);
        if (deltaY < -0.01D && !tags.contains(Tag.BUBBLE_DOWN)) tags.add(Tag.DESCENDING);
        if (data.isSprinting()) tags.add(Tag.SPRINTING);

        if (data.hasVelocity() || ticksNow - data.getLastVelocityTick() <= 5) tags.add(Tag.VELOCITY);
        if (data.getTicksSinceTeleport() < 5 || ticksNow - data.getLastVehicleExitTick() < 10) tags.add(Tag.TELEPORT_GRACE);
        if (ticksNow - st.liquidEntryTick <= LIQUID_ENTRY_GRACE_TICKS) tags.add(Tag.LIQUID_ENTRY);
        if (data.isExempt(ExemptionType.RIPTIDE) || ticksNow - data.getLastRiptideTick() < 15) tags.add(Tag.RIPTIDE);
        if (data.isOnClimbable()) tags.add(Tag.CLIMBABLE);
        if (isNearHorizontalCollision(data)) tags.add(Tag.WALL_TOUCH);

        if (tags.contains(Tag.TELEPORT_GRACE) || tags.contains(Tag.RIPTIDE) || tags.contains(Tag.CLIMBABLE)) {
            st.buffer = Math.max(0f, st.buffer - 0.1f);
            return;
        }

        if (tags.contains(Tag.VELOCITY)) {
            st.buffer     = Math.max(0f, st.buffer - 0.3f);
            st.hDragTicks = 0;
            st.vHoverTicks = 0;
            return;
        }

        if (tags.contains(Tag.LIQUID_ENTRY)) {
            st.buffer     = Math.max(0f, st.buffer - 0.15f);
            st.hDragTicks = 0;
            return;
        }

        boolean skipVertical = tags.contains(Tag.BUBBLE_UP) || tags.contains(Tag.BUBBLE_DOWN);

        String flagReason = null;
        double severity   = 0.0D;

        if (!skipVertical && !tags.contains(Tag.ASCENDING) && !tags.contains(Tag.SWIM_MODE)) {
            if (Math.abs(deltaY) < 1.0E-5D && airTicks == 0 && !data.isServerGround()) {
                st.vHoverTicks++;
                if (st.vHoverTicks >= 5) {
                    tags.add(Tag.V_HOVER);
                    flagReason = String.format("Area Fail (V_Hover) Y=%.5f hoverTicks=%d tags=%s", deltaY, st.vHoverTicks, tags);
                    severity = 2.5D;
                }
            } else {
                st.vHoverTicks = 0;
            }
        } else {
            st.vHoverTicks = 0;
        }

        if (flagReason == null && !skipVertical) {
            double maxUp = MAX_ASCENT_SPEED;

            // Allow jumping out of water ONLY if touching physical ground
            if (data.isServerGround() || data.isLastGround() || WorldUtils.isNearStairOrSlab(wrapper.getPlayer())) {
                maxUp = 0.45D;
            } else if (tags.contains(Tag.SURFACE)) {
                // Jesus strict limit: cannot jump 0.42 off deep water.
                // Vanilla water bounce is 0.04. Leniency to 0.1D.
                maxUp = 0.15D;
            }

            if (tags.contains(Tag.WALL_TOUCH) && tags.contains(Tag.ASCENDING)) {
                maxUp = Math.max(maxUp, WALL_ASCENT_MAX);
            }

            if (tags.contains(Tag.SWIM_MODE)) {
                maxUp = Math.max(maxUp, 0.55D);
                if (tags.contains(Tag.DOLPHIN_GRACE)) {
                    maxUp = Math.max(maxUp, 0.85D);
                }
            }

            double maxDown = MAX_DESCENT_SPEED;
            if (tags.contains(Tag.SLOW_FALLING)) maxDown = Math.max(maxDown, -0.1D);

            double yCap = 0.06D; // Increased from 0.04D - water surface exit needs more leniency
            if (tags.contains(Tag.SURFACE)) {
                yCap = 0.10D; // Extra leniency for surface bobbing/exit
            }
            if (tags.contains(Tag.WALL_TOUCH) && tags.contains(Tag.ASCENDING)) {
                yCap = Math.max(yCap, WALL_ASCENT_CAP);
            }
            if (deltaY > maxUp + yCap) {
                tags.add(Tag.V_CAP);
                double dev = deltaY - (maxUp + yCap);
                flagReason = String.format("Area Fail (V_Cap/Up) Y=%.3f max=%.3f dev=%.3f tags=%s", deltaY, maxUp, dev, tags);
                severity = 1.5D + dev * 10.0D;
            } else if (deltaY < maxDown - yCap) {
                tags.add(Tag.V_CAP);
                double dev = (maxDown - yCap) - deltaY;
                flagReason = String.format("Area Fail (V_Cap/Down) Y=%.3f min=%.3f dev=%.3f tags=%s", deltaY, maxDown, dev, tags);
                severity = 1.5D + dev * 10.0D;
            }
        }

        if (flagReason == null && !skipVertical && !tags.contains(Tag.SWIM_MODE)) {
            double predSink   = (lastDeltaY - LIQUID_GRAVITY) * LIQUID_Y_DRAG;
            double predAscend = (lastDeltaY + LIQUID_GRAVITY) * LIQUID_Y_DRAG;
            double predZero   = lastDeltaY * LIQUID_Y_DRAG;

            double vDiff = Math.abs(deltaY - predSink);
            vDiff = Math.min(vDiff, Math.abs(deltaY - predAscend));
            vDiff = Math.min(vDiff, Math.abs(deltaY - predZero));
            vDiff = Math.min(vDiff, Math.abs(deltaY));

            double threshold = tags.contains(Tag.DOLPHIN_GRACE) ? 0.25D : 0.05D;
            if (tags.contains(Tag.SWIM_MODE)) threshold += 0.15D;
            if (tags.contains(Tag.SPRINTING) && !tags.contains(Tag.SWIM_MODE)) threshold += 0.04D;

            // Allow surface bobbing
            if (tags.contains(Tag.SURFACE)) threshold += 0.06D;
            if (tags.contains(Tag.WALL_TOUCH)) threshold += 0.12D;

            double reducedDiff = Math.max(0.0D, vDiff - threshold);

            if (reducedDiff > NOISE_FLOOR) {
                tags.add(Tag.V_PREDICT);
                flagReason = String.format("Area Fail (V_Predict) Y=%.4f lastY=%.4f diff=%.4f tags=%s", deltaY, lastDeltaY, vDiff, tags);
                severity = 1.0D + reducedDiff * 12.0D;
            }
        }

        if (flagReason == null) {
            double maxXZ;
            if (tags.contains(Tag.LAVA)) {
                maxXZ = MAX_LAVA_XZ;
            } else {
                maxXZ = MAX_WATER_XZ_BASE;
                if (tags.contains(Tag.DEPTH_STRIDER)) maxXZ += depthStriderLevel * DEPTH_STRIDER_BONUS;

                if (tags.contains(Tag.SWIM_MODE)) {
                    maxXZ = Math.max(maxXZ, 0.55D);
                }
                if (tags.contains(Tag.DOLPHIN_GRACE)) {
                    maxXZ = Math.max(maxXZ, 0.95D);
                }
            }

            if (tags.contains(Tag.SURFACE) || tags.contains(Tag.BOBBING)) {
                // FIXED: Only boost speed on surface if they jumped from ground recently, else cap it.
                if (data.getAirTicks() <= 5) maxXZ += 0.1D;
            }

            if (deltaXZ > maxXZ + 0.05D) {
                tags.add(Tag.H_CAP);
                double dev = deltaXZ - maxXZ;
                flagReason = String.format("Area Fail (H_Cap) XZ=%.3f max=%.3f dev=%.3f tags=%s", deltaXZ, maxXZ, dev, tags);
                severity = 1.5D + dev * 8.0D;
            }
        }

        if (flagReason == null && !tags.contains(Tag.DOLPHIN_GRACE) && !tags.contains(Tag.SURFACE) && !tags.contains(Tag.BOBBING)) {
            double maxInput;
            if (tags.contains(Tag.LAVA)) {
                maxInput = 0.08D;
            } else {
                maxInput = tags.contains(Tag.SWIM_MODE) ? 0.25D : 0.15D;
                if (tags.contains(Tag.DEPTH_STRIDER)) maxInput += depthStriderLevel * 0.04D;
                if (tags.contains(Tag.DOLPHIN_GRACE)) maxInput += 0.2D;
            }

            double expectedMax = lastDeltaXZ * LIQUID_XZ_DRAG + maxInput + 0.04D;
            if (deltaXZ > expectedMax) {
                st.hDragTicks++;
                if (st.hDragTicks >= 3) {
                    tags.add(Tag.H_DRAG);
                    double dev = deltaXZ - expectedMax;
                    flagReason = String.format("Area Fail (H_Drag) XZ=%.3f expect≤%.3f consecutive=%d tags=%s", deltaXZ, expectedMax, st.hDragTicks, tags);
                    severity = 1.0D + dev * 6.0D;
                }
            } else {
                st.hDragTicks = 0;
            }
        } else {
            st.hDragTicks = 0;
        }

        if (flagReason != null) {
            st.buffer += (float) severity;
            if (st.buffer > FLAG_BUFFER_THRESHOLD) {
                flag(data, flagReason + String.format(" buffer=%.2f", st.buffer));
                if (Truthful.getInstance().getConfiguration().isLagbacks()) data.executeLagback();
                st.buffer = Math.min(st.buffer, FLAG_BUFFER_RESET);
            }
        } else {
            float decay = (tags.contains(Tag.SURFACE) || tags.contains(Tag.BOBBING)
                    || tags.contains(Tag.BUBBLE_UP) || tags.contains(Tag.BUBBLE_DOWN)
                    || tags.contains(Tag.WALL_TOUCH)) ? 0.18f : 0.08f;
            st.buffer = Math.max(0f, st.buffer - decay);
        }
    }

    private boolean isNearHorizontalCollision(final PlayerData data) {
        final double x = data.getX();
        final double y = data.getY();
        final double z = data.getZ();
        final double radius = 0.36D;

        return hasSolid(data, x + radius, y + 0.20D, z)
                || hasSolid(data, x - radius, y + 0.20D, z)
                || hasSolid(data, x, y + 0.20D, z + radius)
                || hasSolid(data, x, y + 0.20D, z - radius)
                || hasSolid(data, x + radius, y + 1.00D, z)
                || hasSolid(data, x - radius, y + 1.00D, z)
                || hasSolid(data, x, y + 1.00D, z + radius)
                || hasSolid(data, x, y + 1.00D, z - radius);
    }

    private boolean hasSolid(final PlayerData data, final double x, final double y, final double z) {
        return BlockPropertyRegistry.isSolid(data.getWorldCache().getBlockState(
                floor(x),
                floor(y),
                floor(z)
        ));
    }

    private boolean isInLava(final PlayerData data) {
        int x = floor(data.getX());
        int z = floor(data.getZ());
        int feetY = floor(data.getY());
        int midY = floor(data.getY() + 0.5D);
        int headY = floor(data.getY() + 1.62D);
        return isLavaState(data, x, feetY, z)
                || isLavaState(data, x, midY, z)
                || isLavaState(data, x, headY, z);
    }

    private boolean isLavaState(final PlayerData data, final int x, final int y, final int z) {
        return data.getWorldCache().getBlockState(x, y, z).getType().getName().toUpperCase().contains("LAVA");
    }

    private int floor(final double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }
}
