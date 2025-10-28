package ret.tawny.truthful.checks.impl.world.scaffold;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;

@CheckData(order = 'D', type = CheckType.SCAFFOLD)
@SuppressWarnings("unused")
public final class ScaffoldD extends Check {

    @Override
    public void handlePacketPlayerReceive(final PacketEvent event) {
        if (!event.getPacketType().equals(PacketType.Play.Client.BLOCK_PLACE)) return;

        final Player player = event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isTeleportTick()) return;

        final PlayerBlockPlacePacketWrapper wrapper = new PlayerBlockPlacePacketWrapper(event);

        // FINAL REWORK: This is the true "impossible" action.
        // It flags if the player places a block on the BOTTOM face of the exact block they are standing on.
        // A legitimate player cannot reach this face without sneaking.
        if (wrapper.getBlockFace() == BlockFace.DOWN && !player.isSneaking()) {
            if (data.getLastGroundLocation() != null) {
                // Check if the block being placed against is the one the player was last standing on.
                if (wrapper.getBlock().getLocation().toBlockLocation().equals(data.getLastGroundLocation().toBlockLocation())) {
                    flag(data, "Placed a block downwards on their standing block without sneaking");
                }
            }
        }
    }
}