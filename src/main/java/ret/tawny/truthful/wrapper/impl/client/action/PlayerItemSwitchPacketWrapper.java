package ret.tawny.truthful.wrapper.impl.client.action;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import org.bukkit.entity.Player;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

public final class PlayerItemSwitchPacketWrapper extends PacketWrapper {
    private final int slot;

    public PlayerItemSwitchPacketWrapper(Object wrapper, Player player, PacketType.Play.Client type) {
        super(wrapper, player, type);

        if (wrapper instanceof WrapperPlayClientHeldItemChange) {
            this.slot = ((WrapperPlayClientHeldItemChange) wrapper).getSlot();
        } else {
            this.slot = -1;
        }
    }

    /**
     *
     * @return HotBar slot the player has switched too
     */
    public int getSlot() {
        return slot;
    }
}
