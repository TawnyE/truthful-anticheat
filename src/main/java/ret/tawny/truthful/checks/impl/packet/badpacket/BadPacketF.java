package ret.tawny.truthful.checks.impl.packet.badpacket;

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

/**
 * BadPacketF: Invalid Ground State Detection
 *
 * Detects when a player claims to be on ground while clearly in the air.
 * Used by:
 * 1. NoFall hacks (spoofing ground to avoid fall damage)
 * 2. Fly hacks with ground spoofing
 * 3. Speed hacks (ground speed while airborne)
 *
 * Version safe: All versions 1.8+
 */
@CheckData(order = 'F', type = CheckType.BAD_PACKET)
public final class BadPacketF extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isTeleportTick())
            return;

        // Exemptions
        if (data.isMovementExempt() || data.isInsideVehicle())
            return;
        if (data.isInLiquid() || data.isOnClimbable() || data.isInWeb())
            return;
        if (data.isGliding() || data.isRiptiding() || data.isUsingRiptide())
            return;

        // Check if claiming ground while having significant air ticks
        boolean claimsGround = wrapper.isGround();
        int airTicks = data.getAirTicks();
        double deltaY = data.getDeltaY();

        // If claiming ground but:
        // 1. Has been in air for 5+ ticks
        // 2. Has downward velocity (falling)
        // 3. Not near any blocks below
        if (claimsGround && airTicks > 5 && deltaY < -0.1) {
            // Additional check: actual Y position should be near a block
            double y = data.getY();
            double yMod = y % 1.0;
            if (y < 0)
                yMod = 1.0 - Math.abs(yMod);

            // If not close to block level (not within 0.1 of .0)
            if (yMod > 0.1 && yMod < 0.9) {
                if (buffer.increase(player, 1.5) > 6.0) {
                    flag(data, String.format("Ground Spoof. AirTicks: %d, DeltaY: %.2f, Y: %.2f",
                            airTicks, deltaY, y));
                    data.executeLagback();
                    buffer.reset(player, 3.0);
                }
            }
        } else {
            buffer.decrease(player, 0.25);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}
