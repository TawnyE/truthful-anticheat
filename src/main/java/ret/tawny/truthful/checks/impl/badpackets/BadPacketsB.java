package ret.tawny.truthful.checks.impl.badpackets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;

@CheckData(order = 'B', type = CheckType.BAD_PACKET)
@SuppressWarnings("unused")
public final class BadPacketsB extends Check {

    @Override
    public void handlePacketPlayerReceive(final PacketEvent packetEvent) {
        if (!packetEvent.getPacketType().equals(PacketType.Play.Client.HELD_ITEM_SLOT))
            return;

        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(packetEvent.getPlayer());
        if (playerData == null)
            return;

        // EXEMPTION: Do not check players who have just joined.
        if (playerData.getTicksTracked() < 100) {
            return;
        }

        if (playerData.getCurrentSlot() == playerData.getLastSlot()) {
            flag(playerData, "Invalid Slot Switch (switched to the same slot)");
        }
    }
}