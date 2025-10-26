package org.voitac.anticheat.wrapper.impl.client.action;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.google.common.base.Objects;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.voitac.anticheat.wrapper.api.PacketWrapper;

import java.util.List;

public final class PlayerBlockPlacePacketWrapper extends PacketWrapper {

    /**
     * Placement Anchor
     */
    private final BlockPosition blockPosition;

    /**
     * Real Block, not the position wrapper from ProtocolLib
     */
    private final Block block;

    /**
     * Block Hit Vector, used to calculate slab placement in vanilla.
     */
    private final Vector hitVec;

    /**
     * Block Place Direction, used to find the offset from the anchor to place the block
     */
    private final BlockFace blockFace;

    public PlayerBlockPlacePacketWrapper(final PacketEvent packetEvent) {
        super(packetEvent);
        final List<Float> pointsIn = this.packetContainer.getFloat().getValues(); // Each Hit Vector Point
        final float hitX = pointsIn.size() > 0 ? pointsIn.get(0) : 0.5F;
        final float hitY = pointsIn.size() > 1 ? pointsIn.get(1) : 0.5F;
        final float hitZ = pointsIn.size() > 2 ? pointsIn.get(2) : 0.5F;

        this.hitVec = new Vector(hitX, hitY, hitZ);

        final List<Integer> faces = this.packetContainer.getIntegers().getValues();
        final int faceIndex = faces.isEmpty() ? 255 : faces.get(0);
        this.blockFace = faceFromIndex(faceIndex);

        final List<BlockPosition> positions = this.packetContainer.getBlockPositionModifier().getValues();
        if(positions.isEmpty()) {
            final BlockPosition fallback = new BlockPosition(this.player.getLocation().getBlockX(),
                    this.player.getLocation().getBlockY(), this.player.getLocation().getBlockZ());
            this.blockPosition = fallback;
        }else {
            this.blockPosition = positions.get(0);
        }

        this.block = this.player.getWorld().getBlockAt(this.blockPosition.getX(),
                this.blockPosition.getY(), this.blockPosition.getZ());
    }

    private static BlockFace faceFromIndex(final int index) {
        switch(index) {
            default:
                return BlockFace.DOWN;
            case 1:
                return BlockFace.UP;
            case 2:
                return BlockFace.NORTH;
            case 3:
                return BlockFace.SOUTH;
            case 4:
                return BlockFace.WEST;
            case 5:
                return BlockFace.EAST;
            case 255:
                return null; // Interact Scenario
        }
    }

    /**
     *
     * @return Block that was used as placement anchor, not to be confused with the new block that was placed
     */
    public BlockPosition getBlockPosition() {
        return blockPosition;
    }

    /**
     *
     * @return Real Block in World, not the position wrapper
     */
    public Block getBlock() {
        return block;
    }

    /**
     *
     * @return Block Placement Facing
     */
    public BlockFace getBlockFace() {
        return blockFace;
    }

    /**
     *
     * @return Block Placement Hit Vector
     */
    public Vector getHitVec() {
        return hitVec;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("Hit Vector", this.hitVec).add("Facing", this.blockFace).toString();
    }
}
