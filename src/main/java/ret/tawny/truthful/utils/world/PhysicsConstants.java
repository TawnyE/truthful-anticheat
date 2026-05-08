package ret.tawny.truthful.utils.world;

public final class PhysicsConstants {

    private PhysicsConstants() {
    }

    public static final double GRAVITY = 0.08D;
    public static final double SLOW_FALLING_GRAVITY = 0.01D;
    public static final double AIR_DRAG_Y = 0.9800000190734863D;
    public static final double TERMINAL_VELOCITY = -3.92D;

    public static final double AIR_DRAG_XZ = 0.9100000262260437D;
    public static final float DEFAULT_SLIPPERINESS = 0.6F;
    public static final double MIN_MOTION = 0.003D;

    public static final double JUMP_IMPULSE = 0.42D;
    public static final double JUMP_BOOST_MODIFIER = 0.1D;
}
