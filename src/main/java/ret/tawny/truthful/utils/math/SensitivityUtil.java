package ret.tawny.truthful.utils.math;

import java.util.List;

public final class SensitivityUtil {

    private SensitivityUtil() {}

    /**
     * Standard Minecraft GCD constant (2^24).
     */
    private static final double GCD_MULTIPLIER = Math.pow(2, 24);

    /**
     * Calculates the Greatest Common Divisor (GCD) of two floating point numbers.
     * Used to find the minimum rotation 'step' the client can make.
     */
    public static double getGcd(double a, double b) {
        if (a < b) return getGcd(b, a);
        if (Math.abs(b) < 0.001) return a;
        return getGcd(b, a - Math.floor(a / b) * b);
    }

    /**
     * Converts a raw GCD 'step' into a Minecraft Sensitivity percentage (0-200%).
     * Formula: f = sens * 0.6 + 0.2; step = f^3 * 8 * 0.15
     */
    public static int getSensitivityFromPitchGCD(float deltaPitch) {
        // We need the GCD of the current pitch delta and previous ones.
        // This is usually handled in the Profile/PlayerData over time.
        // This method assumes 'deltaPitch' IS the calculated GCD step.

        // 1. Normalize the GCD to the 'f' value
        // delta = f^3 * 1.2 (approximate constant for 8 * 0.15)
        // f = cbrt(delta / 1.2)

        double f = Math.cbrt(deltaPitch / 1.2);

        // 2. Inverse the linear formula: f = sens * 0.6 + 0.2
        // sens = (f - 0.2) / 0.6
        double sensitivity = (f - 0.2) / 0.6;

        // 3. Convert to percent (Minecraft displays e.g. "100%")
        return (int) (sensitivity * 200.0);
    }
}