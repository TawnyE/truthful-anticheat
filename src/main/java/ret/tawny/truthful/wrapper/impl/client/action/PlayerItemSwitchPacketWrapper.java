package ret.tawny.truthful.wrapper.impl.client.action;

import com.comphenix.protocol.events.PacketEvent;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

import java.util.List;

public final class PlayerItemSwitchPacketWrapper extends PacketWrapper {
    /**
     * Slot the player is switching to on their hotbar
     */
    private final int slot;

    public PlayerItemSwitchPacketWrapper(final PacketEvent packetEvent) {
        super(packetEvent);
        final List<Integer> integersIn = packetContainer.getIntegers().getValues();
        this.slot = integersIn.get(0);
    }

    /**
     *
     * @return HotBar slot the player has switched too
     */
    public int getSlot() {
        return slot;
    }
}
