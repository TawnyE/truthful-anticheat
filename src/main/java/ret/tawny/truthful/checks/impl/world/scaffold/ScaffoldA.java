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

@CheckData(order = 'A', type = CheckType.SCAFFOLD)
@SuppressWarnings("unused")
public final class ScaffoldA extends Check {

    @Override
    public void handlePacketPlayerReceive(final PacketEvent event) {
        if (!event.getPacketType().equals(PacketType.Play.Client.BLOCK_PLACE)) return;

        final Player player = event.getPlayer();
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (playerData == null) return;

        final PlayerBlockPlacePacketWrapper blockPlacePacketWrapper = new PlayerBlockPlacePacketWrapper(event);
        final BlockFace placedFace = blockPlacePacketWrapper.getBlockFace();
        final BlockFace lookedFace = WorldUtils.getBlockFace(player);

        if (placedFace == null || lookedFace == BlockFace.SELF || placedFace == lookedFace) {
            return;
        }

        // REFINED EXEMPTION: Exempt players who are looking nearly straight up or down,
        // as this is common in legitimate speed-bridging techniques.
        float pitch = player.getLocation().getPitch();
        if (pitch > 80.0F || pitch < -80.0F) {
            return;
        }

        // Also exempt rapid mouse movements
        if (Math.abs(playerData.getDeltaYaw()) > 20.0F || Math.abs(playerData.getDeltaPitch()) > 20.0F) {
            return;
        }

        flag(playerData, "Placed on an un-faced block. Looked: " + lookedFace + ", Placed: " + placedFace);
    }
}