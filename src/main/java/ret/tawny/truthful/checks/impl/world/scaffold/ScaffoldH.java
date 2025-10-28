package ret.tawny.truthful.checks.impl.world.scaffold;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;

@CheckData(order = 'H', type = CheckType.SCAFFOLD)
@SuppressWarnings("unused")
public final class ScaffoldH extends Check {

    private final CheckBuffer buffer = new CheckBuffer(5.0);

    @Override
    public void handlePacketPlayerReceive(final PacketEvent event) {
        if (!event.getPacketType().equals(PacketType.Play.Client.BLOCK_PLACE)) return;

        final Player player = event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.getDeltaXZ() < 0.1) return; // Only check when moving

        int ticksSinceLastPlace = data.getTicksTracked() - data.getLastBlockPlaceTick();

        // Calculate the minimum number of ticks required to travel one block distance
        double requiredTicks = 1.0 / data.getDeltaXZ();

        // If the player places a block faster than they could have possibly moved to the next position...
        if (ticksSinceLastPlace < requiredTicks && ticksSinceLastPlace > 0) {
            if (buffer.increase(player, 1.0) > 5.0) {
                flag(data, String.format("Impossible placement cadence. TSLP: %d, RT: %.2f", ticksSinceLastPlace, requiredTicks));
            }
        } else {
            buffer.decrease(player, 0.1);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}