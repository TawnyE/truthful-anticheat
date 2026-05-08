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


@CheckData(order = 'K', type = CheckType.AIM)
public final class AimK extends Check {

    private final CheckBuffer buffer = new CheckBuffer(20.0);
    private final Map<UUID, GridData> dataMap = new ConcurrentHashMap<>();

    private static final double MIN_DELTA = 0.35;
    private static final double EXPANDER = 16777216.0;
    private static final double MIN_VALID_STEP = 0.001;
    private static final double MAX_STEP_DRIFT = 0.002;

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isRotationUpdate()) return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isRotationExempt()) return;

        if (data.isInsideVehicle()) {
            dataMap.remove(player.getUniqueId());
            return;
        }

        if (data.isServerFrozen() || data.getTickFreezeGraceTicks() > 0) {
            buffer.decrease(player, 0.5);
            return;
        }

        if (data.getTicksTracked() - data.getLastHitTick() > 50) {
            dataMap.remove(player.getUniqueId());
            buffer.decrease(player, 0.15);
            return;
        }

        GridData grid = dataMap.computeIfAbsent(player.getUniqueId(), k -> new GridData());

        float deltaYaw = data.getRotationDeviation(false);
        float deltaPitch = data.getRotationDeviation(true);

        if (Math.abs(data.getPitch()) > 89.5f || (deltaPitch < MIN_DELTA && deltaYaw < MIN_DELTA)) {
            return;
        }

        long pCurrent = (long) (deltaPitch * EXPANDER);
        long pLast = (long) (grid.lastDeltaPitch * EXPANDER);
        double pStep = (double) MathHelper.getGcd(pCurrent, pLast) / EXPANDER;

        long yCurrent = (long) (deltaYaw * EXPANDER);
        long yLast = (long) (grid.lastDeltaYaw * EXPANDER);
        double yStep = (double) MathHelper.getGcd(yCurrent, yLast) / EXPANDER;

        StepSample pitchSample = buildSample(deltaPitch, pStep, grid.lastPitchStep);
        StepSample yawSample = buildSample(deltaYaw, yStep, grid.lastYawStep);

        StepSample active = pickStableSample(pitchSample, yawSample);

        if (active != null) {
            if (active.stable) {
                grid.stableTicks = Math.min(20, grid.stableTicks + 1);
            } else {
                grid.stableTicks = Math.max(0, grid.stableTicks - 1);
            }

            boolean isAliased = Math.abs(active.error - 0.5) < 0.02 || Math.abs(active.error - 0.33) < 0.02
                    || Math.abs(active.error - 0.25) < 0.02 || Math.abs(active.error - 0.67) < 0.02;

            if (active.error > 0.15 && !isAliased && grid.stableTicks >= 3) {
                if (active.delta < 0.08) return;

                if (buffer.increase(player, 1.0) > 12.0) {
                    flag(data, String.format("Input Grid Mismatch. Err: %.1f%%, Step: %.4f, Stable: %d",
                            active.error * 100.0, active.step, grid.stableTicks));
                    buffer.reset(player, 6.0);
                }
            } else {
                buffer.decrease(player, 0.2);
            }

            grid.lastPitchStep = pitchSample.valid ? pitchSample.step : grid.lastPitchStep;
            grid.lastYawStep = yawSample.valid ? yawSample.step : grid.lastYawStep;
        }

        grid.lastDeltaPitch = deltaPitch;
        grid.lastDeltaYaw = deltaYaw;
    }

    private StepSample pickStableSample(final StepSample pitch, final StepSample yaw) {
        if (pitch.valid && yaw.valid) {
            if (pitch.stable != yaw.stable) {
                return pitch.stable ? pitch : yaw;
            }
            return pitch.error <= yaw.error ? pitch : yaw;
        }
        if (pitch.valid) return pitch;
        if (yaw.valid) return yaw;
        return null;
    }

    private StepSample buildSample(final float delta, final double step, final double previousStep) {
        if (step <= MIN_VALID_STEP) {
            return StepSample.invalid();
        }

        double activeModulo = Math.abs(delta % step);
        double error = Math.min(activeModulo, Math.abs(step - activeModulo)) / step;
        boolean stable = previousStep > 0.0 && Math.abs(step - previousStep) <= MAX_STEP_DRIFT;

        return new StepSample(true, stable, delta, step, error);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    private static class GridData {
        float lastDeltaPitch = 0f;
        float lastDeltaYaw = 0f;
        double lastPitchStep = 0.0;
        double lastYawStep = 0.0;
        int stableTicks = 0;
    }

    private static final class StepSample {
        final boolean valid;
        final boolean stable;
        final float delta;
        final double step;
        final double error;

        private StepSample(final boolean valid, final boolean stable, final float delta, final double step, final double error) {
            this.valid = valid;
            this.stable = stable;
            this.delta = delta;
            this.step = step;
            this.error = error;
        }

        private static StepSample invalid() {
            return new StepSample(false, false, 0.0f, 0.0, 0.0);
        }
    }
}
