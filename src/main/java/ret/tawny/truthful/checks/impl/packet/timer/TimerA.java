package ret.tawny.truthful.checks.impl.packet.timer;

import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.ExemptionType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'A', type = CheckType.TIMER)
public final class TimerA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(15.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;

        final PlayerData data = wrapper.getPlayerData();
        if (data == null) return;

        // FIXED: Added data.isInsideVehicle() (I may be retrat)
        // Vehicles (especially boats on ice) send extra VEHICLE_MOVE packets to correct collisions,
        // which completely inflates the packet count and false flags Timer.
        if (data.isInsideVehicle() || data.isMovementExempt()) {
            buffer.decrease(wrapper.getPlayer(), 0.5D);
            return;
        }

        if (data.isTeleportTick() || data.isServerFrozen() || data.isExempt(ExemptionType.RIPTIDE)) {
            buffer.decrease(wrapper.getPlayer(), 0.5D);
            return;
        }

        long now = System.nanoTime();
        long aheadNanos = data.getTimerBalanceRealTime() - now;

        if (aheadNanos > 350_000_000L) {
            long aheadMs = aheadNanos / 1_000_000L;
            double severity = aheadMs > 500L ? 2.0D : 1.0D;

            if (buffer.increase(wrapper.getPlayer(), severity) > 15.0D) {
                flag(data, String.format("Timer speed balance=+%dms", aheadMs));
                data.addTimerBalance(-200_000_000L);
                buffer.reset(wrapper.getPlayer(), 5.0D);
            }
        } else {
            buffer.decrease(wrapper.getPlayer(), 0.1D);
        }
    }

    @Override
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}