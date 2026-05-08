package ret.tawny.truthful.wrapper.impl.client.sync;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import org.bukkit.entity.Player;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

public final class ConfirmTransactionPacketWrapper extends PacketWrapper {
    /**
     * Window In ID, for example inventory is 0
     */
    private final int windowId;
    /**
     * Transaction ID
     */
    private final int uid;
    /**
     * Accepted should always be true when incoming
     */
    private final boolean accepted;

    public ConfirmTransactionPacketWrapper(Object wrapper, Player player, PacketType.Play.Client type) {
        super(wrapper, player, type);

        if (wrapper instanceof WrapperPlayClientWindowConfirmation) {
            WrapperPlayClientWindowConfirmation transaction = (WrapperPlayClientWindowConfirmation) wrapper;
            this.windowId = transaction.getWindowId();
            this.uid = transaction.getActionId();
            this.accepted = transaction.isAccepted();
        } else if (wrapper instanceof WrapperPlayClientPong) {
            // Modern ping system (1.17+)
            WrapperPlayClientPong pong = (WrapperPlayClientPong) wrapper;
            this.windowId = 0;
            this.uid = pong.getId();
            this.accepted = true;
        } else {
            this.windowId = 0;
            this.uid = -1;
            this.accepted = false;
        }
    }

    public int getWindowId() {
        return windowId;
    }

    public int getUid() {
        return uid;
    }

    public boolean isAccepted() {
        return accepted;
    }
}
