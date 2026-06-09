package ret.tawny.truthful.checks.impl.movement.spoof;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

/**
 * GroundSpoofC - Falling Ground Claim
 *
 * Detects players claiming to be on ground while having significant downward velocity.
 */
@CheckData(order = 'C', type = CheckType.SPOOF)
public final class GroundSpoofC extends Check {

    private final CheckBuffer buffer = new CheckBuffer(6.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || data.isServerFrozen() || data.isTeleportTick()) return;
        if (data.isAllowFlight() || data.isInsideVehicle() || data.isGliding()) return;

        if (data.isNearVehicle() || data.isNearEntity()) return;

        if (wrapper.isGround()) {
            double deltaY = data.getDeltaY();

            // If falling fast but claiming ground
            if (deltaY < -0.15 && !data.isOnGround()) {
                if (buffer.increase(player, 1.0) > 6.0) {
                    flag(data, String.format("Ground Spoof (Falling). dY: %.4f", deltaY));
                    buffer.reset(player, 3.0);
                }
            } else {
                buffer.decrease(player, 0.5);
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}