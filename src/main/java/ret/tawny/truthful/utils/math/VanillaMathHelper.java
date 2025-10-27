package ret.tawny.truthful.utils.math;

public final class VanillaMathHelper {
    private VanillaMathHelper() {}

    /**
     * A table of sin values computed from 0 (inclusive) to 2*pi (exclusive), with steps of 2*PI / 65536.
     */
    private static final float[] SIN_TABLE = new float[65536];
    /**
     * sin looked up in a table
     */
    public static float sin(float p_76126_0_)
    {
        return SIN_TABLE[(int)(p_76126_0_ * 10430.378F) & 65535];
    }

    /**
     * cos looked up in the sin table with the appropriate offset
     */
    public static float cos(float value)
    {
        return SIN_TABLE[(int)(value * 10430.378F + 16384.0F) & 65535];
    }

    public static float sqrt_float(float value)
    {
        return (float)Math.sqrt((double)value);
    }

    static
    {
        for (int i = 0; i < 65536; ++i)
        {
            SIN_TABLE[i] = (float)Math.sin((double)i * Math.PI * 2.0D / 65536.0D);
        }
    }
}
