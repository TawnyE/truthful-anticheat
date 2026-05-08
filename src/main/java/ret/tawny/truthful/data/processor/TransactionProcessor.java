package ret.tawny.truthful.data.processor;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.util.Threading;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

// CHANGE: DECOMP-1 - Extracted TransactionProcessor from PlayerData god class
public final class TransactionProcessor {

    private final PlayerData data;
    private final Configuration config;

    private final Map<Short, TransactionEntry> transactionsSent = new ConcurrentHashMap<>();
    private final Queue<Short> transactionOrder = new ConcurrentLinkedQueue<>();
    private long playerClockAtLeast = System.nanoTime();
    private long timerBalanceRealTime = System.nanoTime();
    private final long clockDrift = 150_000_000L;

    private short transactionId = 0;
    private long transactionPing;
    private long lastTransactionTime;

    private long highPingStartTime = 0;

    public TransactionProcessor(PlayerData data, Configuration config) {
        this.data = data;
        this.config = config;
    }

    public short getNextTransactionId() {
        if (transactionId++ > 32000)
            transactionId = 0;
        return transactionId;
    }

    public void sendTransaction() {
        this.lastTransactionTime = System.currentTimeMillis();
    }

    public void handleTransaction(short id) {
        TransactionEntry entry = transactionsSent.remove(id);

        if (entry != null) {
            this.playerClockAtLeast = Math.max(this.playerClockAtLeast, entry.timestamp);
            long nowNanos = System.nanoTime();
            long diffNanos = nowNanos - entry.timestamp;
            this.transactionPing = (this.transactionPing * 3 + (diffNanos / 1_000_000L)) / 4;
            this.timerBalanceRealTime = Math.max(this.timerBalanceRealTime, this.playerClockAtLeast - clockDrift);
            checkPingKick(this.transactionPing);
        }

        if (data.getVelocities() != null) {
            data.getVelocities().confirm(id);
        }

        if (data.getSetbackHandler() != null && data.getSetbackHandler().onTransaction(id)) {
            if (data.getProcessor() != null) {
                data.getProcessor().reset();
            }
            this.timerBalanceRealTime = System.nanoTime();
            Player player = data.getPlayer();
            if (player != null && player.isOnline()) {
                org.bukkit.Location loc = player.getLocation();
                if (data.getPositionTracker() != null) {
                    data.getPositionTracker().reset(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
                }
            }
        }
    }

    public void recordTransactionSent(short id) {
        this.transactionsSent.put(id, new TransactionEntry(id, System.nanoTime()));
        this.transactionOrder.add(id);
        while (transactionOrder.size() > 100) {
            Short oldestId = transactionOrder.poll();
            if (oldestId != null) {
                this.transactionsSent.remove(oldestId);
            }
        }
    }

    private void checkPingKick(long currentPing) {
        if (!config.isPingKickEnabled()) {
            this.highPingStartTime = 0;
            return;
        }

        if (currentPing > config.getPingKickThreshold()) {
            if (this.highPingStartTime == 0) {
                this.highPingStartTime = System.currentTimeMillis();
            } else {
                long duration = System.currentTimeMillis() - this.highPingStartTime;
                long maxDuration = config.getPingKickDuration() * 1000L;

                if (duration > maxDuration) {
                    Threading.runOnMain(() -> {
                        Player player = data.getPlayer();
                        if (player != null && player.isOnline()) {
                            player.kickPlayer(config.getPingKickMessage());
                        }
                    });
                    this.highPingStartTime = 0;
                }
            }
        } else {
            this.highPingStartTime = 0;
        }
    }

    public long getPing() {
        return transactionPing;
    }

    public void setPing(long ping) {
        this.transactionPing = ping;
    }

    public long getLastTransactionTime() {
        return lastTransactionTime;
    }

    public double getTransactionTimerBalance() {
        return (double) (timerBalanceRealTime - System.nanoTime()) / 1_000_000.0;
    }

    public long getPlayerClockAtLeast() {
        return playerClockAtLeast;
    }

    public long getTimerBalanceRealTime() {
        return timerBalanceRealTime;
    }

    public void addTimerBalance(long nanos) {
        this.timerBalanceRealTime += nanos;
    }

    public void resetTimerBalance() {
        this.timerBalanceRealTime = System.nanoTime();
    }

    public void clear() {
        transactionsSent.clear();
        transactionOrder.clear();
    }

    public Map<Short, TransactionEntry> getTransactionsSent() {
        return transactionsSent;
    }

    public Queue<Short> getTransactionOrder() {
        return transactionOrder;
    }

    public static class TransactionEntry {
        public final short id;
        public final long timestamp;

        public TransactionEntry(short id, long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }
    }
}
