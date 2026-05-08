package ret.tawny.truthful.utils.elytra;

import org.bukkit.entity.Player;

/**
 * FireworkBoostTracker - Physics-based firework boost detection.
 * 
 * Instead of trusting item interactions, this class tracks actual velocity
 * patterns
 * to detect legitimate firework boosts. A real firework boost shows:
 * - 15%/tick multiplicative velocity increase
 * - Consistent boost duration matching firework gunpowder level
 * - Proper acceleration curve
 * 
 * Fake fireworks (spoofed interactions) will lack these physics signatures.
 */
public final class FireworkBoostTracker {

    // ==================== CONSTANTS ====================

    /** Minimum velocity increase per tick during firework boost (15%) */
    public static final double MIN_BOOST_MULTIPLIER = 1.15;

    /** Maximum reasonable boost multiplier (accounting for lag) */
    public static final double MAX_BOOST_MULTIPLIER = 1.25;

    /** Duration in ticks for firework level 1 (1 gunpowder) */
    public static final int DURATION_LEVEL_1 = 10; // ~0.5 seconds

    /** Duration in ticks for firework level 2 (2 gunpowder) */
    public static final int DURATION_LEVEL_2 = 20; // ~1 second

    /** Duration in ticks for firework level 3 (3 gunpowder) */
    public static final int DURATION_LEVEL_3 = 30; // ~1.5 seconds

    /** Grace ticks after boost ends */
    public static final int POST_BOOST_GRACE = 10;

    /** Minimum speed to consider boost detection */
    public static final double MIN_SPEED_FOR_DETECTION = 0.5;

    // ==================== TRACKING STATE ====================

    private boolean isBoostActive;
    private int boostStartTick;
    private int boostDuration;
    private int ticksSinceBoostEnd;

    // Velocity tracking for pattern detection
    private double lastSpeed;
    private double speedAtBoostStart;
    private int consecutiveAccelerationTicks;
    private double totalAcceleration;

    // Detection results
    private boolean lastBoostWasLegitimate;
    private String lastBoostFailReason;

    // False positive prevention
    private int legitimateBoostCount;
    private int suspiciousBoostCount;

    public FireworkBoostTracker() {
        reset();
    }

    /**
     * Resets all boost tracking state.
     */
    public void reset() {
        this.isBoostActive = false;
        this.boostStartTick = 0;
        this.boostDuration = 0;
        this.ticksSinceBoostEnd = 100; // Start high to prevent false triggers
        this.lastSpeed = 0;
        this.speedAtBoostStart = 0;
        this.consecutiveAccelerationTicks = 0;
        this.totalAcceleration = 0;
        this.lastBoostWasLegitimate = false;
        this.lastBoostFailReason = null;
        this.legitimateBoostCount = 0;
        this.suspiciousBoostCount = 0;
    }

    /**
     * Called when a firework interaction is detected (item use).
     * This does NOT immediately grant an exemption - we wait for physics
     * confirmation.
     * 
     * @param currentTick  The current tick count
     * @param currentSpeed The player's current speed
     * @param isGliding    Whether the player is currently gliding
     */
    public void onFireworkInteraction(int currentTick, double currentSpeed, boolean isGliding) {
        if (!isGliding) {
            this.lastBoostFailReason = "Not gliding";
            return;
        }

        if (currentSpeed < MIN_SPEED_FOR_DETECTION) {
            // Player is nearly stationary - likely ground launch
            // Still potentially legitimate but harder to verify
        }

        // Start tracking a potential boost
        this.isBoostActive = true;
        this.boostStartTick = currentTick;
        this.boostDuration = 0;
        this.speedAtBoostStart = currentSpeed;
        this.consecutiveAccelerationTicks = 0;
        this.totalAcceleration = 0;
        this.lastBoostFailReason = null;
    }

    /**
     * Updates the tracker each movement tick.
     * Call this ONCE per movement packet while player is gliding.
     * 
     * @param currentTick  The current tick count
     * @param currentSpeed The player's 3D velocity magnitude
     * @param deltaSpeed   The change in speed from last tick
     * @return true if currently in a legitimate boost period
     */
    public boolean update(int currentTick, double currentSpeed, double deltaSpeed) {
        // Track post-boost grace period
        if (!this.isBoostActive && this.ticksSinceBoostEnd < POST_BOOST_GRACE) {
            this.ticksSinceBoostEnd++;
        }

        // Update speed tracking
        double prevSpeed = this.lastSpeed;
        this.lastSpeed = currentSpeed;

        if (!this.isBoostActive) {
            return isInGracePeriod();
        }

        // Boost is active - validate physics
        this.boostDuration = currentTick - this.boostStartTick;

        // Check for acceleration
        if (deltaSpeed > 0.01) {
            this.consecutiveAccelerationTicks++;
            this.totalAcceleration += deltaSpeed;

            // Validate boost multiplier
            if (prevSpeed > 0.1) {
                double multiplier = currentSpeed / prevSpeed;
                if (multiplier >= MIN_BOOST_MULTIPLIER && multiplier <= MAX_BOOST_MULTIPLIER) {
                    // Good acceleration pattern
                } else if (multiplier > MAX_BOOST_MULTIPLIER) {
                    // Suspicious - too much acceleration
                    this.suspiciousBoostCount++;
                }
            }
        } else {
            // Not accelerating - boost may have ended
            if (this.boostDuration > 5) {
                endBoost(currentTick, true);
            }
        }

        // Check for boost timeout
        if (this.boostDuration > DURATION_LEVEL_3 + 10) {
            // Boost lasted too long - suspicious
            endBoost(currentTick, false);
            this.lastBoostFailReason = "Duration exceeded maximum";
            return false;
        }

        return this.isBoostActive || isInGracePeriod();
    }

    /**
     * Ends the current boost tracking and validates it.
     */
    private void endBoost(int currentTick, boolean wasLegitimate) {
        if (!this.isBoostActive)
            return;

        this.isBoostActive = false;
        this.ticksSinceBoostEnd = 0;

        // Validate the boost
        boolean legitimate = wasLegitimate;

        // Check minimum acceleration ticks
        if (this.consecutiveAccelerationTicks < 3) {
            legitimate = false;
            this.lastBoostFailReason = "Insufficient acceleration";
        }

        // Check total acceleration
        double expectedMinAccel = this.speedAtBoostStart * 0.1; // At least 10% speed gain
        if (this.totalAcceleration < expectedMinAccel) {
            legitimate = false;
            this.lastBoostFailReason = "Acceleration too low";
        }

        // Check duration matches a valid firework level
        if (this.boostDuration < DURATION_LEVEL_1 - 3) {
            // Too short
            legitimate = false;
            this.lastBoostFailReason = "Duration too short";
        }

        this.lastBoostWasLegitimate = legitimate;

        if (legitimate) {
            this.legitimateBoostCount++;
        } else {
            this.suspiciousBoostCount++;
        }
    }

    /**
     * Checks if the player is currently in a boost or grace period.
     * This is what checks should use to determine if boost exemption applies.
     */
    public boolean isInBoostOrGrace() {
        return this.isBoostActive || isInGracePeriod();
    }

    /**
     * Checks if we're in post-boost grace period.
     */
    public boolean isInGracePeriod() {
        return !this.isBoostActive &&
                this.ticksSinceBoostEnd < POST_BOOST_GRACE &&
                this.lastBoostWasLegitimate;
    }

    /**
     * Returns a suspicion score based on boost history.
     * Higher = more suspicious.
     */
    public double getSuspicionScore() {
        if (this.legitimateBoostCount + this.suspiciousBoostCount == 0) {
            return 0.0;
        }

        double ratio = (double) this.suspiciousBoostCount /
                (this.legitimateBoostCount + this.suspiciousBoostCount);

        return ratio * 10.0;
    }

    /**
     * Checks if a boost attempt looks like a fake firework.
     * Call this when you detect a firework interaction that fails physics
     * validation.
     */
    public boolean isFakeFirework() {
        if (this.isBoostActive) {
            return false; // Still tracking
        }

        // If we recently ended a boost that wasn't legitimate
        return this.ticksSinceBoostEnd < 5 && !this.lastBoostWasLegitimate;
    }

    // ==================== GETTERS ====================

    public boolean isBoostActive() {
        return isBoostActive;
    }

    public int getBoostDuration() {
        return boostDuration;
    }

    public int getTicksSinceBoostEnd() {
        return ticksSinceBoostEnd;
    }

    public boolean wasLastBoostLegitimate() {
        return lastBoostWasLegitimate;
    }

    public String getLastBoostFailReason() {
        return lastBoostFailReason;
    }

    public int getLegitimateBoostCount() {
        return legitimateBoostCount;
    }

    public int getSuspiciousBoostCount() {
        return suspiciousBoostCount;
    }

    public double getTotalAcceleration() {
        return totalAcceleration;
    }

    public int getConsecutiveAccelerationTicks() {
        return consecutiveAccelerationTicks;
    }
}
