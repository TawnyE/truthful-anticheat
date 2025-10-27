package ret.tawny.truthful.checks.impl.badpackets;

import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'A', type = CheckType.BAD_PACKET)
@SuppressWarnings("unused")
public final class BadPacketsA extends Check {

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        if (!relMovePacketWrapper.isRotationUpdate()) {
            return;
        }

        final double pitch = relMovePacketWrapper.getPitch();

        if (Math.abs(pitch) > 90.0D) {
            final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(relMovePacketWrapper.getPlayer());
            if (playerData != null) {
                // Corrected method call
                flag(playerData, "Invalid pitch " + pitch);
            }
        }
    }
}