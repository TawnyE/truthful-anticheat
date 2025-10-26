package org.voitac.anticheat.checks.impl.badpackets;

import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;

// TODO
// Make all bad packet checks instant ban
@CheckData(order = 'A', type = CheckType.BAD_PACKET)
public final class BadPacketsA extends Check {

    @Override
    public void onRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        if(!relMovePacketWrapper.isRotationUpdate())
            return;

        final double pitch = relMovePacketWrapper.getPitch();

        if(Math.abs(pitch) > 90.0D) {
            final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(relMovePacketWrapper.getPlayer());
            if(playerData != null) {
                formattedFlag(this, playerData, "Invalid pitch " + pitch);
            }
        }
    }

}
