package ret.tawny.truthful.checks.impl.packet.badpacket;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@CheckData(order = 'C', type = CheckType.BAD_PACKET)
public final class BadPacketC extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, Long> useTimes = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isServerFrozen()) return;

        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            useTimes.put(player.getUniqueId(), System.currentTimeMillis());
        }

        else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);

            if (wrapper.getAction() == DiggingAction.RELEASE_USE_ITEM) {
                long now = System.currentTimeMillis();
                long start = useTimes.getOrDefault(player.getUniqueId(), 0L);
                long duration = now - start;

                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType() == Material.AIR) {
                    hand = player.getInventory().getItemInOffHand();
                }

                if (hand != null && hand.getType().name().contains("BOW")) {
                    // 1. FastBow Logic
                    // Drawing a bow requires ~150-200ms for a minimum shot.
                    // Releasing in < 60ms is physically impossible via standard client input.
                    if (duration < 60) {
                        if (buffer.increase(player, 2.0) > 4.0) {
                            flag(data, String.format("FastBow Release. Duration: %dms", duration));
                            buffer.reset(player, 2.0);
                        }
                    } else {
                        buffer.decrease(player, 0.25);
                    }
                }
            }
        }
    }
}