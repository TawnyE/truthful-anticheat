package ret.tawny.truthful.utils.math;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class Kinematics {
    private Kinematics() {}

    /**
     * Calculates the angle between the player's look direction and their actual movement direction on the XZ plane.
     * @param location The player's current location (containing their yaw).
     * @param deltaX The player's change in X.
     * @param deltaZ The player's change in Z.
     * @return The angle in degrees. Returns 0 if the player is not moving.
     */
    public static double getStrafeAngle(Location location, double deltaX, double deltaZ) {
        Vector moveDirection = new Vector(deltaX, 0, deltaZ);
        if (moveDirection.lengthSquared() == 0) {
            return 0.0;
        }

        Vector lookDirection = location.getDirection().setY(0);
        return Math.toDegrees(moveDirection.angle(lookDirection));
    }
}