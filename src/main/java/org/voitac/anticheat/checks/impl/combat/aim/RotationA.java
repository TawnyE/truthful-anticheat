package org.voitac.anticheat.checks.impl.combat.aim;

import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;

// Simple Rotation Snap check, should detect shitty clients
@CheckData(order = 'A', type = CheckType.AIM)
public final class RotationA extends Check {
    @Override
    public void onRelMove(final RelMovePacketWrapper movePacketWrapper) {
        if(!movePacketWrapper.isRotationUpdate())
            return;
        final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(movePacketWrapper.getPlayer());

        // Shouldn't ever false on vanilla as a small amount of last ticks rotation carries over if the last mouseX delta was > value
        if(Math.abs(playerData.getLastDeltaYaw()) > 45.0F && Math.abs(playerData.getDeltaYaw()) < 1.0F) {
            formattedFlag(this, playerData, "Player Snapped to a location without easing");
        }
    }
}
