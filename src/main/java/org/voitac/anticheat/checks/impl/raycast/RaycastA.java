package org.voitac.anticheat.checks.impl.raycast;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.utils.world.BlockUtils;
import org.voitac.anticheat.utils.world.WorldUtils;
import org.voitac.anticheat.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;

@CheckData(order = 'B', type = CheckType.AIM)
public final class RaycastA extends Check {

    public RaycastA() {
        AntiCheat.getInstance().getScheduler().registerDispatcher(this::handlePlacement, PacketType.Play.Client.BLOCK_PLACE);
        AntiCheat.getInstance().getScheduler().registerDispatcher(this::handlePlacement, PacketType.Play.Client.USE_ITEM);
    }

    private void handlePlacement(final PacketEvent packetEvent) {
        final Player player = packetEvent.getPlayer();
        if(player.getGameMode() == GameMode.CREATIVE)
            return;

        final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(player);
        if(playerData == null)
            return;

        final PlayerBlockPlacePacketWrapper blockPlacePacketWrapper = new PlayerBlockPlacePacketWrapper(packetEvent);
        final BlockFace face = blockPlacePacketWrapper.getBlockFace();

        if(face == null)
            return;

        final Block anchor = blockPlacePacketWrapper.getBlock();
        final Block placedAgainst = BlockUtils.getRelativeBlock(anchor, face);

        final Location eye = player.getEyeLocation();
        final Location target = placedAgainst.getLocation().add(0.5D, 0.5D, 0.5D);

        final double reach = eye.distance(target);

        double maxReach = 4.5D;
        if(player.isSprinting())
            maxReach += 0.2D;
        maxReach += Math.min(0.7D, playerData.getPing() * 0.0025D);

        if(WorldUtils.hasClimbableNearby(player) || WorldUtils.isLiquid(player))
            maxReach += 0.4D;

        if(reach > maxReach && !player.isInsideVehicle() && !player.isGliding()) {
            formattedFlag(this, playerData, String.format("Reach %.2f > %.2f", reach, maxReach));
        }
    }
}
