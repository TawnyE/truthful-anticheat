package ret.tawny.truthful.checks.impl.packet.badpacket;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.bukkit.GameMode;
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


@CheckData(order = 'A', type = CheckType.BAD_PACKET)
public final class BadPacketA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, PacketCounter> packetMap = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || player.getGameMode() == GameMode.CREATIVE)
            return;

        // Resync: Ignore packet spikes during lagbacks/teleports
        if (data.isServerFrozen() || data.isTeleportTick()) return;

        PacketCounter counter = packetMap.computeIfAbsent(player.getUniqueId(), k -> new PacketCounter());

        counter.packets++;
        long now = System.currentTimeMillis();

        // 1. Evaluation Interval: 1 Second
        if (now - counter.lastClear > 1000) {

            // Limit: 800 packets/sec.
            // Vanilla typically sends 100-300 including movement, interaction, and status.
            if (counter.packets > 800) {
                if (buffer.increase(player, 1.0) > 5.0) {
                    flag(data, "Packet Flood Detection. Rate: " + counter.packets + "/s");
                }
            } else {
                buffer.decrease(player, 0.5);
            }
            counter.packets = 0;
            counter.lastClear = now;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packetMap.remove(event.getPlayer().getUniqueId());
        buffer.remove(event.getPlayer());
    }

    private static class PacketCounter {
        int packets = 0;
        long lastClear = System.currentTimeMillis();
    }
}