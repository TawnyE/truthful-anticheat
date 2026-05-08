package ret.tawny.truthful.prediction;

/**
 * EWMAPerformanceTracker - Exponentially Weighted Moving Average for prediction timing.
 *
 * Inspired by GrimAC's predictionNanos/longPredictionNanos tracking.
 * Tracks the time it takes to run prediction, which helps detect:
 * - Timer abuse (client sending packets faster than expected)
 * - Negative timer (client slowing down game speed)
 * - Network manipulation
 *
 * Uses EWMA (alpha = 0.002 for long-term, 0.02 for short-term)
 * for smooth, responsive tracking.
 */
public final class EWMAPerformanceTracker {

    private static final double SHORT_ALPHA = 0.02;
    private static final double LONG_ALPHA = 0.002;

    private double shortTermAvg = 0;
    private double longTermAvg = 0;
    private double packetIntervalAvg = 0;
    private int packetCount = 0;
    private long lastPacketTime = 0;
    private double tickBalance = 0;
    private double maxTickDebt = 0;

    public EWMAPerformanceTracker() {}

    public void reset() {
        shortTermAvg = 0;
        longTermAvg = 0;
        packetIntervalAvg = 0;
        packetCount = 0;
        lastPacketTime = 0;
        tickBalance = 0;
        maxTickDebt = 0;
    }

    /**
     * Record a packet arrival time and update EWMA averages.
     */
    public void recordPacket(long currentTimeNanos) {
        if (lastPacketTime > 0) {
            long interval = currentTimeNanos - lastPacketTime;
            double alpha = packetCount < 50 ? SHORT_ALPHA * 5 : SHORT_ALPHA;

            if (shortTermAvg == 0) {
                shortTermAvg = interval;
                longTermAvg = interval;
                packetIntervalAvg = interval;
            } else {
                shortTermAvg = shortTermAvg * (1 - alpha) + interval * alpha;
                longTermAvg = longTermAvg * (1 - LONG_ALPHA) + interval * LONG_ALPHA;
                packetIntervalAvg = packetIntervalAvg * 0.95 + interval * 0.05;
            }
        }

        packetCount++;
        lastPacketTime = currentTimeNanos;
    }

    /**
     * Update tick balance based on expected vs actual packet rate.
     * Expected interval is 50ms (20 TPS = 50ms per tick).
     */
    public void updateTickBalance(double expectedIntervalNanos) {
        if (packetIntervalAvg > 0) {
            double ratio = expectedIntervalNanos / packetIntervalAvg;
            tickBalance += (ratio - 1.0);
            if (tickBalance > maxTickDebt) {
                maxTickDebt = tickBalance;
            }
            // Decay balance toward zero
            tickBalance *= 0.98;
        }
    }

    /**
     * Check if timer abuse is detected.
     * Returns a severity value: 0 = normal, >0 = abuse detected.
     */
    public double getTimerSeverity() {
        if (longTermAvg <= 0) return 0;

        // Expected packet interval at 20 TPS is ~50ms
        // If packets arrive significantly faster, timer is sped up
        double expectedInterval = 50_000_000; // 50ms in nanos
        double speedRatio = expectedInterval / longTermAvg;

        if (speedRatio > 1.05) {
            return speedRatio - 1.0;
        }
        return 0;
    }

    /**
     * Check for negative timer (slowing down).
     */
    public double getNegativeTimerSeverity() {
        if (longTermAvg <= 0) return 0;

        double expectedInterval = 50_000_000;
        double speedRatio = expectedInterval / longTermAvg;

        if (speedRatio < 0.95) {
            return 1.0 - speedRatio;
        }
        return 0;
    }

    public double getShortTermAvg() { return shortTermAvg; }
    public double getLongTermAvg() { return longTermAvg; }
    public double getPacketIntervalAvg() { return packetIntervalAvg; }
    public double getTickBalance() { return tickBalance; }
    public double getMaxTickDebt() { return maxTickDebt; }
    public int getPacketCount() { return packetCount; }
}
