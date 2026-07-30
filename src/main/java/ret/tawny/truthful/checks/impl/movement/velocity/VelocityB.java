package ret.tawny.truthful.checks.impl.movement.velocity;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.sync.VelocityQueue;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'B', type = CheckType.VELOCITY)
public final class VelocityB extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0D);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;
        final PlayerData data = wrapper.getPlayerData();
        if (data == null) return;

        if (data.isServerFrozen() || data.isTeleportTick() || data.isMovementExempt()) {
            buffer.decrease(data.getPlayer(), 0.25D);
            return;
        }

        VelocityQueue queue = data.getVelocities();
        if (queue.isEmpty()) return;

        double actualY = data.getDeltaY();
        double lastY = data.getLastDeltaY();
        boolean flagged = false;
        double minVDev = Double.MAX_VALUE;
        String flagReason = null;

        for (VelocityQueue.VelocityEntry entry : queue) {
            if (entry.isAcked() && entry.getAckTick() <= 1 && !entry.isExplosion()) {
                Vector initialVel = entry.getCurrent();
                double expectedY = initialVel.getY();

                if (expectedY < 0.08D || data.isUnderBlock()) continue;

                double gravity = data.getGravity();
                int jumpBoost = data.getPotionLevel(PotionEffectType.JUMP_BOOST);
                double baseJumpStrength = data.getJumpStrength() + (jumpBoost * 0.1D);

                // Candidate 1: Standard gravity continuation + velocity impulse
                double predGravity = (lastY - gravity) * 0.98D + expectedY;

                // Candidate 2: Jump takeoff + velocity impulse
                double predJumpPlusVel = baseJumpStrength + expectedY;

                double predPureJump = baseJumpStrength;

                // Candidate 4: Direct Velocity Impulse
                double predDirect = expectedY;

                double dev1 = Math.abs(actualY - predGravity);
                double dev2 = Math.abs(actualY - predJumpPlusVel);
                double dev3 = Math.abs(actualY - predPureJump);
                double dev4 = Math.abs(actualY - predDirect);

                double bestDev = Math.min(dev1, Math.min(dev2, Math.min(dev3, dev4)));
                if (bestDev < minVDev) {
                    minVDev = bestDev;
                }

                double allowedVDev = 0.08D; // Expanded to 0.08m for sub-tick jump timing

                if (minVDev > allowedVDev && !data.isServerGround()) {
                    flagged = true;
                    flagReason = String.format("Vertical Impulse Fail Y=%.4f expectedY=%.4f dev=%.4f",
                            actualY, expectedY, minVDev);
                }
            }
        }

        if (flagged) {
            flag(data, flagReason, 2.0 + (minVDev * 8.0));
            if (buffer.increase(data.getPlayer(), 1.5) > 5.0D) {
                buffer.reset(data.getPlayer(), 2.0D);
            }
        } else {
            buffer.decrease(data.getPlayer(), 0.15D);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}