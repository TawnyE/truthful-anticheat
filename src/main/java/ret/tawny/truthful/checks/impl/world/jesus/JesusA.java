package ret.tawny.truthful.checks.impl.world.jesus;

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

@CheckData(order = 'A', type = CheckType.JESUS)
public final class JesusA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(5.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        if (!relMovePacketWrapper.isPositionUpdate()) return;

        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (playerData == null) return;
        if (player.getAllowFlight() || player.isFlying() || player.isGliding() || player.isInsideVehicle() || player.isSwimming()) return;

        if (!playerData.isInLiquid()) {
            buffer.decrease(player, 1.0);
            return;
        }

        final double deltaY = playerData.getDeltaY();
        boolean suspicious = deltaY > -0.02D && deltaY < 0.05D && playerData.getTicksInAir() > 6 && !WorldUtils.nearBlock(player);

        if (suspicious) {
            if (buffer.increase(player, 1.0) > 5.0) {
                flag(playerData, String.format("Liquid walk. dY: %.3f", deltaY));
                buffer.reset(player, 2.5);
            }
        } else {
            buffer.decrease(player, 0.75);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}