package ret.tawny.truthful.wrapper.impl.client.sync;

import com.comphenix.protocol.events.PacketEvent;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

public final class KeepAlivePacketWrapper extends PacketWrapper {
    /**
     * Keep Alive Response Key
     */
    private final int key;

    /**
     *
     * @param packetEvent - Inbound Keep Alive Packet Event
     */
    public KeepAlivePacketWrapper(final PacketEvent packetEvent) {
        super(packetEvent);
        this.key = this.packetContainer.getIntegers().getValues().get(0);
    }

    /**
     *
     * @return Keep Alive Key
     */
    public int getKey() {
        return key;
    }
}
