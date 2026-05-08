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

@CheckData(order = 'A', type = CheckType.SCAFFOLD)
public final class ScaffoldA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);

    @Override
    public void handlePacketPlayerReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        if (!(event.getPlayer() instanceof Player p)) return;

        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(p);
        if (WorldCheckSupport.skipBasic(data, p)) return;

        ScaffoldSupport.PlacementContext ctx = ScaffoldSupport.context(data);
        if (ctx == null || !ctx.scaffoldLike || !ScaffoldSupport.shouldLookDown(ctx)) {
            buffer.decrease(p, 0.2);
            return;
        }

        float pitch = data.getPitch();
        float deltaPitch = Math.abs(data.getDeltaPitch());

        // Scaffold Pitch Snap Detection
        // Humans smoothly pan their camera down. Scaffold clients instantly snap it.
        if (pitch > 62.0F && deltaPitch > 15.0F && ctx.pitchError < 28.0F && ctx.yawError < 70.0F) {
            if (buffer.increase(p, 1.25) > 5.0) {
                flag(data, String.format("Pitch Snap. pitch=%.1f dPitch=%.1f req=%.1f pErr=%.1f yErr=%.1f",
                        pitch, deltaPitch, ctx.requiredPitch, ctx.pitchError, ctx.yawError));
                buffer.reset(p, 4.0);
            }
        } else {
            buffer.decrease(p, 0.4);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        buffer.remove(e.getPlayer());
    }
}
