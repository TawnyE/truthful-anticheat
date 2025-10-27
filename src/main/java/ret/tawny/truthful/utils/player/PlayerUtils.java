package ret.tawny.truthful.utils.player;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.data.PlayerData;

public final class PlayerUtils {
    private PlayerUtils() {}

    public static final double GRAVITY_ACCELERATION = 0.08D;
    public static final double AIR_DRAG = 0.9800000190734863D;
    public static final double JUMP_MOTION = 0.42F;

    public static PotionEffect getPotion(final PotionEffectType type, final PlayerData data) {
        for (final PotionEffect potionEffect : data.getPlayer().getActivePotionEffects()) {
            if (potionEffect.getType().equals(type)) {
                return potionEffect;
            }
        }
        return null;
    }

    /**
     * Calculates the horizontal (XZ plane) distance between a player and an entity.
     * @param playerData The data of the attacking player.
     * @param target The target entity.
     * @return The horizontal distance.
     */
    public static double getDistanceHz(final PlayerData playerData, final Entity target) {
        if (target == null) return 0;
        final Location targetLoc = target.getLocation();
        final double deltaX = playerData.getX() - targetLoc.getX();
        final double deltaZ = playerData.getZ() - targetLoc.getZ();
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    /**
     * Calculates the vertical (Y axis) distance between a player and an entity.
     * @param playerData The data of the attacking player.
     * @param target The target entity.
     * @return The absolute vertical distance.
     */
    public static double getDistanceVert(final PlayerData playerData, final Entity target) {
        if (target == null) return 0;
        final Location targetLoc = target.getLocation();
        return Math.abs(playerData.getY() - targetLoc.getY());
    }
}