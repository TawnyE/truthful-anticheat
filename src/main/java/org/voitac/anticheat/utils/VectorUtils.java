package org.voitac.anticheat.utils;

import org.bukkit.util.Vector;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.utils.math.MathHelper;
import org.voitac.anticheat.utils.math.VanillaMathHelper;

public final class VectorUtils {
    private VectorUtils() {}


    /**
     *
     * @param vector
     * @param length - length of the vector,
     * @return
     */
    public static Vector calculateVectorEnd(final Vector vector, double length) {
        length += 1;
        final double x = vector.getX() * length + 1;
        final double y = vector.getY() * length + 1;
        final double z = vector.getZ() * length + 1;

        return new Vector(x, y, z);
    }

    /**
     *
     * @return Normalized Vector
     */
    public static Vector normalizeVector(final Vector vector) {
        final double magnitude = magnitude(vector);
        return new Vector(vector.getX() / magnitude, vector.getY() / magnitude, vector.getZ() / magnitude);
    }

    /**
     *
     * @return The Vector of a magnitude
     */
    public static double magnitude(final Vector vector) {
        return Math.sqrt((vector.getX() * vector.getX()) + (vector.getY() * vector.getY()) + (vector.getZ() * vector.getZ()));
    }

    /**
     *
     * @return Interpolated Look Vector
     */
    public static Vector getLookVector(final PlayerData playerData) {
        if (playerData.getTicksTracked() < 2) {
            return getVectorForRotation(playerData.getPitch(), playerData.getYaw());
        }
        else
            return getVectorForRotation(playerData.getLastPitch() + (playerData.getPitch() - playerData.getLastPitch()),
                    playerData.getLastYaw() + (playerData.getYaw() - playerData.getLastYaw()));

    }

    /**
     * @return  Creates a Vec3 using the pitch and yaw of the entities' rotation.
     */
    public static Vector getVectorForRotation(final float pitch, final float yaw) {
        final float f = VanillaMathHelper.cos(-yaw * MathHelper.RADIAN - (float)Math.PI);
        final float f1 = VanillaMathHelper.sin(-yaw * MathHelper.RADIAN - (float)Math.PI);
        final float f2 = -VanillaMathHelper.cos(-pitch * MathHelper.RADIAN);
        final float f3 = VanillaMathHelper.sin(-pitch * MathHelper.RADIAN);
        return new Vector((double)(f1 * f2), (double)f3, (double)(f * f2));
    }
}
