package ret.tawny.truthful.utils;

import org.bukkit.util.Vector;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.math.MathHelper;
import ret.tawny.truthful.utils.math.VanillaMathHelper;

public final class VectorUtils {
    private VectorUtils() {}

    public static Vector getLookVector(final PlayerData playerData) {
        if (playerData.getTicksTracked() < 2) {
            return getVectorForRotation(playerData.getPitch(), playerData.getYaw());
        } else {
            float interpolatedPitch = playerData.getLastPitch() + (playerData.getPitch() - playerData.getLastPitch());
            float interpolatedYaw = playerData.getLastYaw() + (playerData.getYaw() - playerData.getLastYaw());
            return getVectorForRotation(interpolatedPitch, interpolatedYaw);
        }
    }

    public static Vector getVectorForRotation(final float pitch, final float yaw) {
        final float f = VanillaMathHelper.cos(-yaw * MathHelper.RADIAN - (float) Math.PI);
        final float f1 = VanillaMathHelper.sin(-yaw * MathHelper.RADIAN - (float) Math.PI);
        final float f2 = -VanillaMathHelper.cos(-pitch * MathHelper.RADIAN);
        final float f3 = VanillaMathHelper.sin(-pitch * MathHelper.RADIAN);
        return new Vector((f1 * f2), f3, (f * f2));
    }
}