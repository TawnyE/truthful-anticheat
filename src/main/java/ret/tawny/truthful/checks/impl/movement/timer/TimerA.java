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

    private final Map<UUID, PlayerTimerData> timerDataMap = new HashMap<>();
    private static final double EXPECTED_DELAY = 50.0; // Minecraft ticks at 20TPS, so 1000ms / 20 = 50ms per tick.

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (playerData == null || playerData.isTeleportTick()) {
            return;
        }

        PlayerTimerData timerData = timerDataMap.computeIfAbsent(player.getUniqueId(), id -> new PlayerTimerData());
        final long now = System.currentTimeMillis();
        final long delay = now - timerData.lastPacketTime;

        // If the delay is unusually long, the player was likely lagging or standing still.
        // We reset their balance to prevent extreme values and give them a fresh start.
        if (delay > 150) {
            timerData.balance = 0.0;
            timerData.lastPacketTime = now;
            return;
        }

        // The core balance calculation:
        // Add the time we expect to pass (50ms) and subtract the time that actually passed.
        // - If lagging (delay > 50), balance goes down.
        // - If speeding (delay < 50), balance goes up.
        timerData.balance += EXPECTED_DELAY - delay;

        // Clamp the negative balance. This prevents a player who lags a lot from building
        // up a huge buffer that they could "spend" to cheat later.
        timerData.balance = Math.max(-500.0, timerData.balance);

        // A consistently positive balance means the player is sending packets faster than the server expects.
        // A threshold of +200 allows for natural network fluctuations.
        if (timerData.balance > 200.0) {
            timerData.violations++;
            if (timerData.violations > 5) {
                double speed = (delay > 0) ? (EXPECTED_DELAY / delay) : 50.0; // Calculate speed multiplier
                flag(playerData, String.format("Speed %.2fx, Balance: %.2f", speed, timerData.balance));
            }
            // After a flag, we reset the balance to prevent a single spike from causing a flag spam.
            timerData.balance = 0.0;
        } else {
            // If the player is behaving, slowly forgive past violations.
            timerData.violations = Math.max(0, timerData.violations - 0.05);
        }

        timerData.lastPacketTime = now;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        timerDataMap.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Helper class to store timer-related data for each player.
     */
    private static class PlayerTimerData {
        long lastPacketTime = System.currentTimeMillis();
        double balance = 0.0;
        double violations = 0.0;
    }
}