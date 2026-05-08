package ret.tawny.truthful.checks.impl.world.fastbreak;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.checks.impl.world.WorldCheckSupport;
import ret.tawny.truthful.data.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'A', type = CheckType.FAST_BREAK)
public final class FastBreakA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, DigInfo> digMap = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (WorldCheckSupport.skipBasic(data, player)) return;

        WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
        DiggingAction action = digging.getAction();

        if (action == DiggingAction.START_DIGGING) {
            digMap.put(player.getUniqueId(), new DigInfo(data.getTicksTracked(), digging.getBlockPosition()));
            return;
        }

        if (action == DiggingAction.CANCELLED_DIGGING) {
            digMap.remove(player.getUniqueId());
            return;
        }

        if (action != DiggingAction.FINISHED_DIGGING) return;

        DigInfo info = digMap.remove(player.getUniqueId());
        if (info == null) return;

        int elapsed = data.getTicksTracked() - info.startTick;
        String block = data.getWorldCache().getBlockState(info.pos.getX(), info.pos.getY(), info.pos.getZ()).getType().getName().toLowerCase();

        int required = requiredTicks(block, data);
        if (elapsed < required) {
            if (buffer.increase(player, 1.5D) > 15.0D) {
                flag(data, String.format("FastBreak elapsed=%d req=%d block=%s", elapsed, required, block));
                buffer.reset(player, 5.0D);
            }
        } else buffer.decrease(player, 0.5D);
    }

    private int requiredTicks(String name, PlayerData data) {
        if (name.contains("grass") || name.contains("flower") || name.contains("torch") || name.contains("sapling")) return 0;

        int req = 2;
        if (name.contains("stone") || name.contains("deepslate") || name.contains("ore") || name.contains("brick")) req = 3;
        if (name.contains("obsidian") || name.contains("ancient_debris")) req = 20;

        if (data.hasPotionEffect(PotionEffectType.HASTE)) req = Math.max(0, req - 2);

        int eff = data.getEnchantLevel("efficiency");
        if (eff >= 5) req = Math.max(0, req - 3);
        else if (eff >= 3) req = Math.max(0, req - 1);

        return req;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        digMap.remove(event.getPlayer().getUniqueId());
    }

    private static final class DigInfo {
        final int startTick;
        final Vector3i pos;
        DigInfo(int startTick, Vector3i pos) { this.startTick = startTick; this.pos = pos; }
    }
}
