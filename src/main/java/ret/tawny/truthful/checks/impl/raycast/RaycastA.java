package ret.tawny.truthful.checks.impl.raycast;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.BlockUtils;
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;

@CheckData(order = 'A', type = CheckType.RAYCAST)
@SuppressWarnings("unused") // Suppress "never used" warning, as this class is instantiated by reflection.
public final class RaycastA extends Check {

    public RaycastA() {
        Truthful.getInstance().getScheduler().registerDispatcher(this::handlePlacement, PacketType.Play.Client.BLOCK_PLACE);
        Truthful.getInstance().getScheduler().registerDispatcher(this::handlePlacement, PacketType.Play.Client.USE_ITEM);
    }

    private void handlePlacement(final PacketEvent packetEvent) {
        if (!isEnabled()) return;

        final Player player = packetEvent.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (playerData == null) return;

        final PlayerBlockPlacePacketWrapper blockPlacePacketWrapper = new PlayerBlockPlacePacketWrapper(packetEvent);
        final BlockFace face = blockPlacePacketWrapper.getBlockFace();

        if (face == null) return;

        final Block anchor = blockPlacePacketWrapper.getBlock();
        final Block placedAgainst = BlockUtils.getRelativeBlock(anchor, face);

        final Location eye = player.getEyeLocation();
        final Location target = placedAgainst.getLocation().add(0.5D, 0.5D, 0.5D);

        final double reach = eye.distance(target);

        double maxReach = 4.5D;
        if (player.isSprinting()) maxReach += 0.2D;
        maxReach += Math.min(0.7D, playerData.getPing() * 0.0025D);

        if (WorldUtils.hasClimbableNearby(player) || WorldUtils.isLiquid(player)) {
            maxReach += 0.4D;
        }

        if (reach > maxReach && !player.isInsideVehicle() && !player.isGliding()) {
            flag(playerData, String.format("Reach %.2f > %.2f", reach, maxReach));
        }
    }
}