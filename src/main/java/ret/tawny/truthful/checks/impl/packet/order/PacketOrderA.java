package ret.tawny.truthful.checks.impl.packet.order;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
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


@CheckData(order = 'A', type = CheckType.PACKET_ORDER)
@SuppressWarnings("unused")
public final class PacketOrderA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, PlaceOrderState> stateMap = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || Truthful.getInstance().isBedrockPlayer(player))
            return;

        PacketTypeCommon type = event.getPacketType();
        PlaceOrderState state = stateMap.computeIfAbsent(player.getUniqueId(), k -> new PlaceOrderState());

        // Track swings
        if (type == PacketType.Play.Client.ANIMATION) {
            state.lastSwingTick = data.getTicksTracked();
        }

        // Check block placements
        else if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            int diff = data.getTicksTracked() - state.lastSwingTick;

            // Swing should have occurred within the last 2 ticks  
            // (handles [Swing, Flying, Place] and [Swing, Place] orderings)
            if (diff > 2) {
                // No recent swing — possible scaffold/auto-place
                if (state.pendingPlaceTick == -1) {
                    state.pendingPlaceTick = data.getTicksTracked();
                }
            } else {
                // Valid swing → place sequence
                state.pendingPlaceTick = -1;
                buffer.decrease(player, 0.2);
            }
        }

        // Check timeout on flying packets
        else if (WrapperPlayClientPlayerFlying.isFlying(type)) {
            if (state.pendingPlaceTick != -1) {
                // Allow 2 ticks grace for the swing to arrive after the place
                if (data.getTicksTracked() - state.pendingPlaceTick > 2) {
                    if (buffer.increase(player, 1.0) > 6.0) {
                        flag(data, "Block placement without animation");
                        buffer.reset(player, 3.0);
                    }
                    state.pendingPlaceTick = -1;
                }
            } else {
                buffer.decrease(player, 0.1);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        stateMap.remove(event.getPlayer().getUniqueId());
    }

    private static class PlaceOrderState {
        int lastSwingTick = -100;
        int pendingPlaceTick = -1;
    }
}
