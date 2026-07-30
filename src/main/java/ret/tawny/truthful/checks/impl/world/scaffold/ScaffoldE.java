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

@CheckData(order = 'E', type = CheckType.SCAFFOLD)
public final class ScaffoldE extends Check {

    private final CheckBuffer buffer = new CheckBuffer(8.0);

    @Override
    public void handlePacketPlayerReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
        if (!(event.getPlayer() instanceof Player p)) return;

        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(p);
        if (WorldCheckSupport.skipBasic(data, p)) return;

        ScaffoldSupport.PlacementContext ctx = ScaffoldSupport.context(data);
        if (ctx == null || !ctx.scaffoldLike) { buffer.decrease(p, 0.1); return; }

        double eyeX = data.getX();
        double eyeY = data.getY() + data.getEyeHeight(false, data.isSneaking(), data.isSwimming());
        double eyeZ = data.getZ();
        double faceX = ctx.placedX + 0.5D;
        double faceY = ctx.placedY + 0.5D;
        double faceZ = ctx.placedZ + 0.5D;

        double dx = faceX - eyeX;
        double dy = faceY - eyeY;
        double dz = faceZ - eyeZ;
        double euclidDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Exempt standing directly on block edge (< 0.25m distance geometrically forces alignment)
        if (euclidDist < 0.25D) {
            buffer.decrease(p, 0.1);
            return;
        }

        double requiredDX = ctx.placedX + 0.5D - data.getX();
        double requiredDZ = ctx.placedZ + 0.5D - data.getZ();
        double requiredHorizontal = Math.hypot(requiredDX, requiredDZ);
        double requiredYawRad = Math.atan2(-requiredDX, requiredDZ);
        double yawRad = Math.toRadians(data.getYaw());
        double yawDiff = Math.abs(Math.sin(yawRad - requiredYawRad)) * requiredHorizontal;

        boolean tooPerfect = euclidDist < 0.45D && yawDiff < 0.08D && ctx.pitchError < 2.0F;

        if (tooPerfect) {
            if (buffer.increase(p, 0.85) > 5.5) {
                flag(data, String.format("Nudge. dist=%.2f yawNudge=%.3f pitchErr=%.1f", euclidDist, yawDiff, ctx.pitchError));
                buffer.reset(p, 3.0);
            }
        } else {
            buffer.decrease(p, 0.1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}