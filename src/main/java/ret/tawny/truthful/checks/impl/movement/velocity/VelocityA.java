package ret.tawny.truthful.checks.impl.movement.velocity;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.sync.VelocityQueue;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'A', type = CheckType.VELOCITY)
public final class VelocityA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0D);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;
        final PlayerData data = wrapper.getPlayerData();
        if (data == null) return;

        if (data.isServerFrozen() || data.isTeleportTick()) {
            buffer.decrease(data.getPlayer(), 0.25D);
            return;
        }

        VelocityQueue queue = data.getVelocities();
        if (queue.isEmpty()) {
            return;
        }

        double deltaXZ = data.getDeltaXZ();
        boolean flagged = false;
        double severity = 0;
        String flagReason = null;

        for (VelocityQueue.VelocityEntry entry : queue) {
            if (entry.isAcked() && entry.getAckTick() <= 1 && !entry.isExplosion()) {
                Vector initialVel = entry.getCurrent();
                double expectedXZ = Math.hypot(initialVel.getX(), initialVel.getZ());

                // FIXED: Check 3x3 footprint to catch diagonal wall bumps
                if (expectedXZ < 0.1D || data.isUnderBlock() || isNearWall(data)) {
                    continue;
                }

                double minYield = expectedXZ * 0.15D;
                // If they get hit mid-air, they can use air strafing to counter it almost completely
                if (!data.isServerGround()) {
                    minYield = expectedXZ * 0.02D;
                }

                if (deltaXZ < minYield) {
                    flagged = true;
                    severity += (minYield - deltaXZ) * 18.0D;
                    flagReason = String.format("Horizontal 0%% yield XZ=%.4f expected=%.4f", deltaXZ, minYield);
                }
            }
        }

        if (flagged) {
            flag(data, flagReason);
            if (buffer.increase(data.getPlayer(), severity) > 5.0D) {
                if (Truthful.getInstance().getConfiguration().isLagbacks()) data.executeLagback();
                buffer.reset(data.getPlayer(), 1.0D);
            }
        } else {
            buffer.decrease(data.getPlayer(), 0.1D);
        }
    }

    private boolean isNearWall(PlayerData data) {
        int px = (int) Math.floor(data.getX());
        int py = (int) Math.floor(data.getY());
        int pz = (int) Math.floor(data.getZ());

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                if (ox == 0 && oz == 0) continue; // Skip dead center
                if (ret.tawny.truthful.utils.world.BlockPropertyRegistry.isSolid(data.getWorldCache().getBlockState(px + ox, py, pz + oz)) ||
                        ret.tawny.truthful.utils.world.BlockPropertyRegistry.isSolid(data.getWorldCache().getBlockState(px + ox, py + 1, pz + oz))) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}