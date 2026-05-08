package ret.tawny.truthful.checks.impl.packet.badpacket;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;

/**
 * BadPacketK: Negative Transaction ID Detection
 *
 * Detects when a player sends negative transaction/confirmation IDs.
 * This is a known crash exploit and protocol manipulation technique.
 *
 * Transaction IDs should always be positive or zero in normal gameplay.
 * Negative IDs can cause:
 * 1. Integer overflow exploits
 * 2. Server crash attempts
 * 3. Inventory duplication glitches
 *
 * Version safe: All versions 1.8+
 * Note: In 1.17+ the packet was renamed, but PacketEvents handles this.
 */
@CheckData(order = 'K', type = CheckType.BAD_PACKET)
public final class BadPacketK extends Check {

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        // Validate WINDOW_CONFIRMATION transaction packets (legacy + modern mapping via PacketEvents)
        if (event.getPacketType() != PacketType.Play.Client.WINDOW_CONFIRMATION)
            return;

        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isServerFrozen() || data.isTeleportTick())
            return;

        WrapperPlayClientWindowConfirmation wrapper = new WrapperPlayClientWindowConfirmation(event);
        short actionId = wrapper.getActionId();

        // Negative action IDs are suspicious (strictly should be positive for our AC
        // transactions)
        if (actionId < 0) {
            flag(data, "Negative Transaction ID: " + actionId);
            event.setCancelled(true);
        }

        // Check window ID validity (should be 0-255 matches byte)
        int windowId = wrapper.getWindowId();
        if (windowId < 0 || windowId > 255) {
            flag(data, "Invalid Window ID: " + windowId);
            event.setCancelled(true);
        }
    }
}
