package ret.tawny.truthful.checks.impl.combat.lag;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

@CheckData(order = 'C', type = CheckType.LAG)
public final class LagC extends Check {

    private static final int MIN_SAMPLES = 20;
    private static final double MIN_CORRELATION = 0.85D;
    private static final int PING_BIN_COUNT = 4;
    private static final long MIN_PING_SPREAD_MS = 60L;

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, SampleBag> bags = new ConcurrentHashMap<>();

    private static class SampleBag {
        final long[] binPingSums = new long[PING_BIN_COUNT];
        final int[] binAttackCounts = new int[PING_BIN_COUNT];
        final int[] binViolationCounts = new int[PING_BIN_COUNT];
        int totalSamples = 0;
    }

    private static int pingBin(long pingMs) {
        if (pingMs < 50) return 0;
        if (pingMs < 100) return 1;
        if (pingMs < 200) return 2;
        return 3;
    }

    @Override
    public void handlePacketPlayerReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        Player player = (Player) event.getPlayer();
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isExempt()
                || player.getGameMode() == GameMode.CREATIVE
                || Truthful.getInstance().isBedrockPlayer(player)) return;

        long ping = data.getPing();
        if (ping <= 0) return;

        boolean recentViolation = data.getTicksTracked() - data.getLastFlagTick() <= 1;

        int bin = pingBin(ping);
        UUID uuid = player.getUniqueId();
        SampleBag bag = bags.computeIfAbsent(uuid, k -> new SampleBag());
        bag.binPingSums[bin] += ping;
        bag.binAttackCounts[bin]++;
        if (recentViolation) bag.binViolationCounts[bin]++;
        bag.totalSamples++;

        evaluate(player.getUniqueId(), data);
    }

    private void evaluate(UUID uuid, PlayerData data) {
        SampleBag bag = bags.get(uuid);
        if (bag == null || bag.totalSamples < MIN_SAMPLES) return;

        List<Integer> activeBins = new ArrayList<>();
        for (int i = 0; i < PING_BIN_COUNT; i++) {
            if (bag.binAttackCounts[i] >= 5) {
                activeBins.add(i);
            }
        }

        if (activeBins.size() < 2) return;

        int lowestBin = activeBins.get(0);
        int highestBin = activeBins.get(activeBins.size() - 1);

        long pingLo = bag.binPingSums[lowestBin] / bag.binAttackCounts[lowestBin];
        long pingHi = bag.binPingSums[highestBin] / bag.binAttackCounts[highestBin];

        if ((pingHi - pingLo) < MIN_PING_SPREAD_MS) {
            buffer.decrease(data.getPlayer(), 0.10);
            return;
        }

        double rateLo = (double) bag.binViolationCounts[lowestBin] / bag.binAttackCounts[lowestBin];
        double rateHi = (double) bag.binViolationCounts[highestBin] / bag.binAttackCounts[highestBin];

        // Lag abuse MUST show higher violation rate at higher ping (minimum 25% contrast)
        if (rateHi <= rateLo || (rateHi - rateLo) < 0.25D) {
            buffer.decrease(data.getPlayer(), 0.10);
            return;
        }

        double[] x = new double[activeBins.size()];
        double[] y = new double[activeBins.size()];

        for (int i = 0; i < activeBins.size(); i++) {
            int b = activeBins.get(i);
            x[i] = (double) bag.binPingSums[b] / bag.binAttackCounts[b];
            y[i] = (double) bag.binViolationCounts[b] / bag.binAttackCounts[b];
        }

        double r = spearman(x, y);

        if (r > MIN_CORRELATION) {
            if (buffer.increase(data.getPlayer(), 0.6 + r * 0.5) > 4.5) {
                flag(data, String.format("PingCorr r=%.3f (pingHi=%dms rateHi=%.2f | pingLo=%dms rateLo=%.2f | n=%d)",
                        r, pingHi, rateHi, pingLo, rateLo, bag.totalSamples));
                buffer.reset(data.getPlayer(), 3.0);
            }
        } else {
            buffer.decrease(data.getPlayer(), 0.06);
        }
    }

    private static double spearman(double[] x, double[] y) {
        int n = x.length;
        if (n <= 1) return 0.0;
        int[] rankX = ranks(x);
        int[] rankY = ranks(y);
        double sumD2 = 0.0D;
        for (int i = 0; i < n; i++) {
            double d = rankX[i] - rankY[i];
            sumD2 += d * d;
        }
        double denom = n * (n * n - 1);
        return denom == 0 ? 0.0 : 1.0 - (6.0 * sumD2) / denom;
    }

    private static int[] ranks(double[] arr) {
        int n = arr.length;
        int[] rank = new int[n];
        IntStream.range(0, n)
                .boxed()
                .sorted(Comparator.comparingDouble(i -> arr[i]))
                .forEach(sortedIdx -> rank[sortedIdx] = sortedIdx + 1);
        return rank;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        bags.remove(event.getPlayer().getUniqueId());
    }
}