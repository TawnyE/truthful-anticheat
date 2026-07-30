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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CheckData(order = 'F', type = CheckType.SCAFFOLD)
public final class ScaffoldF extends Check {

    private static final long ANALYSIS_WINDOW_MS = 3000L;
    private final CheckBuffer buffer = new CheckBuffer(8.0);

    @Override
    public void handlePacketPlayerReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        if (!(event.getPlayer() instanceof Player p)) return;

        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(p);
        if (WorldCheckSupport.skipBasic(data, p)) return;

        ScaffoldSupport.PlacementContext ctx = ScaffoldSupport.context(data);
        if (ctx == null || !ctx.scaffoldLike || ctx.face != BlockFace.UP) {
            buffer.decrease(p, 0.05);
            return;
        }

        long now = System.currentTimeMillis();
        ScaffoldSupport.bag().record(p.getUniqueId(), ctx, now);

        List<ScaffoldSupport.PlacementSlot> recent = ScaffoldSupport.bag().recent(p.getUniqueId(), 80);
        long cutoff = now - ANALYSIS_WINDOW_MS;
        List<Long> gaps = new ArrayList<>();
        long prevTimestamp = -1;

        for (ScaffoldSupport.PlacementSlot slot : recent) {
            if (slot.timestamp < cutoff) continue;
            if (prevTimestamp >= 0) {
                long gap = slot.timestamp - prevTimestamp;
                if (Math.abs(gap - 200L) > 25L && gap >= 50L && gap <= 500L) {
                    gaps.add(gap);
                }
            }
            prevTimestamp = slot.timestamp;
        }

        if (gaps.size() < 5) { buffer.decrease(p, 0.05); return; }

        double sum = 0.0D;
        for (long g : gaps) sum += g;
        double mean = sum / gaps.size();

        double variance = 0.0D;
        for (long g : gaps) {
            double d = g - mean;
            variance += d * d;
        }
        double cv = mean > 0 ? Math.sqrt(variance / gaps.size()) / mean : 0.0D;

        Map<Long, Integer> bucketCounts = new HashMap<>();
        for (long g : gaps) {
            long bucket = Math.round(g / 10.0D) * 10L;
            bucketCounts.merge(bucket, 1, Integer::sum);
        }

        int largestBucket = bucketCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double bucketConcentration = (double) largestBucket / gaps.size();

        if (cv < 0.15 && bucketConcentration > 0.75) {
            if (buffer.increase(p, 1.0) > 4.5) {
                flag(data, String.format("StatPerfect. cv=%.3f bucket=%.2f mean=%dms n=%d", cv, bucketConcentration, Math.round(mean), gaps.size()));
                buffer.reset(p, 3.0);
            }
            return;
        }

        buffer.decrease(p, 0.08);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        ScaffoldSupport.bag().remove(event.getPlayer().getUniqueId());
    }
}