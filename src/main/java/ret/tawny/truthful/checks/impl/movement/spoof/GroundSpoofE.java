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
 * GroundSpoofE - Fall Distance Spoofing
 *
 * Targets NoFall hacks that toggle ground status momentarily to reset fall distance.
 */
@CheckData(order = 'E', type = CheckType.SPOOF)
public final class GroundSpoofE extends Check {

    private final CheckBuffer buffer = new CheckBuffer(5.0);
    private float serverFallDistance = 0;

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isServerFrozen()) return;

        if (data.isAllowFlight() || data.isFlying() || data.isInsideVehicle() || data.isGliding()) {
            serverFallDistance = 0;
            return;
        }

        if (data.isInLiquid() || data.isOnClimbable() || data.isTeleportTick()) {
            serverFallDistance = 0;
            return;
        }

        double deltaY = data.getDeltaY();

        if (data.isServerGround()) {
            serverFallDistance = 0;
        } else if (deltaY < 0) {
            serverFallDistance += (float) -deltaY;
        }

        // Logic: Server sees significant fall, Client claims ground
        if (serverFallDistance > 3.5 && wrapper.isGround()) {
            // Leniency for edge landing
            if (data.isOnGround()) return;

            if (buffer.increase(player, 1.0) > 3.0) {
                flag(data, String.format("Fall Distance Spoof. Server: %.2f", serverFallDistance));
                buffer.reset(player, 1.0);
            }
        } else {
            buffer.decrease(player, 0.1);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}