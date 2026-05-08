package ret.tawny.truthful.checks.impl.combat.autoclicker;

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
import ret.tawny.truthful.data.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'D', type = CheckType.AUTOCLICKER)
public final class AutoClickerD extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, Long> lastClickNano = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDelayNano = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingAttacks = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            final Player player = (Player) event.getPlayer();
            if (Truthful.getInstance().isBedrockPlayer(player)) return;

            final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
            if (data == null || data.isServerFrozen() || data.isExempt()) return;

            // Only check attacks that actually hit an entity
            Integer entityId = pendingAttacks.remove(player.getUniqueId());
            if (entityId == null) {
                // This is a ground punch - ignore it
                return;
            }

            long now = System.nanoTime();
            long last = lastClickNano.getOrDefault(player.getUniqueId(), 0L);
            long prevDelay = lastDelayNano.getOrDefault(player.getUniqueId(), 0L);

            if (last != 0) {
                long diff = now - last;

                // Increased from 15ms to 8ms (125 CPS) - short bursts of rapid clicking are humanly possible
                // Only flag if BOTH current and previous delay are suspiciously fast
                if (diff < 8_000_000 && prevDelay > 0 && prevDelay < 8_000_000) {
                    if (buffer.increase(player, 2.0) > 6.0) {
                        flag(data, String.format("Hardware Double-Click (%.1f ms)", diff / 1_000_000.0));
                        buffer.reset(player, 5.0);
                    }
                } else if (diff > 8_000_000) {
                    buffer.decrease(player, 0.4);
                }

                lastDelayNano.put(player.getUniqueId(), diff);
            }

            lastClickNano.put(player.getUniqueId(), now);
        }
    }

    // Track actual attacks (not ground punches)
    @Override
    public void onAttack(final org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            pendingAttacks.put(event.getDamager().getUniqueId(), event.getEntity().getEntityId());
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastClickNano.remove(uuid);
        lastDelayNano.remove(uuid);
        pendingAttacks.remove(uuid);
        buffer.remove(event.getPlayer());
    }
}