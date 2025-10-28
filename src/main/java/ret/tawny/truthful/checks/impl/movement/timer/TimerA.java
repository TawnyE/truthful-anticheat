package ret.tawny.truthful.checks.impl.movement.timer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@CheckData(order = 'A', type = CheckType.TIMER)
@SuppressWarnings("unused")
public final class TimerA extends Check {

    private final Map<UUID, Long> lastPacketTimeMap = new HashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        // Add a grace period for when the player first joins the server.
        if (data == null || data.isTeleportTick() || data.getTicksTracked() < 100) {
            return;
        }

        long now = System.currentTimeMillis();
        long last = lastPacketTimeMap.computeIfAbsent(player.getUniqueId(), id -> now - 50L);
        long delay = now - last;

        // CRITICAL FIX: Prevent division by zero if packets arrive in the same millisecond.
        if (delay <= 0) {
            delay = 1; // Treat it as 1ms to avoid infinity
        }

        data.timerSpeed.add(50.0 / delay);

        if (data.timerSpeed.isFull()) {
            double averageSpeed = data.timerSpeed.getAverage();

            // Be more lenient, only flag for speeds consistently above 130%
            if (averageSpeed > 1.3) {
                flag(data, String.format("Average speed is too high. Speed: %.2fx", averageSpeed));
            }
        }

        lastPacketTimeMap.put(player.getUniqueId(), now);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        lastPacketTimeMap.remove(event.getPlayer().getUniqueId());
    }
}