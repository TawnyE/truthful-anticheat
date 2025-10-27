package ret.tawny.truthful.wrapper.impl.client.action;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

import java.util.List;

public final class PlayerBlockPlacePacketWrapper extends PacketWrapper {

    private final BlockPosition blockPosition;
    private final Block block;
    private final Vector hitVec;
    private final BlockFace blockFace;

    public PlayerBlockPlacePacketWrapper(final PacketEvent packetEvent) {
        super(packetEvent);
        final List<Float> pointsIn = this.packetContainer.getFloat().getValues();
        final float hitX = pointsIn.size() > 0 ? pointsIn.get(0) : 0.5F;
        final float hitY = pointsIn.size() > 1 ? pointsIn.get(1) : 0.5F;
        final float hitZ = pointsIn.size() > 2 ? pointsIn.get(2) : 0.5F;

        this.hitVec = new Vector(hitX, hitY, hitZ);

        this.blockFace = resolveFace();

        final List<BlockPosition> positions = this.packetContainer.getBlockPositionModifier().getValues();
        if (positions.isEmpty()) {
            final BlockPosition fallback = new BlockPosition(this.player.getLocation().getBlockX(),
                    this.player.getLocation().getBlockY(), this.player.getLocation().getBlockZ());
            this.blockPosition = fallback;
        } else {
            this.blockPosition = positions.get(0);
        }

        this.block = this.player.getWorld().getBlockAt(this.blockPosition.getX(),
                this.blockPosition.getY(), this.blockPosition.getZ());
    }

    private BlockFace resolveFace() {
        final EnumWrappers.Direction direction = readDirection();
        if (direction != null) {
            // Use a manual, robust mapping to convert ProtocolLib's enum to Bukkit's enum
            return mapDirectionToBlockFace(direction);
        }

        // Fallback for very old server versions
        final List<Integer> faces = this.packetContainer.getIntegers().getValues();
        final int faceIndex = faces.isEmpty() ? 255 : faces.get(0);
        return faceFromIndex(faceIndex);
    }

    // This new helper method provides a guaranteed conversion, fixing the error.
    private BlockFace mapDirectionToBlockFace(EnumWrappers.Direction direction) {
        if (direction == null) return null;
        return switch (direction) {
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case EAST -> BlockFace.EAST;
            case WEST -> BlockFace.WEST;
            case UP -> BlockFace.UP;
            case DOWN -> BlockFace.DOWN;
        };
    }

    private EnumWrappers.Direction readDirection() {
        // This logic attempts multiple ways to read the direction for maximum compatibility
        try {
            return this.packetContainer.getEnumModifier(EnumWrappers.Direction.class, 1).readSafely(0);
        } catch (final Throwable ignored) {
            try {
                return this.packetContainer.getDirections().readSafely(0);
            } catch (final Throwable ignored2) {
                return null;
            }
        }
    }

    private static BlockFace faceFromIndex(final int index) {
        return switch (index) {
            case 0 -> BlockFace.DOWN;
            case 1 -> BlockFace.UP;
            case 2 -> BlockFace.NORTH;
            case 3 -> BlockFace.SOUTH;
            case 4 -> BlockFace.WEST;
            case 5 -> BlockFace.EAST;
            default -> null; // Interact Scenario (index 255)
        };
    }

    public BlockPosition getBlockPosition() {
        return blockPosition;
    }

    public Block getBlock() {
        return block;
    }

    public BlockFace getBlockFace() {
        return blockFace;
    }

    public Vector getHitVec() {
        return hitVec;
    }

    @Override
    public String toString() {
        return String.format("PlayerBlockPlace[HitVector=%s, Facing=%s]", this.hitVec.toString(), this.blockFace);
    }
}