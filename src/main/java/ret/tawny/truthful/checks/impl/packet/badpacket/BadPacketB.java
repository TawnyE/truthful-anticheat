package ret.tawny.truthful.checks.impl.packet.badpacket;

import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

/**
 * BadPacketB - Invalid Rotation Detection
 *
 * Verifies that the player's pitch remains within the vanilla bounds (-90 to 90).
 */
@CheckData(order = 'B', type = CheckType.BAD_PACKET)
public final class BadPacketB extends Check {

    private final CheckBuffer buffer = new CheckBuffer(3.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isRotationUpdate())
            return;

        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(wrapper.getPlayer());

        // Resync
        if (data == null || data.isServerFrozen() || data.isTeleportTick())
            return;

        float pitch = wrapper.getPitch();

        // 1. Logic: Boundary Check
        // Minecraft pitch is clamped between -90 (Up) and 90 (Down).
        // Sending values outside this range indicates head-spoofing or malicious clients.
        if (Math.abs(pitch) > 90.0f) {
            if (buffer.increase(wrapper.getPlayer(), 1.0) > 1.0) {
                flag(data, String.format("Illegal Rotation Pitch. Pitch: %.2f", pitch));
                buffer.reset(wrapper.getPlayer(), 0.5);
            }
        } else {
            buffer.decrease(wrapper.getPlayer(), 0.05);
        }
    }
}