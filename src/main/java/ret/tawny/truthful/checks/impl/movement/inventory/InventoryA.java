package ret.tawny.truthful.checks.impl.movement.inventory;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.checks.impl.movement.MovementCheckSupport;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'A', type = CheckType.INVENTORY)
public final class InventoryA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;

        final PlayerData data = wrapper.getPlayerData();
        if (MovementCheckSupport.skipForPrediction(data) || data.isGliding() || data.hasVelocity()) return;

        long now = System.currentTimeMillis();
        long clickDelta = now - data.getLastWindowClick();
        if (clickDelta >= 200L) {
            buffer.decrease(wrapper.getPlayer(), 0.1D);
            return;
        }

        if (now - data.getActionTracker().getLastInventoryClose() < 250L) {
            buffer.decrease(wrapper.getPlayer(), 0.2D);
            return;
        }

        double move = data.getDeltaXZ();
        double allowed = data.isOnGround() ? 0.15D : 0.12D;
        if (data.getGroundTicks() < 4) allowed += 0.05D;

        if (move > allowed) {
            if (buffer.increase(wrapper.getPlayer(), 1.0D) > 5.0D) {
                flag(data, String.format("Inventory move speed=%.3f limit=%.3f clickDelta=%dms", move, allowed, clickDelta));
                if (Truthful.getInstance().getConfiguration().isLagbacks()) data.executeLagback();
                buffer.reset(wrapper.getPlayer(), 2.0D);
            }
        } else {
            buffer.decrease(wrapper.getPlayer(), 0.2D);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}
