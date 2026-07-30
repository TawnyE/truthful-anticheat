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

@CheckData(order = 'B', type = CheckType.SCAFFOLD)
public final class ScaffoldB extends Check {

    private static final int WINDOW_MS = 1500;
    private static final int MIN_ALTERNATIONS = 5;
    private static final float MIN_YAW_SHIFT = 60.0F;

    private final CheckBuffer buffer = new CheckBuffer(8.0);

    @Override
    public void handlePacketPlayerReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        if (!(event.getPlayer() instanceof Player p)) return;

        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(p);
        if (WorldCheckSupport.skipBasic(data, p)) return;

        ScaffoldSupport.PlacementContext ctx = ScaffoldSupport.context(data);
        if (ctx == null || !ctx.scaffoldLike) { buffer.decrease(p, 0.15); return; }

        long now = System.currentTimeMillis();
        boolean isUp = ctx.face == BlockFace.UP;
        boolean isSide = ctx.face != BlockFace.UP && ctx.face != BlockFace.DOWN;
        if (!isUp && !isSide) { buffer.decrease(p, 0.1); return; }

        ScaffoldSupport.bag().record(p.getUniqueId(), ctx, now);

        List<ScaffoldSupport.PlacementSlot> recent = ScaffoldSupport.bag().recent(p.getUniqueId(), 20);
        if (recent.size() < 5) { buffer.decrease(p, 0.1); return; }

        long windowStart = now - WINDOW_MS;
        List<ScaffoldSupport.PlacementSlot> windowed = new ArrayList<>();
        for (ScaffoldSupport.PlacementSlot slot : recent) {
            if (slot.timestamp >= windowStart) windowed.add(slot);
        }
        if (windowed.size() < MIN_ALTERNATIONS) { buffer.decrease(p, 0.05); return; }

        int alternations = 0;
        boolean lastWasUp = windowed.get(0).ctx.face == BlockFace.UP;
        for (int i = 1; i < windowed.size(); i++) {
            boolean isCurrentUp = windowed.get(i).ctx.face == BlockFace.UP;
            if (isCurrentUp != lastWasUp) {
                float yawDelta = Math.abs(windowed.get(i).ctx.yawError - windowed.get(i - 1).ctx.yawError);
                if (yawDelta >= MIN_YAW_SHIFT) {
                    alternations++;
                    lastWasUp = isCurrentUp;
                }
            }
        }

        if (alternations >= MIN_ALTERNATIONS) {
            if (buffer.increase(p, 1.15) > 5.5) {
                flag(data, String.format("RapidAlt. alt=%d", alternations));
                buffer.reset(p, 3.0);
            }
            return;
        }
        buffer.decrease(p, 0.12);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        ScaffoldSupport.bag().remove(event.getPlayer().getUniqueId());
    }
}