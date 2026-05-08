package ret.tawny.truthful.checks.impl.movement.velocity;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.sync.VelocityQueue;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'D', type = CheckType.VELOCITY)
public final class VelocityD extends Check {

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
            if (!entry.isAcked()) {
                int ticksPending = entry.getAckTick();
                if (ticksPending > 5 && deltaXZ > 0.12D) {
                    flagged = true;
                    severity += deltaXZ * 12.0D;
                    flagReason = String.format("Moving while withholding velocity ACKs (pending=%d) dist=%.4f",
                            ticksPending, deltaXZ);
                }
            }
        }

        if (flagged) {
            flag(data, flagReason);
            if (buffer.increase(data.getPlayer(), severity) > 8.0D) {
                if (Truthful.getInstance().getConfiguration().isLagbacks()) data.executeLagback();
                buffer.reset(data.getPlayer(), 2.0D);
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