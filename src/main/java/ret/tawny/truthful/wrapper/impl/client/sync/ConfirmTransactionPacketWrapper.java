package ret.tawny.truthful.wrapper.impl.client.sync;

import com.comphenix.protocol.events.PacketEvent;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

public final class ConfirmTransactionPacketWrapper extends PacketWrapper {
    /**
     * Window In ID, for example inventory is 0
     */
    private final int windowId;
    /**
     * Transaction ID
     */
    private final short uid;
    /**
     * Accepted should always be true when incoming
     */
    private final boolean accepted;

    public ConfirmTransactionPacketWrapper(final PacketEvent packetEvent) {
        super(packetEvent);
        this.windowId = this.packetContainer.getIntegers().getValues().get(0);
        this.uid = this.packetContainer.getShorts().getValues().get(0);
        this.accepted = this.packetContainer.getBooleans().getValues().get(0);
    }

    public int getWindowId() {
        return windowId;
    }

    public short getUid() {
        return uid;
    }

    public boolean isAccepted() {
        return accepted;
    }
}
