package ret.tawny.truthful.utils.math;

import ret.tawny.truthful.utils.vec.Vector2f;

public final class MathHelper {

    public static final float RADIAN = (float) (Math.PI / 180.0F);

    public static float wrapAngleTo180_float(float value) {
        value = value % 360.0F;
        if (value >= 180.0F)
            value -= 360.0F;

        if (value < -180.0F)
            value += 360.0F;
        return value;
    }

    /**
     * Calculates the greatest common divisor (GCD) of two numbers using the Euclidean algorithm.
     * This is essential for heuristic aim analysis in ScaffoldG.
     * @param a The first number.
     * @param b The second number.
     * @return The GCD of a and b.
     */
    public static long gcd(long a, long b) {
        return (b == 0) ? a : gcd(b, a % b);
    }

    /**
     * @param base
     * @param exponent
     * @return Returns base to the power of exponent.
     */
    public static double pow(final double base, final double exponent) {
        double result = 1;
        if (exponent < 0)
            return result / pow(base, -exponent);

        for (int i = 0; i < exponent; ++i)
            result *= base;

        return result;
    }

    /**
     * @param base
     * @param exponent
     * @return Returns base to the power of exponent.
     */
    public static float pow(final float base, final float exponent) {
        float result = 1;
        if (exponent < 0)
            return result / pow(base, -exponent);

        for (int i = 0; i < exponent; ++i)
            result *= base;

        return result;
    }

    public static double[] distributeByWeight(final double base, final double... values) {
        final int size = values.length;
        final double[] weighted = new double[size];

        double sum = 0;
        for (final double d : values) sum += d;
        for (int i = 0; i < size; ++i)
            weighted[i] = base * (values[i] / (sum * 100.0F));
        return weighted;
    }

    /**
     * @param start
     * @param end
     * @param t     - progress
     */
    public static float lerp(final float start, final float end, final float t) {
        return start * (1 - t) + end * t;
    }

    /**
     * @param p0 - origin
     * @param p1 - inter
     * @param p2 - goal
     * @param t  - progress
     */
    public static Vector2f quadraticBezier(final Vector2f p0, final Vector2f p1, final Vector2f p2, final float t) {
        if (t < 0 || t > 1)
            throw new IllegalArgumentException("t must be between 0 and 1");
        final float x = pow(1 - t, 2) * p0.getX() + (1 - t) * 2 * t * p1.getX() + t * t * p2.getX();
        final float y = pow(1 - t, 2) * p0.getY() + (1 - t) * 2 * t * p1.getY() + t * t * p2.getY();
        return new Vector2f(x, y);
    }

    /**
     * @param p0 - origin
     * @param p1 - inter
     * @param p2 - goal
     * @param t  - progress
     */
    public static float quadraticBezierPoint(final float p0, final float p1, final float p2, final float t) {
        if (t < 0 || t > 1)
            throw new IllegalArgumentException("t must be between 0 and 1");
        return (float) (pow(1 - t, 2) * p0 + (1 - t) * 2 * t * p1 + t * t * p2);
    }

    /**
     * Allows you to create a bezier curve without knowing how many points there will be
     *
     * @param t          - progress
     * @param ctrlPoints
     */
    public static Vector2f dynamicBezier(final float t, final Vector2f... ctrlPoints) {
        final int size = ctrlPoints.length;
        if (size < 2)
            throw new IllegalArgumentException("A bezier curve requires at least 2 control points");
        if (t < 0 || t > 1)
            throw new IllegalArgumentException("t must be between 0 and 1");

        float x = 0;
        float y = 0;

        int n = size - 1;
        for (int i = 0; i <= n; i++) {
            final double b = binomialCoefficient(n, i) * Math.pow(1 - t, n - i) * Math.pow(t, i);
            x += b * ctrlPoints[i].getX();
            y += b * ctrlPoints[i].getY();
        }
        return new Vector2f(x, y);
    }

    public static double binomialCoefficient(final int n, final int k) {
        double c = 1;
        for (int i = 0; i < k; ++i)
            c = c * (n - i) / (i + 1);
        return c;
    }
}