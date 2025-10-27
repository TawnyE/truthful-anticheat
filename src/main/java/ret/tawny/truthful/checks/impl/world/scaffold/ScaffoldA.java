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
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@CheckData(order = 'A', type = CheckType.SCAFFOLD)
@SuppressWarnings("unused")
public final class ScaffoldA extends Check {

    // Using a list to delay checks is unreliable. We will check instantly but with better logic.

    @Override
    public void handlePacketPlayerReceive(final PacketEvent event) {
        if (!event.getPacketType().equals(PacketType.Play.Client.BLOCK_PLACE)) return;

        final Player player = event.getPlayer();
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (playerData == null) return;

        final PlayerBlockPlacePacketWrapper blockPlacePacketWrapper = new PlayerBlockPlacePacketWrapper(event);
        final BlockFace placedFace = blockPlacePacketWrapper.getBlockFace();
        final BlockFace lookedFace = WorldUtils.getBlockFace(player);

        // If the player is looking at the face they placed on, or if we can't determine a face, it's valid.
        if (placedFace == null || lookedFace == BlockFace.SELF || placedFace == lookedFace) {
            return;
        }

        // --- EXEMPTION LOGIC ---
        // If the player is rapidly changing their yaw or pitch, it's highly likely they are
        // "flicking" their mouse to place a block. We should exempt this to prevent false flags.
        float deltaYaw = Math.abs(playerData.getDeltaYaw());
        float deltaPitch = Math.abs(playerData.getDeltaPitch());

        if (deltaYaw > 15.0F || deltaPitch > 15.0F) {
            return; // Exempt high-rotation movements.
        }

        // If the faces don't match AND the player is not turning quickly, it's a suspicious placement.
        flag(playerData, "Placed on an un-faced block. Looked: " + lookedFace + ", Placed: " + placedFace);
    }
}