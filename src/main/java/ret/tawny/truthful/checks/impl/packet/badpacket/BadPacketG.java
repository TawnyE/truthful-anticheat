package ret.tawny.truthful.checks.impl.packet.badpacket;

import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;


@CheckData(order = 'G', type = CheckType.BAD_PACKET)
public final class BadPacketG extends Check {

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate())
            return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isServerFrozen() || data.isTeleportTick())
            return;

        double x = wrapper.getX();
        double y = wrapper.getY();
        double z = wrapper.getZ();

        // Check for NaN
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            flag(data, String.format("NaN Position. X: %s, Y: %s, Z: %s", x, y, z));
            data.executeLagback();
            return;
        }

        // Check for Infinity
        if (Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
            flag(data, String.format("Infinite Position. X: %s, Y: %s, Z: %s", x, y, z));
            data.executeLagback();
            return;
        }

        // Check for absurdly large values (potential overflow/exploit)
        double maxCoord = 30000000.0; // Slightly beyond world border limit
        if (Math.abs(x) > maxCoord || Math.abs(z) > maxCoord) {
            flag(data, String.format("Extreme Position. X: %.0f, Z: %.0f", x, z));
            data.executeLagback();
            return;
        }

        // Y bounds check using world limits (+/-64 safety margin for custom dimensions)
        int minY = data.getWorldMinHeight() - 64;
        int maxY = data.getWorldMaxHeight() + 64;
        if (y < minY || y > maxY) {
            flag(data, String.format("Invalid Y Position: %.2f", y));
            data.executeLagback();
        }
    }
}
