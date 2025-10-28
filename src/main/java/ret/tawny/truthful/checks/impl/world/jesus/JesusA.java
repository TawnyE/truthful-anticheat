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
@SuppressWarnings("unused")
public final class JesusA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        if (!relMovePacketWrapper.isPositionUpdate()) return;

        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || player.getAllowFlight() || player.isFlying() || data.isTeleportTick() || player.isSwimming()) {
            return;
        }

        if (!data.isInLiquid() || WorldUtils.nearBlock(player)) {
            buffer.decrease(player, 0.5);
            return;
        }

        final double deltaY = data.getDeltaY();
        final int airTicks = data.getTicksInAir();

        // A player legitimately bobbing on the water's surface will have small, fluctuating deltaY values.
        // A "Jesus" cheat often results in a perfectly static deltaY of 0, or small positive "hops".
        boolean suspicious = (Math.abs(deltaY) < 0.001D && airTicks > 2) || (deltaY > 0 && deltaY < 0.1 && data.getLastDeltaY() <= 0);

        if (suspicious) {
            if (buffer.increase(player, 1.0) > 10.0) {
                flag(data, String.format("Unnatural vertical motion in liquid. dY: %.4f, AT: %d", deltaY, airTicks));
                buffer.reset(player, 5.0);
            }
        } else {
            buffer.decrease(player, 0.25);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}