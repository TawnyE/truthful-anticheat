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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'H', type = CheckType.SCAFFOLD)
public final class ScaffoldH extends Check {

	private final CheckBuffer buffer = new CheckBuffer(9.0);
	private final Map<UUID, Integer> stableTicks = new ConcurrentHashMap<>();

	@Override
	public void handlePacketPlayerReceive(PacketReceiveEvent event) {
		if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
		if (!(event.getPlayer() instanceof Player p)) return;

		PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(p);
		if (WorldCheckSupport.skipBasic(data, p)) return;

		ScaffoldSupport.PlacementContext ctx = ScaffoldSupport.context(data);
		if (ctx == null) { decrease(p); return; }

		boolean isFootOrBelow = ctx.placedY <= (int) Math.floor(data.getY());
		boolean activePlacement = ctx.scaffoldLike && isFootOrBelow && ctx.face != BlockFace.DOWN;

		if (!activePlacement) { decrease(p); return; }

		// Exempt legitimate straight-line bridging (holding fixed crosshair angle)
		if (data.isSneaking() || Math.abs(data.getDeltaYaw()) < 0.1F) {
			decrease(p);
			return;
		}

		int q = stableTicks.getOrDefault(p.getUniqueId(), 0);
		if (q > 25) {
			float rot = Math.abs(data.getDeltaYaw()) + Math.abs(data.getDeltaPitch());
			if (rot < 1.0F && data.getDeltaXZ() > 0.22D) {
				if (buffer.increase(p, 0.7) > 5.5) {
					flag(data, String.format("BalanceHold q=%d rot=%.1f xz=%.4f", q, rot, data.getDeltaXZ()));
					buffer.reset(p, 3.0);
				}
				return;
			}
		}
		decrease(p);
	}

	@Override
	public void handleRelMove(final RelMovePacketWrapper event) {
		Player p = event.getPlayer();
		PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(p);
		if (data == null || WorldCheckSupport.skipBasic(data, p)) return;

		ScaffoldSupport.PlacementContext ctx = ScaffoldSupport.context(data);
		boolean active = ctx != null && ctx.scaffoldLike && ctx.placedY <= (int) Math.floor(data.getY()) && ctx.face != BlockFace.DOWN;

		if (active && data.getDeltaXZ() < 0.12D && Math.abs(data.getDeltaYaw()) < 0.5F && Math.abs(data.getDeltaPitch()) < 0.5F) {
			stableTicks.merge(p.getUniqueId(), 1, Integer::sum);
		} else {
			stableTicks.computeIfPresent(p.getUniqueId(), (k, v) -> Math.max(0, v - 1));
		}
	}

	private void decrease(Player p) { buffer.decrease(p, 0.1); }

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		buffer.remove(event.getPlayer());
		stableTicks.remove(event.getPlayer().getUniqueId());
	}
}