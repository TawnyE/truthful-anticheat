package ret.tawny.truthful.checks.impl.movement.baritone;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'C', type = CheckType.BARITONE)
public final class BaritoneC extends Check {

    private final Map<UUID, BreakData> dataMap = new ConcurrentHashMap<>();

    @Override
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isMovementExempt()) return;

        final BreakData bd = dataMap.computeIfAbsent(player.getUniqueId(), id -> new BreakData());
        final long now = System.currentTimeMillis();

        if (bd.lastBreakMs > 0L) {
            long delta = now - bd.lastBreakMs;
            if (delta > 50L && delta < 1000L) {
                long diff = Math.abs(delta - bd.lastDeltaMs);

                // FIXED: Block breaking in Minecraft is tick-based (50ms).
                // If a human holds left click, the server naturally receives breaks in perfect 50ms intervals (e.g. 450ms).
                // We ignore these natural tick-aligned intervals to prevent strip-mining false flags.
                long mod = delta % 50;
                boolean tickAligned = mod <= 5 || mod >= 45;

                if (diff <= 3L && !tickAligned) {
                    bd.consistent++;
                } else if (diff > 25L) {
                    bd.consistent = 0; // Hard reset on human variance
                } else {
                    bd.consistent = Math.max(0, bd.consistent - 2); // Faster decay
                }

                if (bd.consistent > 15) {
                    data.lowerBaritoneTrust(2.0D);
                    if (data.getBaritoneTrust() < 50.0D) {
                        flag(data, String.format("Consistent break intervals d=%d c=%d trust=%.1f", delta, bd.consistent, data.getBaritoneTrust()));
                    }
                }

                bd.lastDeltaMs = delta;
            }
        }

        bd.lastBreakMs = now;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    private static final class BreakData {
        long lastBreakMs;
        long lastDeltaMs;
        int consistent;
    }
}