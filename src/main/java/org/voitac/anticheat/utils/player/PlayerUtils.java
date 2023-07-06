package org.voitac.anticheat.utils.player;

import org.bukkit.entity.Entity;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

public final class PlayerUtils {
    private PlayerUtils() {}

    public static final double MAX_FLY_PURE_SPRINT = 1.12D;

    public static final double MAX_FLY_MOTION = 0.3750000149011612D;

    // All of this is reallllly retarded, and instead it should be dynamically calculated with a method
    public static final double BASE_PURE = 0.21585899972067188D;

    public static final double BASE_PURE_I = 0.2590307366885431D;

    public static final double BASE_PURE_II = 0.3021987868137503D;

    public static final double BASE_PURE_SPRINT = 0.2806146828476002D;

    public static final double BASE_PURE_SPRINT_I = 0.33673441794162884D;

    public static final double BASE_PURE_SPRINT_II = 0.39285848521971406D;

    public static final double BASE_PURE_SNEAK = 0.0D;

    public static final double GRAVITY = 0.9800000190734863D;

    public static final double OFFSET = 0.07840000152587834D; // Gravity drag on 0

    public static final double MINIMUM_DIVISOR = 0.096D;

    public static final double AIM_PRECISION = 1E-3;

    public static final float AIR_MOVEMENT_FACTOR = 0.02F;

    public static PotionEffect hasSpeed(final PlayerData data) {
        for(final PotionEffect potionEffect : data.getPlayer().getActivePotionEffects()) {
            if(potionEffect.getType() == PotionEffectType.SPEED) {
                return potionEffect;
            }
        }
        return null;
    }

    public static PotionEffect getPotion(final PotionEffectType type, final PlayerData data) {
        for(final PotionEffect potionEffect : data.getPlayer().getActivePotionEffects()) {
            if(potionEffect.getType().equals(type)) {
                return potionEffect;
            }
        }
        return null;
    }

    public static double getMaxSpeedInsideWalking(final PlayerData data) {
        final PotionEffect potionEffect = hasSpeed(data);
        if(potionEffect == null)
            return BASE_PURE;
        if (potionEffect.getAmplifier() == 1)
            return BASE_PURE_I;
        return BASE_PURE_II;
    }

    public static boolean isSprinting(final PlayerData data) {
        return data.getDeltaXZ() > getMaxSpeedInsideWalking(data);
    }

    public static boolean getLookingAt(final Player player, final Player target) {
        final Location eye = player.getEyeLocation();
        final Vector toEntity = target.getEyeLocation().toVector().subtract(eye.toVector());
        final double dot = toEntity.normalize().dot(eye.getDirection());

        return dot > 0.99D;
    }

    public static double getLookingAtDiff(final Player player, final Player target) {
        final Location eye = player.getEyeLocation();
        final Vector toEntity = target.getEyeLocation().toVector().subtract(eye.toVector());
        final double dot = toEntity.normalize().dot(eye.getDirection());

        final PlayerData data = AntiCheat.getInstance().getDataManager().getPlayerData(player),
        victimData = AntiCheat.getInstance().getDataManager().getPlayerData(target);

        return dot / Math.min(getDistance(data, target), 1);
    }

    public static float[] to(final PlayerData data, final PlayerData target) {
        final double x = data.getX(), z = data.getZ(),
                targetX = target.getX(), targetZ = target.getZ(),
                targetLastX = target.getLastX(), targetLastZ = target.getLastZ();

        final double intpX = targetX + (targetX - targetLastX) - x,
                intpZ = targetZ + (targetZ - targetLastZ) - z;

        final float yaw = (float) Math.toDegrees(Math.atan2(intpX, intpZ)) * -1;

        return new float[]{yaw, Math.abs(yaw - data.getYaw())};
    }

    public static List<Entity> targetsInRange(final PlayerData playerData, final double range) {
        return playerData.getPlayer().getNearbyEntities(range, range, range);
    }

    public static double getDistance(final PlayerData playerData, final Entity target) {
        if(target == null)
            return 0;
        final Location targetLoc = target.getLocation();
        final double deltaX = playerData.getX() - targetLoc.getX();
        double deltaY = playerData.getY() - targetLoc.getY() + 1.62D;
        if(target instanceof Player)
            deltaY -= 1.62D;
        final double deltaZ = playerData.getZ() - targetLoc.getZ();
        return (double) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    public static double getDistanceHz(final PlayerData playerData, final Entity target) {
        if(target == null)
            return 0;
        final Location targetLoc = target.getLocation();
        final double deltaX = playerData.getX() - targetLoc.getX();
        final double deltaZ = playerData.getZ() - targetLoc.getZ();
        return (double) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    public static double getDistanceVert(final PlayerData playerData, final Entity target) {
        if(target == null)
            return 0;
        final Location targetLoc = target.getLocation();
        double deltaY = playerData.getY() - targetLoc.getY() + 1.62D;
        if(target instanceof Player)
            deltaY -= 1.62D;
        return deltaY;
    }

    public static float getDirection(final PlayerData playerData) {
        return (float) Math.toRadians(playerData.getYaw());
    }

    public static double[] motionForward(final PlayerData playerData) {
        final float dir = getDirection(playerData);
        final double x = -StrictMath.sin(dir);
        final double z = StrictMath.cos(dir);

        return new double[] {x, z};
    }
}
