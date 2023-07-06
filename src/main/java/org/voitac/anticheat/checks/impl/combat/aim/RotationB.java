package org.voitac.anticheat.checks.impl.combat.aim;

import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'B', type = CheckType.AIM)
public final class RotationB extends Check {

    @Override
    public void onRelMove(final RelMovePacketWrapper movePacketWrapper) {
        if(!movePacketWrapper.isRotationUpdate())
            return;
        final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(movePacketWrapper.getPlayer());


    }
}
