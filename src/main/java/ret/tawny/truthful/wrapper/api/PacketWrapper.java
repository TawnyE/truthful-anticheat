package ret.tawny.truthful.wrapper.api;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;

public abstract class PacketWrapper {
    protected final PacketContainer packetContainer;
    protected final Player player;
    protected final PacketType type;

    protected PacketWrapper(PacketEvent packetEvent) {
        this.packetContainer = packetEvent.getPacket();
        this.player = packetEvent.getPlayer();
        this.type = packetEvent.getPacketType();
    }

    public final Player getPlayer() {
        return player;
    }
}
