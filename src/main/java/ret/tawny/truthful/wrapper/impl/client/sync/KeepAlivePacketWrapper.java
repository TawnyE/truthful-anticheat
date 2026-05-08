package ret.tawny.truthful.wrapper.impl.client.sync;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import org.bukkit.entity.Player;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

public final class KeepAlivePacketWrapper extends PacketWrapper {
    /**
     * Unique keep-alive identifier
     */
    private final long timestamp;

    /**
     * @param wrapper - Inbound Keep Alive/Pong Packet
     */
    public KeepAlivePacketWrapper(Object wrapper, Player player, PacketType.Play.Client type) {
        super(wrapper, player, type);

        if (wrapper instanceof WrapperPlayClientKeepAlive) {
            this.timestamp = ((WrapperPlayClientKeepAlive) wrapper).getId();
        } else if (wrapper instanceof WrapperPlayClientPong) {
            this.timestamp = ((WrapperPlayClientPong) wrapper).getId();
        } else {
            this.timestamp = -1;
        }
    }

    public long getTimestamp() {
        return timestamp;
    }
}
