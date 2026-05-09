package ret.tawny.truthful.utils.math;

public final class SensitivityUtil {

    private SensitivityUtil() {}

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
        if (!Float.isFinite(deltaPitch) || deltaPitch <= 0.0F) {
            return -1;
        }

        double f = Math.cbrt(deltaPitch / 1.2);
        double sensitivity = (f - 0.2) / 0.6;
        int percent = (int) Math.round(sensitivity * 200.0);

        if (percent < 0) return 0;
        if (percent > 200) return 200;
        return percent;
    }
}
