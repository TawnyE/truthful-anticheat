package ret.tawny.truthful.checks.impl.packet.invalid;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.data.PlayerData;

/**
 * InvalidB - Invalid Slot Switch Detection
 * Detects switching to the same slot (invalid action)
 */
@CheckData(order = 'B', type = CheckType.INVALID)
public final class InvalidB extends Check {

    private final CheckBuffer buffer = new CheckBuffer(5.0);

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent packetEvent) {
        if (packetEvent.getPacketType() != PacketType.Play.Client.HELD_ITEM_CHANGE)
            return;

        final org.bukkit.entity.Player player = (org.bukkit.entity.Player) packetEvent.getPlayer();
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (playerData == null)
            return;

        com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange wrapper = new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange(
                packetEvent);

        int slot = wrapper.getSlot();

        // 1. Bounds Check
        if (slot < 0 || slot > 8) {
            flag(playerData, "Invalid Slot ID: " + slot);
            return;
        }

        // 2. Redundancy Check removed (false positives with rapid inputs/resyncs)
        /*
         * if (slot == playerData.getLastSlot()) {
         * if (buffer.increase(player, 1.0) > 4.0) {
         * flag(playerData, "Redundant Slot Switch");
         * buffer.reset(player, 2.0);
         * }
         * } else {
         * buffer.decrease(player, 0.1);
         * }
         */
    }
}
