package ret.tawny.truthful.wrapper.api;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.entity.Player;

/**
 * Base class for packet wrappers that abstracts packet data extraction.
 * Subclasses will store the actual PacketEvents wrapper object.
 */
public abstract class PacketWrapper {
    protected final Object packetWrapper;
    protected final Player player;
    protected final com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon type;

    // Constructor for client packets
    protected PacketWrapper(Object packetWrapper, Player player,
            com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon type) {
        this.packetWrapper = packetWrapper;
        this.player = player;
        this.type = type;
    }

    public final Player getPlayer() {
        return player;
    }

    public final com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon getType() {
        return type;
    }
}
