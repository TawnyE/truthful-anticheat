package org.voitac.anticheat.checks.impl.world.scaffold;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.utils.world.BlockUtils;
import org.voitac.anticheat.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;

// This check is a simple bad packet check for hit vectors, this may need to be refactored as a bad packet check
@CheckData(order = 'B', type = CheckType.SCAFFOLD)
public final class ScaffoldB extends Check {

    @Override
    public void onPacketPlayerReceive(final PacketEvent event) {
        if(!event.getPacketType().equals(PacketType.Play.Client.BLOCK_PLACE))
            return;
        final PlayerBlockPlacePacketWrapper blockPlacePacketWrapper = new PlayerBlockPlacePacketWrapper(event);
        final Player player = event.getPlayer();
        final Block looking = BlockUtils.getFirstBlockInSight(player, 3.0D);

        // Abnormal blocks qualify as any block where its bounding box isn't 1, 1, 1 as this would make our hit vector check inaccurate
        // No method as far as im aware to easily get the bounding box of the block
        if(BlockUtils.isAbnormal(blockPlacePacketWrapper.getBlock()))
            return;
        if(!this.validate(blockPlacePacketWrapper))
            formattedFlag(this, AntiCheat.getInstance().getDataManager().getPlayerData(event.getPlayer()), blockPlacePacketWrapper);
    }

    private boolean validate(final PlayerBlockPlacePacketWrapper blockPlacePacketWrapper) {
        final double facingX = blockPlacePacketWrapper.getHitVec().getX();
        final double facingY = blockPlacePacketWrapper.getHitVec().getY();
        final double facingZ = blockPlacePacketWrapper.getHitVec().getZ();

        // Instant illegal packet check before we even check the side
        if(facingX < 0 || facingX > 1 || facingY < 0 || facingY > 1 || facingZ < 0 || facingZ > 1)
            return false;

        if(blockPlacePacketWrapper.getBlockFace() == null)
            return facingX == 0 && facingY == 0 && facingZ == 0;

        // A facing like east will only have the X value of 1, as it is the positive X side, this means the only two values that can be different are Z and Y.
        // This prevents clients from simply doing something like...
        // facingOffsetX = Math.random();
        // This is something seem in low quality clients
        switch(blockPlacePacketWrapper.getBlockFace()) {
            case NORTH:
                if(facingZ != 0)
                    return false;
                break;
            case WEST:
                if(facingX != 0)
                    return false;
                break;
            case SOUTH:
                if(facingZ != 1)
                    return false;
                break;
            case EAST:
                if(facingX != 1)
                    return false;
                break;
            case UP:
                if(facingY != 1)
                    return false;
                break;
            case DOWN:
                if(facingY != 0)
                    return false;
                break;
        }
        // The side length of a mc block is 16, and as it is a perfect cube every side length is 16
        // As we only get the vector for a specific side, every value should be divided perfectly by length * sides, and therefor should have no remainder
        return facingX % (1D / 64D) == 0 && facingZ % (1D / 64D) == 0 && facingY % (1D / 64D) == 0;
    }
}
