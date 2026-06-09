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

@CheckData(order = 'C', type = CheckType.VELOCITY)
public final class VelocityC extends Check {

    private final CheckBuffer buffer = new CheckBuffer(15.0D);

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
            int tick = entry.getAckTick();
            if (entry.isAcked() && tick >= 2 && tick <= 3 && !entry.isExplosion()) {
                Vector currentExpected = entry.getCurrent();
                double expectedXZ = Math.hypot(currentExpected.getX(), currentExpected.getZ());

                if (expectedXZ < 0.05D || data.isUnderBlock() || data.isServerGround() || isNearWall(data)) {
                    continue;
                }

                double minYield = expectedXZ * 0.15D;
                if (!data.isServerGround()) {
                    minYield = expectedXZ * 0.02D;
                }

                if (deltaXZ < minYield) {
                    flagged = true;
                    severity += (minYield - deltaXZ) * 15.0D;
                    flagReason = String.format("Atypical velocity deceleration on tick %d XZ=%.4f exp=%.4f", tick, deltaXZ, minYield);
                }
            }
        }

        if (flagged) {
            flag(data, flagReason);
            if (buffer.increase(data.getPlayer(), severity) > 6.0D) {
                buffer.reset(data.getPlayer(), 2.0D);
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
                if (ox == 0 && oz == 0) continue;
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