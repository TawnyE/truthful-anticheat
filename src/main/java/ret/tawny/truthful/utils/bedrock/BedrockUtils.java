package ret.tawny.truthful.utils.bedrock;

import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;

import java.util.UUID;

/**
 * BedrockUtils - Dedicated utilities for Geyser/Floodgate clients.
 *
 * Bedrock clients behave differently than Java clients due to:
 * 1. Touch controls (different rotation packets).
 * 2. Input lag/Jitter (Geyser translation).
 * 3. Native physics differences (Reach, Knockback).
 *
 * This class defines the "Hard Limits" for Bedrock players to allow
 * lenience while stopping blatant cheating.
 */
public final class BedrockUtils {

    private BedrockUtils() {}

    // === BEDROCK PHYSICS CONSTANTS ===

    // Bedrock reach is naturally higher (up to 3.0-3.5 on mobile).
    // We set a hard cap at 4.5 to allow for translation jitter.
    public static final double MAX_REACH = 4.5;

    // Bedrock friction is often miscalculated by Geyser during lag.
    // Instead of 0.91 (Air Drag), we use broader limits.
    public static final double MAX_SPEED_GROUND = 0.65; // ~2x Walking speed
    public static final double MAX_SPEED_AIR = 0.75;    // Allows for inconsistent air-strafing

    // Gravity calculation tolerance.
    // Java is precise (0.08). Bedrock packets often arrive in bursts, making Y-deltas fluctuate.
    public static final double GRAVITY_TOLERANCE = 0.15;

    /**
     * Determines if a player is using a Bedrock client.
     * 1. Checks for custom prefix defined in config.yml.
     * 2. Checks for Floodgate UUID version (0).
     */
    public static boolean isBedrock(Player player) {
        try {
            // 1. Configurable Username Prefix
            String prefix = Truthful.getInstance().getConfiguration().getBedrockPrefix();
            if (prefix != null && !prefix.isEmpty() && player.getName().startsWith(prefix)) {
                return true;
            }

            // 2. Floodgate UUID Check (Fallback)
            // Floodgate UUIDs usually have version 0 (Reserved for offline/foreign clients).
            UUID uuid = player.getUniqueId();
            if (uuid.version() == 0) {
                return true;
            }

        } catch (Exception ignored) {
            // Fail safe -> Treat as Java to ensure standard checks run
        }
        return false;
    }

    /**
     * Returns true if the player should skip Standard Java Checks.
     */
    public static boolean shouldSkipJavaChecks(Player player) {
        return isBedrock(player);
    }
}