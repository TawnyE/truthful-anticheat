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
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.List;

@CheckData(order = 'G', type = CheckType.SCAFFOLD)
public final class ScaffoldG extends Check {

    private final CheckBuffer buffer = new CheckBuffer(8.0);

    @Override
    public void handlePacketPlayerReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        if (!(event.getPlayer() instanceof Player p)) return;

        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(p);
        if (WorldCheckSupport.skipBasic(data, p)) return;

        ScaffoldSupport.PlacementContext ctx = ScaffoldSupport.context(data);
        if (ctx == null || !ctx.scaffoldLike) { buffer.decrease(p, 0.1); return; }

        boolean isUp = ctx.face == BlockFace.UP;
        boolean isFeetPlate = ctx.placedY <= Math.floor(data.getY()) + 1 && ctx.placedY >= Math.floor(data.getY()) - 1;
        if (!isUp && !isFeetPlate) { buffer.decrease(p, 0.05); return; }

        float deltaYaw = Math.abs(data.getDeltaYaw());
        float deltaPitch = Math.abs(data.getDeltaPitch());

        int sensitivityPercent = data.getSensitivityPercent();
        if (sensitivityPercent < 0) { buffer.decrease(p, 0.05); return; }

        float f = (sensitivityPercent / 200.0F) * 0.6F + 0.2F;
        float gcdStep = f * f * f * 1.2F;

        boolean yawQuantized = deltaYaw > 0.001F && Math.abs(deltaYaw % gcdStep) < 0.001F;
        boolean pitchQuantized = deltaPitch > 0.001F && Math.abs(deltaPitch % gcdStep) < 0.001F;

        if (!yawQuantized && !pitchQuantized) { buffer.decrease(p, 0.05); return; }

        List<ScaffoldSupport.RotationSnapshot> trail = ScaffoldSupport.RotationBag.INSTANCE.recent(p.getUniqueId(), 6);
        int quantCount = 0;
        for (ScaffoldSupport.RotationSnapshot snap : trail) {
            float dy = Math.abs(snap.yaw - data.getLastYaw());
            if (dy > 0.001F && Math.abs(dy % gcdStep) < 0.001F) quantCount++;
        }

        if (quantCount >= 4) {
            if (buffer.increase(p, 1.05) > 5.0) {
                flag(data, String.format("RotationSnap. qCount=%d yawQ=%s pitchQ=%s step=%.5f", quantCount, yawQuantized, pitchQuantized, gcdStep));
                buffer.reset(p, 3.0);
            }
            return;
        }

        buffer.decrease(p, 0.05);
    }

    @Override
    public void handleRelMove(final RelMovePacketWrapper event) {
        if (!event.isRotationUpdate()) return;
        Player p = event.getPlayer();
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(p);
        if (data == null || WorldCheckSupport.skipBasic(data, p)) return;

        ScaffoldSupport.RotationBag.INSTANCE.push(p.getUniqueId(), data.getYaw(), data.getPitch(), data.getTicksTracked());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        ScaffoldSupport.bag().remove(event.getPlayer().getUniqueId());
        ScaffoldSupport.RotationBag.INSTANCE.remove(event.getPlayer().getUniqueId());
    }
}