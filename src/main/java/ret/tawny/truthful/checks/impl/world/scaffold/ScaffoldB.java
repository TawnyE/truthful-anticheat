package ret.tawny.truthful.checks.impl.world.scaffold;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.BlockUtils;
import ret.tawny.truthful.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;

@CheckData(order = 'B', type = CheckType.SCAFFOLD)
@SuppressWarnings("unused") // Suppress "never used" warning, as this class is instantiated by reflection.
public final class ScaffoldB extends Check {

    @Override
    public void handlePacketPlayerReceive(final PacketEvent event) {
        if(!event.getPacketType().equals(PacketType.Play.Client.BLOCK_PLACE)) return;

        final Player player = event.getPlayer();
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);
        if(playerData == null) return;

        final PlayerBlockPlacePacketWrapper blockPlacePacketWrapper = new PlayerBlockPlacePacketWrapper(event);

        if (BlockUtils.isAbnormal(blockPlacePacketWrapper.getBlock().getType())) return;

        if (!this.validate(blockPlacePacketWrapper)) {
            flag(playerData, "Invalid block hit vector. " + blockPlacePacketWrapper.getHitVec());
        }
    }

    private boolean validate(final PlayerBlockPlacePacketWrapper wrapper) {
        final double facingX = wrapper.getHitVec().getX();
        final double facingY = wrapper.getHitVec().getY();
        final double facingZ = wrapper.getHitVec().getZ();

        if (facingX < 0 || facingX > 1 || facingY < 0 || facingY > 1 || facingZ < 0 || facingZ > 1) {
            return false;
        }

        if (wrapper.getBlockFace() == null) return true;

        return switch (wrapper.getBlockFace()) {
            case NORTH -> facingZ == 0;
            case WEST -> facingX == 0;
            case SOUTH -> facingZ == 1;
            case EAST -> facingX == 1;
            case UP -> facingY == 1;
            case DOWN -> facingY == 0;
            default -> true;
        };
    }
}