package ret.tawny.truthful.checks.impl.combat.anchor;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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

@CheckData(order = 'A', type = CheckType.ANCHOR)
public final class AnchorAuraA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, Long> lastInteractTime = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT)
            return;

        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null)
            return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemStack offhand = player.getInventory().getItemInOffHand();

        boolean holdingGlowstone = (hand != null && hand.getType() == Material.GLOWSTONE) ||
                (offhand != null && offhand.getType() == Material.GLOWSTONE);

        // Anchor Aura Flow:
        // 1. Right Click with Glowstone (Charge)
        // 2. Right Click with Hand/Item (Explode)

        // This check flags if those two actions happen inhumanly fast.

        long now = System.currentTimeMillis();
        long last = lastInteractTime.getOrDefault(player.getUniqueId(), 0L);
        long diff = now - last;

        if (holdingGlowstone) {
            // Player is charging
            lastInteractTime.put(player.getUniqueId(), now);
        } else {
            // Player is potentially exploding (interacting without glowstone)
            // If they charged < 100ms ago, it's a macro.
            // Human: Click (Charge) -> Release -> Click (Explode) takes time.

            if (diff > 0 && diff < 80) {
                // Verify they are actually looking at a block (simple check)
                if (data.getCurrentBlockPlacement() != null) {
                    if (buffer.increase(player, 1.5) > 6.0) {
                        flag(data, String.format("Fast Anchor (Charge->Explode). Delay: %dms", diff));
                        // Anchor macros are combat hacks -> lagback recommended
                        data.executeLagback();
                        buffer.reset(player, 3.0);
                    }
                }
            } else {
                buffer.decrease(player, 0.1);
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        lastInteractTime.remove(event.getPlayer().getUniqueId());
    }
}