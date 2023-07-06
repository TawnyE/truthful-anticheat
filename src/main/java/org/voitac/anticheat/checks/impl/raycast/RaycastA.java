package org.voitac.anticheat.checks.impl.raycast;

import com.comphenix.protocol.PacketType;
import org.bukkit.entity.Player;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;

@CheckData(order = 'B', type = CheckType.AIM)
public final class RaycastA extends Check {

    public RaycastA() {
        AntiCheat.getInstance().getScheduler().registerDispatcher(packetEvent -> {
            final PlayerBlockPlacePacketWrapper blockPlacePacketWrapper = new PlayerBlockPlacePacketWrapper(packetEvent);
            final Player player = packetEvent.getPlayer();


        }, PacketType.Play.Client.BLOCK_PLACE);
    }

}
