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
import ret.tawny.truthful.utils.math.Statistics;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'X', type = CheckType.AIM, displayName = "Aim(X)")
public final class AimX extends Check {

    private static final int SAMPLE_WINDOW = 20;
    private static final double CONFIDENCE_MAX = 8.0D;
    private static final double HUMAN_ENTROPY_FLOOR = 3.10D; // Human movement is >= 3.10
    private final CheckBuffer buffer = new CheckBuffer(10.0);

    private static final class AimXState {
        final List<Float> yawSamples = new ArrayList<>();
        final List<Float> pitchSamples = new ArrayList<>();
        double confidence = 0.0D;
        int ticksInCombat = 0;
    }

    private final Map<UUID, AimXState> states = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isRotationUpdate()) return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isRotationExempt() || data.isInsideVehicle()) return;

        AimXState st = states.computeIfAbsent(player.getUniqueId(), k -> new AimXState());

        float dYaw = Math.abs(data.getDeltaYaw());
        float dPitch = Math.abs(data.getDeltaPitch());

        if (dYaw > 0.01f || dPitch > 0.01f) {
            st.yawSamples.add(dYaw);
            st.pitchSamples.add(dPitch);
            if (st.yawSamples.size() > SAMPLE_WINDOW) {
                st.yawSamples.remove(0);
                st.pitchSamples.remove(0);
            }
        } else {
            return;
        }

        if (st.yawSamples.size() < SAMPLE_WINDOW) return;

        st.ticksInCombat++;
        if (st.ticksInCombat % 5 != 0) return;

        double currentEntropyYaw = Statistics.getShannonEntropy(st.yawSamples);
        double currentVarYaw = Statistics.getVariance(st.yawSamples);

        // Human Protection Ceiling - Normal human mouse movement (3.20 - 4.32) is instantly passed
        if (currentEntropyYaw >= HUMAN_ENTROPY_FLOOR) {
            st.confidence = Math.max(0.0D, st.confidence - 0.5D);
            buffer.decrease(player, 0.2);
            return;
        }

        // Generic Low-Entropy Synthetic Aim Detection (no profile files needed)
        boolean matched = currentEntropyYaw < 2.10D && currentVarYaw < 1.8D;

        if (matched) {
            st.confidence = Math.min(CONFIDENCE_MAX, st.confidence + 1.0D);

            debugVerbose(String.format("Synthetic Aim | Conf: %.1f/%.1f | Entropy: %.2f | Var: %.2f",
                    st.confidence, CONFIDENCE_MAX, currentEntropyYaw, currentVarYaw));

            if (st.confidence >= CONFIDENCE_MAX) {
                if (buffer.increase(player, 2.0) > 3.0) {
                    flag(data, String.format("Synthetic Aim (Confidence: %.1f/8, Entropy: %.2f, Var: %.2f)",
                            st.confidence, currentEntropyYaw, currentVarYaw));
                    buffer.reset(player, 1.0);
                    st.confidence = 4.0D;
                }
            }
        } else {
            st.confidence = Math.max(0.0D, st.confidence - 0.25D);
            buffer.decrease(player, 0.1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        states.remove(event.getPlayer().getUniqueId());
    }
}