package ret.tawny.truthful.checks.impl.movement.baritone;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'B', type = CheckType.BARITONE)
public final class BaritoneB extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, BotData> dataMap = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;

        WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
        if (dig.getAction() == DiggingAction.START_DIGGING) {
            dataMap.computeIfAbsent(player.getUniqueId(), id -> new BotData()).lastDigMs = System.currentTimeMillis();
        }
    }

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isRotationUpdate()) return;
        final PlayerData data = wrapper.getPlayerData();
        if (data == null || data.isMovementExempt() || data.isRotationExempt()) return;

        final BotData bd = dataMap.computeIfAbsent(wrapper.getPlayer().getUniqueId(), id -> new BotData());
        if (System.currentTimeMillis() - bd.lastDigMs > 250L) {
            bd.suspicion = Math.max(0.0D, bd.suspicion - 0.2D);
            return;
        }

        final float dyaw = Math.abs(data.getDeltaYaw());
        final float dpitch = Math.abs(data.getDeltaPitch());
        final boolean moving = data.getDeltaXZ() > 0.10D;

        if (moving && dpitch == 0.0F && dyaw > 1.0F) bd.suspicion += 0.8D;
        else if (moving && bd.lastYaw > 10.0F && dyaw < 0.08F) bd.suspicion += 1.2D;
        else bd.suspicion = Math.max(0.0D, bd.suspicion - 0.3D);

        if (bd.suspicion > 7.0D && buffer.increase(wrapper.getPlayer(), 1.0D) > 5.0D) {
            data.lowerBaritoneTrust(4.0D);
            flag(data, String.format("Robotic mining pattern trust=%.1f sus=%.2f", data.getBaritoneTrust(), bd.suspicion));
            buffer.reset(wrapper.getPlayer(), 2.0D);
            bd.suspicion = 2.0D;
        }

        bd.lastYaw = dyaw;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    private static final class BotData {
        long lastDigMs;
        float lastYaw;
        double suspicion;
    }
}
