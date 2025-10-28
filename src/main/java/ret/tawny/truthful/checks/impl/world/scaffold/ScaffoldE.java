package ret.tawny.truthful.checks.impl.world.scaffold;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;

@CheckData(order = 'E', type = CheckType.SCAFFOLD)
@SuppressWarnings("unused")
public final class ScaffoldE extends Check {

    @Override
    public void handlePacketPlayerReceive(final PacketEvent event) {
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(event.getPlayer());
        if (data == null) return;

        // Track when the player switches their item slot
        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_SLOT) {
            data.setLastSlotSwitchTime(System.currentTimeMillis());
        }

        // When they place a block, check how long it has been since they switched
        if (event.getPacketType() == PacketType.Play.Client.BLOCK_PLACE) {
            long timeSinceSwitch = System.currentTimeMillis() - data.getLastSlotSwitchTime();

            // A human cannot switch items and place a block in under ~50ms consistently.
            // We check for an impossibly low time.
            if (timeSinceSwitch < 5) {
                flag(data, "Impossibly fast item switch and place. Time: " + timeSinceSwitch + "ms");
            }
        }
    }
}