package ret.tawny.truthful.checks.impl.world.scaffold;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.checks.impl.world.WorldCheckSupport;
import ret.tawny.truthful.data.PlayerData;

import java.util.ArrayList;
import java.util.List;

@CheckData(order = 'C', type = CheckType.SCAFFOLD)
public final class ScaffoldC extends Check {

    private static final long INTERVAL_WINDOW_MS = 4000;
    private static final int MIN_SAMPLES = 5;

    private final CheckBuffer buffer = new CheckBuffer(10.0);

    @Override
    public void handlePacketPlayerReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        if (!(event.getPlayer() instanceof Player p)) return;

        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(p);
        if (WorldCheckSupport.skipBasic(data, p)) return;

        ScaffoldSupport.PlacementContext ctx = ScaffoldSupport.context(data);
        if (ctx == null || !ctx.scaffoldLike) { buffer.decrease(p, 0.1); return; }

        boolean isUp = ctx.face == BlockFace.UP || ctx.placedY > ctx.clickedY;
        if (!isUp) { buffer.decrease(p, 0.05); return; }

        long now = System.currentTimeMillis();
        ScaffoldSupport.bag().record(p.getUniqueId(), ctx, now);

        List<ScaffoldSupport.PlacementSlot> recent = ScaffoldSupport.bag().recent(p.getUniqueId(), 60);

        long cutoff = now - INTERVAL_WINDOW_MS;
        List<Long> intervals = new ArrayList<>();
        long prev = -1;
        for (ScaffoldSupport.PlacementSlot slot : recent) {
            if (slot.timestamp < cutoff) continue;
            if (prev >= 0) {
                long gap = slot.timestamp - prev;
                if (Math.abs(gap - 200L) > 25L) {
                    intervals.add(Math.max(1L, gap));
                }
            }
            prev = slot.timestamp;
        }

        if (intervals.size() < MIN_SAMPLES) { buffer.decrease(p, 0.1); return; }

        double sum = 0.0D;
        for (long iv : intervals) sum += iv;
        double mean = sum / intervals.size();
        double variance = 0.0D;
        for (long iv : intervals) {
            double d = iv - mean;
            variance += d * d;
        }
        double cv = mean > 0 ? Math.sqrt(variance / intervals.size()) / mean : 0.0D;

        if (cv < 0.06) {
            if (buffer.increase(p, 1.0) > 5.0) {
                flag(data, String.format("TowerPattern. cv=%.3f mean=%.0fms samples=%d", cv, mean, intervals.size()));
                buffer.reset(p, 3.0);
            }
        } else {
            buffer.decrease(p, 0.1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        ScaffoldSupport.bag().remove(event.getPlayer().getUniqueId());
    }
}