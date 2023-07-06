package org.voitac.anticheat.checks.impl.badpackets;

import org.bukkit.entity.Player;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;

// TODO
// Make all bad packet checks instant ban
public final class BadPacketsA extends Check {

    @Override
    public void onRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        final Player player = relMovePacketWrapper.getPlayer();

        if(Math.abs(player.getLocation().getPitch()) > 90)
            formattedFlag(this, AntiCheat.getInstance().getDataManager().getPlayerData(player));
    }

}
