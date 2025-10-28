package ret.tawny.truthful.checks.impl.movement.spoof;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'A', type = CheckType.SPOOF)
@SuppressWarnings("unused")
public final class GroundSpoofA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(5.0); // Increased buffer slightly

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        if (!relMovePacketWrapper.isPositionUpdate()) return;

        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (playerData == null) return;
        if (player.getAllowFlight() || player.isFlying() || player.isGliding() || player.isInsideVehicle()) return;
        if (playerData.isTeleportTick() || playerData.isInLiquid() || WorldUtils.hasClimbableNearby(player)) return;

        final boolean clientGround = relMovePacketWrapper.isGround();
        final boolean serverGround = playerData.isOnGround();

        if (clientGround && !serverGround && playerData.getTicksInAir() > 4) { // Increased ticks in air
            if (buffer.increase(player, 1.0) > 5.0) {
                flag(playerData, "Client claims onGround without server-side support");
                buffer.reset(player, 2.5);
            }
        } else {
            buffer.decrease(player, 0.5); // Made forgiveness more generous
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}