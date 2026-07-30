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

    private static final double EXPANDER = 16777216.0D;
    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, AimData> dataMap = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper event) {
        if (!event.isRotationUpdate()) return;

        final Player player = event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || data.isRotationExempt() || data.isInsideVehicle()) return;

        if (data.isServerFrozen() || data.getTickFreezeGraceTicks() > 0) {
            buffer.decrease(player, 0.5);
            return;
        }

        AimData aimData = dataMap.computeIfAbsent(player.getUniqueId(), k -> new AimData());

        final float deltaPitch = data.getRotationDeviation(true);
        final float deltaYaw = data.getRotationDeviation(false);
        final float pitch = data.getPitch();

        if (Math.abs(pitch) > 89.0f || deltaPitch > 15.0f || deltaYaw > 15.0f) {
            aimData.lastDeltaPitch = deltaPitch;
            aimData.lastDeltaYaw = deltaYaw;
            buffer.decrease(player, 0.2);
            return;
        }

        if (deltaPitch < 0.15f || deltaYaw < 0.15f) {
            aimData.lastDeltaPitch = deltaPitch;
            aimData.lastDeltaYaw = deltaYaw;
            return;
        }

        final long currentPitch = (long) (deltaPitch * EXPANDER);
        final long lastPitch = (long) (aimData.lastDeltaPitch * EXPANDER);
        final long gcdPitch = MathHelper.getGcd(currentPitch, lastPitch);
        final double stepPitch = gcdPitch / EXPANDER;

        final long currentYaw = (long) (deltaYaw * EXPANDER);
        final long lastYaw = (long) (aimData.lastDeltaYaw * EXPANDER);
        final long gcdYaw = MathHelper.getGcd(currentYaw, lastYaw);
        final double stepYaw = gcdYaw / EXPANDER;

        final double step = Math.max(stepPitch, stepYaw);
        final double primaryDelta = stepPitch >= stepYaw ? deltaPitch : deltaYaw;

        if (step > 0.005D) {
            double pixels = primaryDelta / step;
            double error = Math.abs(pixels - Math.round(pixels));

            if (error < 0.001D) {
                double val = Math.pow(step / 1.2D, 1.0D / 3.0D);
                double sens = (val - 0.2D) / 0.6D;

                if (sens < -0.05D || sens > 5.0D) {
                    if (buffer.increase(player, 1.0) > 6.0) {
                        flag(data, String.format("Bad Sensitivity Step: %.5f, Sens: %.2f%%", step, sens * 100));
                    }
                } else {
                    buffer.decrease(player, 0.15);
                }
            } else {
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
    }
}