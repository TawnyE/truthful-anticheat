package ret.tawny.truthful.checks.impl.packet.badpacket;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
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
 * BadPacketJ: Sneak + Sprint Impossible State Detection
 *
 * Update: Added a persistence check. The impossible state must persist
 * for a specific duration to rule out packet arrival jitter (race conditions).
 */
@CheckData(order = 'J', type = CheckType.BAD_PACKET)
public final class BadPacketJ extends Check {

    private final CheckBuffer buffer = new CheckBuffer(8.0);
    private final Map<UUID, StateData> stateMap = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.ENTITY_ACTION)
            return;

        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null)
            return;

        // VERSION GATE: Only check 1.9+ clients
        // 1.8 has client-side desync where this happens naturally
        ClientVersion clientVersion = PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
        if (clientVersion.isOlderThan(ClientVersion.V_1_9)) {
            return;
        }

        WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction(event);
        WrapperPlayClientEntityAction.Action action = wrapper.getAction();

        StateData state = stateMap.computeIfAbsent(player.getUniqueId(), k -> new StateData());

        // Track state changes
        switch (action) {
            case START_SNEAKING:
                state.sneaking = true;
                break;
            case STOP_SNEAKING:
                state.sneaking = false;
                break;
            case START_SPRINTING:
                state.sprinting = true;
                break;
            case STOP_SPRINTING:
                state.sprinting = false;
                break;
            default:
                break;
        }

        // Logic: Impossible State Persistence
        // Packets can arrive out of order by ~50ms.
        // We only flag if they maintain Sneak + Sprint for > 100ms.

        long now = System.currentTimeMillis();

        if (state.sneaking && state.sprinting) {
            if (state.impossibleStartTime == 0) {
                state.impossibleStartTime = now;
            }

            long duration = now - state.impossibleStartTime;

            // Grace period: 100ms of overlap is allowed for network jitter
            if (duration > 100) {
                if (buffer.increase(player, 1.0) > 5.0) {
                    flag(data, "Impossible State: Sneak+Sprint > " + duration + "ms");
                    data.executeLagback();
                    buffer.reset(player, 2.0);
                }
            }
        } else {
            // State is valid
            state.impossibleStartTime = 0;
            buffer.decrease(player, 0.1);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        stateMap.remove(event.getPlayer().getUniqueId());
    }

    private static class StateData {
        boolean sneaking = false;
        boolean sprinting = false;
        long impossibleStartTime = 0;
    }
}