package ret.tawny.truthful.checks.impl.movement.velocity;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.sync.VelocityQueue;
import ret.tawny.truthful.utils.world.BlockPropertyRegistry;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'A', type = CheckType.VELOCITY)
public final class VelocityA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0D);

    private static final double[][] INPUT_VECTORS = {
            {0.0, 0.0}, {0.0, 1.0}, {0.0, -1.0}, {1.0, 0.0}, {-1.0, 0.0},
            {1.0, 1.0}, {-1.0, 1.0}, {1.0, -1.0}, {-1.0, -1.0}
    };

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;
        final PlayerData data = wrapper.getPlayerData();
        if (data == null) return;

        if (data.isServerFrozen() || data.isTeleportTick() || data.isMovementExempt()) {
            buffer.decrease(data.getPlayer(), 0.25D);
            return;
        }

        VelocityQueue queue = data.getVelocities();
        if (queue.isEmpty()) return;

        final double actualX = data.getDeltaX();
        final double actualZ = data.getDeltaZ();
        final float yaw = data.getYaw();
        final boolean onGround = data.isServerGround();

        boolean flagged = false;
        double minVectorDev = Double.MAX_VALUE;
        String flagReason = null;

        for (VelocityQueue.VelocityEntry entry : queue) {
            if (entry.isAcked() && entry.getAckTick() <= 1 && !entry.isExplosion()) {
                Vector initialVel = entry.getCurrent();
                double velXZ = Math.hypot(initialVel.getX(), initialVel.getZ());

                if (velXZ < 0.05D || data.isUnderBlock()) continue;

                double friction = onGround ? computeGroundFriction(data) : 0.91D;
                double lastX = data.getLastDeltaX() * friction;
                double lastZ = data.getLastDeltaZ() * friction;

                for (double[] input : INPUT_VECTORS) {
                    double strafe = input[0];
                    double forward = input[1];

                    double len = Math.hypot(strafe, forward);
                    if (len >= 1.0D) {
                        strafe /= len;
                        forward /= len;
                    }

                    double accelFactor = computeAccelFactor(data, onGround, friction);

                    double yawRad = Math.toRadians(yaw);
                    double sinYaw = Math.sin(yawRad);
                    double cosYaw = Math.cos(yawRad);

                    double inputX = (strafe * cosYaw - forward * sinYaw) * accelFactor;
                    double inputZ = (forward * cosYaw + strafe * sinYaw) * accelFactor;

                    double simX = lastX + initialVel.getX() + inputX;
                    double simZ = lastZ + initialVel.getZ() + inputZ;

                    if (isWallObstructed(data, simX, simZ)) {
                        if (Math.abs(actualX) < 0.01) simX = actualX;
                        if (Math.abs(actualZ) < 0.01) simZ = actualZ;
                    }

                    double vecDev = Math.hypot(actualX - simX, actualZ - simZ);
                    if (vecDev < minVectorDev) {
                        minVectorDev = vecDev;
                    }
                }

                long ping = data.getPing();
                double pingGrace = ping > 80 ? Math.min(0.12D, (ping / 50.0) * 0.035D) : 0.0D;
                double entityGrace = data.isNearEntity() ? 0.08D : 0.0D;
                double allowedDev = 0.08D + (onGround ? 0.04D : 0.02D) + pingGrace + entityGrace;

                if (minVectorDev > allowedDev) {
                    flagged = true;
                    flagReason = String.format("Horizontal Vector Fail vecDev=%.4f allowed=%.4f velXZ=%.3f (rtt=%dms nearEntity=%s)",
                            minVectorDev, allowedDev, velXZ, ping, data.isNearEntity());
                }
            }
        }

        if (flagged) {
            flag(data, flagReason, 2.0 + (minVectorDev * 10.0));
            if (buffer.increase(data.getPlayer(), 1.5) > 5.0D) {
                buffer.reset(data.getPlayer(), 2.0D);
            }
        } else {
            buffer.decrease(data.getPlayer(), 0.2D);
        }
    }

    private double computeAccelFactor(PlayerData data, boolean onGround, double friction) {
        double base = data.getWalkSpeed();
        int speedLevel = data.getPotionLevel(org.bukkit.potion.PotionEffectType.SPEED);
        if (speedLevel > 0) base *= 1.0D + 0.20D * speedLevel;
        if (data.isSprinting()) base *= 1.30D;
        if (data.isSneaking()) base *= 0.30D;

        if (onGround) {
            double f3 = Math.max(0.048D, friction * friction * friction);
            return base * (0.16277136D / f3);
        }
        return data.isSprinting() ? 0.026D : 0.020D;
    }

    private double computeGroundFriction(PlayerData data) {
        int x = (int) Math.floor(data.getX());
        int y = (int) Math.floor(data.getY() - 0.2D);
        int z = (int) Math.floor(data.getZ());
        return BlockPropertyRegistry.getFriction(data.getWorldCache().getBlockState(x, y, z)) * 0.91D;
    }

    private boolean isWallObstructed(PlayerData data, double simX, double simZ) {
        int px = (int) Math.floor(data.getX());
        int py = (int) Math.floor(data.getY());
        int pz = (int) Math.floor(data.getZ());

        int ox = simX > 0 ? 1 : -1;
        int oz = simZ > 0 ? 1 : -1;

        return BlockPropertyRegistry.isSolid(data.getWorldCache().getBlockState(px + ox, py, pz)) ||
                BlockPropertyRegistry.isSolid(data.getWorldCache().getBlockState(px, py, pz + oz));
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}