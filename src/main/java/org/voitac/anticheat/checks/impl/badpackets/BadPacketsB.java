package org.voitac.anticheat.checks.impl.badpackets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.wrapper.impl.client.action.PlayerItemSwitchPacketWrapper;

// Impossible Slot Change
@CheckData(order = 'B', type = CheckType.BAD_PACKET)
public final class BadPacketsB extends Check {

    @Override
    public void onPacketPlayerReceive(final PacketEvent packetEvent) {
        if(!packetEvent.getPacketType().equals(PacketType.Play.Client.HELD_ITEM_SLOT))
            return;
        final PlayerItemSwitchPacketWrapper itemSwitchPacketWrapper = new PlayerItemSwitchPacketWrapper(packetEvent);

        final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(packetEvent.getPlayer());
        if(playerData == null)
            return;

        if(playerData.getCurrentSlot() == playerData.getLastSlot())
            formattedFlag(this, playerData, "Invalid Slot Switch");
    }

}
