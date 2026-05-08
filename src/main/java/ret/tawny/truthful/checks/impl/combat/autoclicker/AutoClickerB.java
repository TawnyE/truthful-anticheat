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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'B', type = CheckType.AUTOCLICKER)
public final class AutoClickerB extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, Long> lastClickNano = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDelayNano = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> identicalStreak = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            final Player player = (Player) event.getPlayer();
            if (Truthful.getInstance().isBedrockPlayer(player)) return;

            final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
            if (data == null || data.isServerFrozen() || data.isExempt() || data.getActionTracker().isDigging()) return;

            long now = System.nanoTime();
            long lastTime = lastClickNano.getOrDefault(player.getUniqueId(), 0L);

            if (lastTime != 0) {
                long delay = now - lastTime;
                double delayMs = delay / 1_000_000.0;

                // Batched packet filter
                if (delayMs < 10) return;

                long prevDelay = lastDelayNano.getOrDefault(player.getUniqueId(), -1L);

                if (delayMs <= 250) {
                    long diff = Math.abs(delay - prevDelay);

                    if (diff < 1_000_000) {
                        int streak = identicalStreak.getOrDefault(player.getUniqueId(), 0) + 1;
                        identicalStreak.put(player.getUniqueId(), streak);

                        if (streak > 3) {
                            if (buffer.increase(player, 2.0) > 5.0) {
                                flag(data, String.format("Identical Delays. Streak: %d, Diff: %.2fms", streak, diff / 1_000_000.0));
                                buffer.reset(player, 4.0);
                            }
                        }
                    } else {
                        identicalStreak.put(player.getUniqueId(), 0);
                        buffer.decrease(player, 0.4);
                    }

                    lastDelayNano.put(player.getUniqueId(), delay);
                } else {
                    identicalStreak.put(player.getUniqueId(), 0);
                }
            }
            lastClickNano.put(player.getUniqueId(), now);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastClickNano.remove(uuid);
        lastDelayNano.remove(uuid);
        identicalStreak.remove(uuid);
        buffer.remove(event.getPlayer());
    }
}