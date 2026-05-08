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
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'L', type = CheckType.AIM)
public final class AimL extends Check {

    private final CheckBuffer buffer = new CheckBuffer(15.0);
    private final Map<UUID, BiomechData> dataMap = new ConcurrentHashMap<>();

    private static final float MIN_AXIS_LOCK_YAW = 4.0f;
    private static final float MAX_AXIS_LOCK_PITCH = 0.01f;
    private static final float MIN_LINEAR_DELTA = 1.0f;
    private static final float MAX_RATIO_DIFF = 0.0025f;

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

        if (data.getTicksTracked() - data.getLastHitTick() > 40) {
            dataMap.remove(player.getUniqueId());
            return;
        }

        BiomechData bData = dataMap.computeIfAbsent(player.getUniqueId(), k -> new BiomechData());

        float deltaYaw = data.getRotationDeviation(false);
        float deltaPitch = data.getRotationDeviation(true);

        if (deltaYaw > MIN_AXIS_LOCK_YAW && deltaPitch <= MAX_AXIS_LOCK_PITCH) {
            bData.lockedTicks++;
            if (bData.lockedTicks > 8) {
                if (buffer.increase(player, 1.2) > 10.0) {
                    flag(data, String.format("Axis Lock (Y-Axis). dYaw: %.2f dPitch: %.4f", deltaYaw, deltaPitch));
                }
            }
        } else {
            bData.lockedTicks = Math.max(0, bData.lockedTicks - 1);
        }

        if (deltaYaw > MIN_LINEAR_DELTA && deltaPitch > MIN_LINEAR_DELTA) {
            float ratio = deltaYaw / deltaPitch;
            if (bData.lastRatio != 0.0f) {
                float ratioDiff = Math.abs(ratio - bData.lastRatio);
                if (ratioDiff < MAX_RATIO_DIFF) {
                    bData.linearTicks++;
                    if (bData.linearTicks > 5) {
                        if (buffer.increase(player, 1.4) > 12.0) {
                            flag(data, String.format("Robotic Linearity. ratioΔ=%.5f", ratioDiff));
                            buffer.reset(player, 6.0);
                        }
                    }
                } else {
                    bData.linearTicks = Math.max(0, bData.linearTicks - 1);
                    buffer.decrease(player, 0.1);
                }
            }
            bData.lastRatio = ratio;
        }

        float yawSign = Math.signum(data.getYaw() - data.getLastYaw());
        if (bData.lastYawSign != 0 && yawSign != bData.lastYawSign) {
            if (bData.lastDeltaYaw > 15.0f && deltaYaw < 1.0f) {
                buffer.decrease(player, 2.0);
            }
        }

        bData.lastYawSign = yawSign;
        bData.lastDeltaYaw = deltaYaw;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    private static class BiomechData {
        int lockedTicks = 0;
        int linearTicks = 0;
        float lastRatio = 0f;
        float lastYawSign = 0f;
        float lastDeltaYaw = 0f;
    }
}
