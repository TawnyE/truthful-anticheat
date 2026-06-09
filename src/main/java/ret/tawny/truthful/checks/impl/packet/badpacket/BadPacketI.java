package ret.tawny.truthful.checks.impl.packet.badpacket;

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

/**
 * BadPacketI: Duplicate Position Spam Detection
 */
@CheckData(order = 'I', type = CheckType.BAD_PACKET)
public final class BadPacketI extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, PositionData> dataMap = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate())
            return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isTeleportTick())
            return;

        // FIXED: Added data.isInsideVehicle()
        // When riding a vehicle, the client sends duplicate position packets to keep the player
        // glued to the seat. This is normal vanilla behavior.
        if (data.isServerFrozen() || data.getTickFreezeGraceTicks() > 0 || data.isInsideVehicle())
            return;

        double x = wrapper.getX();
        double y = wrapper.getY();
        double z = wrapper.getZ();

        PositionData posData = dataMap.computeIfAbsent(player.getUniqueId(), k -> new PositionData());

        if (posData.lastX == x && posData.lastY == y && posData.lastZ == z) {
            posData.duplicateCount++;
            long now = System.currentTimeMillis();
            long timeSinceLast = now - posData.lastTime;

            if (posData.duplicateCount > 5 && timeSinceLast < 100) {
                if (buffer.increase(player, 1.0) > 6.0) {
                    flag(data, String.format("Position Spam. Duplicates: %d in %dms",
                            posData.duplicateCount, timeSinceLast));
                    buffer.reset(player, 3.0);
                    posData.duplicateCount = 0;
                }
            }
        } else {
            posData.duplicateCount = 0;
            buffer.decrease(player, 0.2);
        }

        posData.lastX = x;
        posData.lastY = y;
        posData.lastZ = z;
        posData.lastTime = System.currentTimeMillis();
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    private static class PositionData {
        double lastX, lastY, lastZ;
        long lastTime = 0;
        int duplicateCount = 0;
    }
}