package ret.tawny.truthful.checks.impl.combat.killaura;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KillAuraG: Multi-Target Detection
 */
@CheckData(order = 'G', type = CheckType.KILLAURA)
public final class KillAuraG extends Check {

    private static final int MAX_TARGETS_WINDOW = 10;
    private static final long TIME_WINDOW_MS = 500L;
    private static final int SUSPICIOUS_TARGET_COUNT = 3;

    private final CheckBuffer buffer = new CheckBuffer(12.0);
    private final Map<UUID, TargetHistory> historyMap = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY)
            return;

        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK)
            return;

        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isExempt())
            return;

        int targetId = interact.getEntityId();
        long now = System.currentTimeMillis();

        TargetHistory history = historyMap.computeIfAbsent(player.getUniqueId(), k -> new TargetHistory());
        history.addTarget(targetId, now);

        // Count unique targets within time window
        int uniqueTargets = history.countUniqueTargetsInWindow(now, TIME_WINDOW_MS);
        long fastestSwitch = history.getFastestSwitchMs(now, TIME_WINDOW_MS);

        // Detection: 3+ unique targets in 500ms is highly suspicious
        if (uniqueTargets >= SUSPICIOUS_TARGET_COUNT) {
            double severity = (uniqueTargets - 2) * 1.5;

            // Bonus severity for impossibly fast switches (< 50ms between different
            // targets)
            if (fastestSwitch > 0 && fastestSwitch < 50) {
                severity += 2.0;
            }

            if (buffer.increase(player, severity) > 8.0) {
                flag(data, String.format("Multi-Target. Targets: %d in %dms, FastestSwitch: %dms",
                        uniqueTargets, TIME_WINDOW_MS, fastestSwitch));
                data.executeLagback();
                buffer.reset(player, 4.0);
            }
        } else {
            buffer.decrease(player, 0.3);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        historyMap.remove(event.getPlayer().getUniqueId());
    }

    private static class TargetHistory {
        private final Deque<TargetEntry> entries = new ArrayDeque<>();

        void addTarget(int targetId, long timestamp) {
            entries.addLast(new TargetEntry(targetId, timestamp));
            while (entries.size() > MAX_TARGETS_WINDOW) {
                entries.pollFirst();
            }
        }

        int countUniqueTargetsInWindow(long now, long windowMs) {
            Set<Integer> unique = new HashSet<>();
            for (TargetEntry entry : entries) {
                if (now - entry.timestamp <= windowMs) {
                    unique.add(entry.targetId);
                }
            }
            return unique.size();
        }

        long getFastestSwitchMs(long now, long windowMs) {
            long fastest = Long.MAX_VALUE;
            TargetEntry prev = null;

            for (TargetEntry entry : entries) {
                if (now - entry.timestamp > windowMs)
                    continue;

                if (prev != null && prev.targetId != entry.targetId) {
                    long switchTime = entry.timestamp - prev.timestamp;
                    if (switchTime < fastest) {
                        fastest = switchTime;
                    }
                }
                prev = entry;
            }

            return fastest == Long.MAX_VALUE ? -1 : fastest;
        }
    }

    private static class TargetEntry {
        final int targetId;
        final long timestamp;

        TargetEntry(int targetId, long timestamp) {
            this.targetId = targetId;
            this.timestamp = timestamp;
        }
    }
}
