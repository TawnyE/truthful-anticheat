package ret.tawny.truthful.checks.impl.packet.order;

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

/**
 * PacketOrderC — Crystal Place & Break Automation
 *
 * Detects automated crystal PvP by tracking same-tick crystal placement
 * and destruction patterns. A single same-tick operation is legitimate
 * (skilled crystal PvP), but SUSTAINED same-tick operations at high
 * frequency indicate automation.
 *
 * FIXED: Raised buffer threshold to avoid flagging skilled crystal PvP
 * players on occasional same-tick operations. Now requires consistent
 * sustained patterns to flag.
 */
@CheckData(order = 'C', type = CheckType.PACKET_ORDER)
public final class PacketOrderC extends Check {

    private final CheckBuffer buffer = new CheckBuffer(15.0);
    private final Map<UUID, CrystalState> stateMap = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return;

        CrystalState state = stateMap.computeIfAbsent(player.getUniqueId(), k -> new CrystalState());

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            // PERFORMANCE: Use cached hand state
            if (data.isHoldingCrystal()) {
                state.placeTick = data.getTicksTracked();
                state.placeCount++;
            }
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            // Check if attacking in the SAME tick as placing
            if (state.placeTick == data.getTicksTracked()) {
                state.sameTicks++;

                // Single same-tick is fine (skilled player). Sustained pattern is bot.
                // Only flag after 5+ same-tick operations in recent history
                if (state.sameTicks >= 5) {
                    if (buffer.increase(player, 1.5) > 10.0) {
                        flag(data, String.format("Crystal automation (sameTickOps=%d, total=%d)",
                                state.sameTicks, state.placeCount));
                        buffer.reset(player, 5.0);
                        state.sameTicks = 0;
                    }
                }
            } else {
                // Not same-tick — decay
                if (state.sameTicks > 0) state.sameTicks--;
                buffer.decrease(player, 0.2);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        stateMap.remove(event.getPlayer().getUniqueId());
    }

    private static class CrystalState {
        int placeTick = -1;
        int sameTicks = 0;
        int placeCount = 0;
    }
}
