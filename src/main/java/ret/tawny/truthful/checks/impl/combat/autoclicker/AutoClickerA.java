package ret.tawny.truthful.checks.impl.combat.autoclicker;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.math.MathHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'A', type = CheckType.AUTOCLICKER)
public final class AutoClickerA extends Check {

    private static final int SAMPLE_SIZE = 40;

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, ClickData> clickDataMap = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            final Player player = (Player) event.getPlayer();
            if (Truthful.getInstance().isBedrockPlayer(player)) return;

            final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
            if (data == null || data.isExempt() || data.isServerFrozen() || data.getActionTracker().isDigging()) return;

            long now = System.nanoTime();
            ClickData cd = clickDataMap.computeIfAbsent(player.getUniqueId(), k -> new ClickData());

            if (cd.lastClickTime != 0) {
                long delayNanos = now - cd.lastClickTime;
                double delayMillis = delayNanos / 1_000_000.0;

                // FIXED: Do not reset lastClickTime on batched packets (< 10ms).
                // This combines the times so it naturally counts as one valid interval.
                if (delayMillis < 10) return;

                if (delayMillis <= 500) {
                    cd.delays.addLast((int) delayMillis);

                    if (cd.delays.size() > SAMPLE_SIZE) {
                        cd.delays.pollFirst();
                    }

                    if (cd.delays.size() == SAMPLE_SIZE) {
                        ArrayList<Integer> delayList = new ArrayList<>(cd.delays);

                        double mean = MathHelper.getMean(delayList);
                        double stdDev = Math.sqrt(MathHelper.getVariance(delayList));
                        double cps = 1000.0 / mean;
                        double cv = (mean > 0) ? (stdDev / mean) : 0.0;

                        if (cps > 8.0) {
                            if (cv < 0.05) {
                                if (buffer.increase(player, 1.5) > 6.0) {
                                    flag(data, String.format("Robotic Consistency. CPS: %.1f, CV: %.4f", cps, cv));
                                    buffer.reset(player, 5.0);
                                }
                            } else if (cv < 0.08 && cps > 14.0) {
                                if (buffer.increase(player, 1.0) > 10.0) {
                                    flag(data, String.format("Low Variance. CPS: %.1f, CV: %.3f", cps, cv));
                                    buffer.reset(player, 7.0);
                                }
                            } else {
                                buffer.decrease(player, 0.5);
                            }
                        } else {
                            buffer.decrease(player, 0.25);
                        }
                    }
                } else {
                    cd.delays.clear();
                }
            }
            cd.lastClickTime = now;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clickDataMap.remove(event.getPlayer().getUniqueId());
        buffer.remove(event.getPlayer());
    }

    private static class ClickData {
        long lastClickTime = 0;
        final Deque<Integer> delays = new ArrayDeque<>();
    }
}