package ret.tawny.truthful.checks.impl.combat.aim;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.math.MathHelper;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'A', type = CheckType.AIM)
public final class AimA extends Check {

    private static final double MIN_VALID_SENSITIVITY = -0.05; // Slightly below 0 to allow rounding
    private static final double MAX_VALID_SENSITIVITY = 5.0; // Very high

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, AimData> dataMap = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper event) {
        if (!event.isRotationUpdate()) return;

        final Player player = event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || data.isRotationExempt()) return;

        // FIX: Boats rotate the player's camera via keyboard (A/D), producing perfectly linear
        // rotations with zero acceleration that look exactly like GCD-spoofing aimbots. (my sped)
        if (data.isInsideVehicle()) {
            buffer.decrease(player, 0.5);
            return;
        }

        if (data.isServerFrozen() || data.getTickFreezeGraceTicks() > 0) {
            buffer.decrease(player, 0.5);
            return;
        }

        AimData aimData = dataMap.computeIfAbsent(player.getUniqueId(), k -> new AimData());

        final float deltaPitch = data.getRotationDeviation(true);
        final float deltaYaw = data.getRotationDeviation(false);
        final float pitch = data.getPitch();

        if (Math.abs(pitch) > 89.0f) {
            aimData.lastDeltaPitch = deltaPitch;
            buffer.decrease(player, 0.1);
            return;
        }

        // Ignore extreme flicks
        if (deltaPitch > 15.0 || deltaYaw > 15.0) {
            aimData.lastDeltaPitch = deltaPitch;
            buffer.decrease(player, 0.25);
            return;
        }

        // Cinematic / Smooth camera check (OptiFine/Vanilla)
        // Acceleration is very low and continuous
        float accelPitch = Math.abs(deltaPitch - aimData.lastDeltaPitch);
        if (accelPitch < 0.05 && deltaPitch > 0.0) {
            aimData.cinematicSmoothTicks = Math.min(20, aimData.cinematicSmoothTicks + 1);
        } else {
            aimData.cinematicSmoothTicks = Math.max(0, aimData.cinematicSmoothTicks - 2);
        }

        if (aimData.cinematicSmoothTicks > 5) {
            buffer.decrease(player, 0.2);
            aimData.lastDeltaPitch = deltaPitch;
            return;
        }

        // FIX: Rised from 0.1 to 0.2 to handle high-DPI + low-sensitivity mice.
        // Small deltas cause numerically unstable GCD calculations (precision loss in long cast).
        if (deltaPitch < 0.2) {
            aimData.lastDeltaPitch = deltaPitch;
            return;
        }

        // Calculate GCD for both pitch and yaw (vanilla sensitivity applies to both axes)
        final long currentPitch = (long) (deltaPitch * 16777216.0);
        final long lastPitch = (long) (aimData.lastDeltaPitch * 16777216.0);
        final long gcdPitch = MathHelper.getGcd(currentPitch, lastPitch);
        final double stepPitch = gcdPitch / 16777216.0;

        final long currentYaw = (long) (deltaYaw * 16777216.0);
        final long lastYaw = (long) (aimData.lastDeltaYaw * 16777216.0);
        final long gcdYaw = MathHelper.getGcd(currentYaw, lastYaw);
        final double stepYaw = gcdYaw / 16777216.0;

        // Use the larger step (more likely to be the true sensitivity step)
        final double step = Math.max(stepPitch, stepYaw);
        final double primaryDelta = stepPitch >= stepYaw ? deltaPitch : deltaYaw;

        if (step > 0.005) {
            double pixels = primaryDelta / step;
            double error = Math.abs(pixels - Math.round(pixels));

            // If the cheat engine attempts to spoof a vanilla step, but fails to use a legitimate sensitivity scale
            if (error < 0.001) {
                double val = Math.pow(step / 1.2, 1.0 / 3.0);
                double sens = (val - 0.2) / 0.6;

                if (sens < MIN_VALID_SENSITIVITY || sens > MAX_VALID_SENSITIVITY) {
                    if (buffer.increase(player, 1.0) > 6.0) {
                        flag(data, String.format("Bad Sensitivity. Step: %.5f, Sens: %.2f%%", step, sens * 100));
                    }
                } else {
                    buffer.decrease(player, 0.15);
                }
            } else {
                // 1.14+ Raw Input and OptiFine Fast Math naturally cause floating-point drift
                buffer.decrease(player, 0.05);
            }
        } else {
            buffer.decrease(player, 0.05);
        }

        aimData.lastDeltaPitch = deltaPitch;
        aimData.lastDeltaYaw = deltaYaw;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    private static class AimData {
        float lastDeltaPitch = 0f;
        float lastDeltaYaw = 0f;
        int cinematicSmoothTicks = 0;
    }
}