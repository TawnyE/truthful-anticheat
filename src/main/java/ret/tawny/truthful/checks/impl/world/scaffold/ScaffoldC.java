package ret.tawny.truthful.checks.impl.world.scaffold;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;

@CheckData(order = 'C', type = CheckType.SCAFFOLD)
@SuppressWarnings("unused") // Suppress "never used" warning, as this class is instantiated by reflection.
public final class ScaffoldC extends Check {

    @Override
    public void handlePacketPlayerReceive(final PacketEvent event) {
        if (!event.getPacketType().equals(PacketType.Play.Client.BLOCK_PLACE)) return;

        final Player player = event.getPlayer();
        if (player.isFlying()) return;

        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return;

        if (!player.isSprinting()) return;

        final PlayerBlockPlacePacketWrapper blockPlacePacketWrapper = new PlayerBlockPlacePacketWrapper(event);

        final boolean flag = switch (blockPlacePacketWrapper.getBlockFace()) {
            case EAST -> data.getDeltaX() > 0;
            case SOUTH -> data.getDeltaZ() > 0;
            case WEST -> data.getDeltaX() < 0;
            case NORTH -> data.getDeltaZ() < 0;
            default -> false;
        };

        if (flag) {
            flag(data, "Impossible sprinting direction while placing block");
        }
    }
}