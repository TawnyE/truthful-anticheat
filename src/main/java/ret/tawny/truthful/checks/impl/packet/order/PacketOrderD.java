package ret.tawny.truthful.checks.impl.packet.order;

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
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'D', type = CheckType.PACKET_ORDER)
public final class PacketOrderD extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, OrderData> dataMap = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return;

        OrderData orderData = dataMap.computeIfAbsent(player.getUniqueId(), k -> new OrderData());

        // Track Swings
        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            orderData.lastSwingTick = data.getTicksTracked();
            // If we were suspicious of an attack, this swing validates it
            orderData.pendingAttackTick = -1;
        }

        // Check Attacks
        else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            if (wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {

                // Logic: NoSwing
                // In Vanilla, you must swing your arm to attack.
                // However, packets can arrive as [Attack, Swing] due to batching.

                int diff = data.getTicksTracked() - orderData.lastSwingTick;

                // If last swing was > 10 ticks ago (0.5s), they likely didn't swing yet.
                // Instead of flagging immediately, we mark it as pending.
                if (diff > 10) {
                    if (orderData.pendingAttackTick == -1) {
                        orderData.pendingAttackTick = data.getTicksTracked();
                    }
                }
            }
        }
    }

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(wrapper.getPlayer());
        if (data == null) return;

        OrderData orderData = dataMap.get(wrapper.getPlayer().getUniqueId());
        if (orderData == null) return;

        // Check timeout for pending attacks
        if (orderData.pendingAttackTick != -1) {
            // Allow 2 ticks grace period for the Swing packet to arrive
            // This handles [Attack, Flying, Swing] or [Attack, Swing] orderings
            if (data.getTicksTracked() - orderData.pendingAttackTick > 2) {
                if (buffer.increase(wrapper.getPlayer(), 1.0) > 5.0) {
                    flag(data, "NoSwing (Attack without Animation)");
                    buffer.reset(wrapper.getPlayer(), 2.0);
                }
                // Reset to avoid spamming flags for the same missing swing
                orderData.pendingAttackTick = -1;
            }
        } else {
            buffer.decrease(wrapper.getPlayer(), 0.1);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    private static class OrderData {
        int lastSwingTick = -1;
        int pendingAttackTick = -1;
    }
}