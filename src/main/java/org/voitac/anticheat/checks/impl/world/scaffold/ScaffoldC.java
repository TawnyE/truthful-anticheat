package org.voitac.anticheat.checks.impl.world.scaffold;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.utils.player.PlayerUtils;
import org.voitac.anticheat.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;

// Simple sprint check, kinda sped but should never false. It will flag someone who is using strafe even if they aren't sprinting but that's a positive
@CheckData(order = 'C', type = CheckType.SCAFFOLD)
public final class ScaffoldC extends Check {

    @Override
    public void onPacketPlayerReceive(final PacketEvent event) {
        if(!event.getPacketType().equals(PacketType.Play.Client.BLOCK_PLACE))
            return;
        final PlayerBlockPlacePacketWrapper blockPlacePacketWrapper = new PlayerBlockPlacePacketWrapper(event);
        final Player player = event.getPlayer();

        if(player.isFlying())
            return;
        final PlayerData data = AntiCheat.getInstance().getDataManager().getPlayerData(player);

        // TODO
        // Easily bypassed by turning down speed a tiny bit, may need to simply remove this check and have it all as one sprint check
        if(!PlayerUtils.isSprinting(data))
            return;

        final boolean flag;

        switch(blockPlacePacketWrapper.getBlockFace()) {
            case EAST:
                flag = data.getDeltaX() > 0;
                break;

            case SOUTH:
                flag = data.getDeltaZ() > 0;
                break;

            case WEST:
                flag = data.getDeltaX() < 0;
                break;

            case NORTH:
                flag = data.getDeltaZ() < 0;
                break;

            default:
                flag = false;
                break;
        }
        if(flag)
            formattedFlag(this, data, "Impossible sprinting whilst placing");
    }
}
