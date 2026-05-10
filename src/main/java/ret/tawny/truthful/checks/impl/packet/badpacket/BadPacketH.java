package ret.tawny.truthful.checks.impl.packet.badpacket;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;


@CheckData(order = 'H', type = CheckType.BAD_PACKET)
public final class BadPacketH extends Check {

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.HELD_ITEM_CHANGE)
            return;

        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null)
            return;

        WrapperPlayClientHeldItemChange wrapper = new WrapperPlayClientHeldItemChange(event);
        int slot = wrapper.getSlot();

        // Valid hotbar slots are 0-8
        if (slot < 0 || slot > 8) {
            flag(data, "Invalid Hotbar Slot: " + slot + " (Valid: 0-8)");
            event.setCancelled(true);
        }
    }
}
