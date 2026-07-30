package ret.tawny.truthful.checks.impl.world.scaffold;

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
import ret.tawny.truthful.checks.impl.world.WorldCheckSupport;
import ret.tawny.truthful.data.PlayerData;

import java.util.List;

@CheckData(order = 'D', type = CheckType.SCAFFOLD)
public final class ScaffoldD extends Check {

    private static final double VANILLA_REACH = 4.5D;
    private static final double MATURE_REACH_THRESHOLD = 5.20D;
    private static final double HARD_MAX = 7.5D;
    private static final long MATURE_WINDOW_MS = 5000L;
    private static final int MATURE_MIN_SAMPLES = 4;

    private final CheckBuffer buffer = new CheckBuffer(8.0);

    @Override
    public void handlePacketPlayerReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        if (!(event.getPlayer() instanceof Player p)) return;

        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(p);
        if (WorldCheckSupport.skipBasic(data, p)) return;

        ScaffoldSupport.PlacementContext ctx = ScaffoldSupport.context(data);
        if (ctx == null) { buffer.decrease(p, 0.05); return; }

        ScaffoldSupport.ReachBag.INSTANCE.push(p.getUniqueId(), ctx.reach, System.currentTimeMillis());

        if (ctx.reach > HARD_MAX) {
            if (buffer.increase(p, 2.0) > 4.0) {
                flag(data, String.format("ReachHard. reach=%.3f hardMax=%.1f", ctx.reach, HARD_MAX));
                buffer.reset(p, 3.0);
            }
            return;
        }

        List<ScaffoldSupport.ReachSample> recent = ScaffoldSupport.ReachBag.INSTANCE.recent(p.getUniqueId(), 40);
        if (recent.size() < MATURE_MIN_SAMPLES) { buffer.decrease(p, 0.05); return; }

        long now = System.currentTimeMillis();
        double maxReach = 0.0D;
        for (ScaffoldSupport.ReachSample s : recent) {
            if (now - s.timestamp > MATURE_WINDOW_MS) break;
            if (s.dist > maxReach) maxReach = s.dist;
        }

        if (maxReach > MATURE_REACH_THRESHOLD && maxReach <= HARD_MAX) {
            double violation = (maxReach - VANILLA_REACH) / (MATURE_REACH_THRESHOLD - VANILLA_REACH);
            if (buffer.increase(p, Math.min(1.0, violation) * 1.4) > 5.0) {
                flag(data, String.format("ReachViolation. max=%.3f thresh=%.2f", maxReach, MATURE_REACH_THRESHOLD));
                buffer.reset(p, 3.0);
            }
            return;
        }

        buffer.decrease(p, 0.12);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        ScaffoldSupport.ReachBag.INSTANCE.remove(event.getPlayer().getUniqueId());
    }
}