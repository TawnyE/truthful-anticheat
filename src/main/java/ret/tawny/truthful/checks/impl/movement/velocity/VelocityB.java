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
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'B', type = CheckType.VELOCITY)
public final class VelocityB extends Check {

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

        double deltaY = data.getDeltaY();
        boolean flagged = false;
        double severity = 0;
        String flagReason = null;

        for (VelocityQueue.VelocityEntry entry : queue) {
            if (entry.isAcked() && entry.getAckTick() <= 1 && !entry.isExplosion()) {
                Vector initialVel = entry.getCurrent();
                double expectedY = initialVel.getY();

                // Check blocks above and general edge cases where a jump gets interrupted
                if (expectedY < 0.1D || data.isUnderBlock() || WorldUtils.isNearStairOrSlab(data.getPlayer())) {
                    continue;
                }

                // Lower yield to 20% to account for heavy gravity manipulation or partial edge steps
                double minYield = expectedY * 0.20D;

                if (deltaY < minYield && !data.isServerGround() && !data.isLastGround()) {
                    flagged = true;
                    severity += (minYield - deltaY) * 18.0D;
                    flagReason = String.format("Vertical 0%% yield Y=%.4f expected=%.4f", deltaY, minYield);
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

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}