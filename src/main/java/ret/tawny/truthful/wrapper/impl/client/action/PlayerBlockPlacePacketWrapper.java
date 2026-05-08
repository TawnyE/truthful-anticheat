package ret.tawny.truthful.wrapper.impl.client.action;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

public final class PlayerBlockPlacePacketWrapper extends PacketWrapper {

    private final com.github.retrooper.packetevents.util.Vector3i blockPosition;
    // Removed "Block" field to prevent async access
    private final Vector hitVec;
    private final org.bukkit.block.BlockFace blockFace;
    private final InteractionHand hand;

    public PlayerBlockPlacePacketWrapper(PacketReceiveEvent event) {
        this(new WrapperPlayClientPlayerBlockPlacement(event), (Player) event.getPlayer(),
                PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT);
    }

    public PlayerBlockPlacePacketWrapper(Object wrapper, Player player, PacketType.Play.Client type) {
        super(wrapper, player, type);

        if (wrapper instanceof WrapperPlayClientPlayerBlockPlacement) {
            WrapperPlayClientPlayerBlockPlacement placement = (WrapperPlayClientPlayerBlockPlacement) wrapper;

            this.blockPosition = placement.getBlockPosition();

            com.github.retrooper.packetevents.util.Vector3f cursor = placement.getCursorPosition();
            this.hitVec = new Vector(cursor.x, cursor.y, cursor.z);

            BlockFace peFace = placement.getFace();
            this.blockFace = convertBlockFace(peFace);

            this.hand = placement.getHand();

            // NOTE: We do NOT get the Bukkit Block here anymore.
            // Getting chunks/blocks async is unsafe.
        } else {
            this.blockPosition = null;
            this.hitVec = new Vector(0.5, 0.5, 0.5);
            this.blockFace = org.bukkit.block.BlockFace.UP;
            this.hand = InteractionHand.MAIN_HAND;
        }
    }

    private org.bukkit.block.BlockFace convertBlockFace(BlockFace peFace) {
        if (peFace == null) return null;
        return switch (peFace) {
            case NORTH -> org.bukkit.block.BlockFace.NORTH;
            case SOUTH -> org.bukkit.block.BlockFace.SOUTH;
            case EAST -> org.bukkit.block.BlockFace.EAST;
            case WEST -> org.bukkit.block.BlockFace.WEST;
            case UP -> org.bukkit.block.BlockFace.UP;
            case DOWN -> org.bukkit.block.BlockFace.DOWN;
            default -> null;
        };
    }

    public com.github.retrooper.packetevents.util.Vector3i getBlockPosition() { return blockPosition; }
    public org.bukkit.block.BlockFace getBlockFace() { return blockFace; }
    public Vector getHitVec() { return hitVec; }
    public InteractionHand getHand() { return hand; }

    @Override
    public String toString() {
        return String.format("PlayerBlockPlace[HitVector=%s, Facing=%s]", this.hitVec.toString(), this.blockFace);
    }
}