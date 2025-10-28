package ret.tawny.truthful.checks.impl.world.scaffold;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'F', type = CheckType.SCAFFOLD)
@SuppressWarnings("unused")
public final class ScaffoldF extends Check {

    private final CheckBuffer buffer = new CheckBuffer(20.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        Player player = relMovePacketWrapper.getPlayer();
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return;

        long timeSincePlace = System.currentTimeMillis() - data.getLastBlockPlaceTime();

        // Only analyze pitch stability within a short window after a block is placed.
        if (timeSincePlace > 300) {
            buffer.decrease(player, 0.5); // Forgive buffer when not actively scaffolding.
            return;
        }

        float deltaPitch = Math.abs(data.getDeltaPitch());
        float deltaYaw = Math.abs(data.getDeltaYaw());

        // --- FINAL REWORKED LOGIC ---
        // A static pitch is only suspicious if the player is also turning. A human cannot
        // move their mouse horizontally without some minor vertical jitter. A cheat can.
        // This stops the check from flagging players bridging in a straight line.
        boolean suspicious = deltaPitch < 0.001f      // Pitch is perfectly static
                && deltaYaw > 0.1f                     // AND Yaw is changing
                && data.getPitch() > 70                // AND they are looking down (bridging)
                && data.getDeltaXZ() > 0.1;            // AND they are moving

        if (suspicious) {
            if (buffer.increase(player, 1.0) > 20.0) {
                String debug = String.format("Unnatural pitch stability while turning. dP: %.4f, dY: %.4f", deltaPitch, deltaYaw);
                flag(data, debug);
            }
        } else {
            buffer.decrease(player, 0.75);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}