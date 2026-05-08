package ret.tawny.truthful.wrapper.impl.client.action;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.entity.Player;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

public final class PlayerDiggingPacketWrapper extends PacketWrapper {

    public PlayerDiggingPacketWrapper(Object wrapper, Player player, PacketType.Play.Client type) {
        super(wrapper, player, type);
        // Digging packet data can be extracted if needed in the future
    }
}
