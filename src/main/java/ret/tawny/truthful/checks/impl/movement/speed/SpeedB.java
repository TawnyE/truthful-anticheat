package ret.tawny.truthful.checks.impl.movement.speed;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'B', type = CheckType.SPEED)
@SuppressWarnings("unused")
public final class SpeedB extends Check {

    private final CheckBuffer buffer = new CheckBuffer(15.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        if (!relMovePacketWrapper.isPositionUpdate()) return;

        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || !data.isOnGround() || data.isTeleportTick()) return;

        // EXEMPTION: Forgive acceleration for the first 3 ticks after landing to allow for sprint-jumping.
        if (data.getTicksOnGround() < 3) {
            buffer.decrease(player, 1.0);
            return;
        }

        if (data.isCollidedHorizontally()) return;

        double deltaXZ = data.getDeltaXZ();
        double lastDeltaXZ = data.getLastDeltaXZ();
        double acceleration = deltaXZ - lastDeltaXZ;

        double limit = player.isSprinting() ? 0.027 : 0.022;
        limit += (player.getActivePotionEffects().stream().filter(e -> e.getType().getName().equals("SPEED")).mapToInt(e -> e.getAmplifier() + 1).sum()) * 0.006;

        if (acceleration > limit) {
            // Scale the buffer increase by how much they exceeded the limit.
            if (buffer.increase(player, (acceleration / limit)) > 15.0) {
                flag(data, String.format("Impossible acceleration. A: %.5f, L: %.5f", acceleration, limit));
                buffer.reset(player, 7.5);
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