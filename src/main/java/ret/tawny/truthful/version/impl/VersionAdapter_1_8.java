package ret.tawny.truthful.version.impl;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.version.IVersionAdapter;

public final class VersionAdapter_1_8 implements IVersionAdapter {

    private static final double BASE_WALK_SPEED = 0.21585;
    private static final double BASE_SPRINT_SPEED = 0.2806;
    private static final double BASE_AIR_SPEED = 0.36;

    @Override
    public double getBaseGroundSpeed(Player player) {
        double max = player.isSprinting() ? BASE_SPRINT_SPEED : BASE_WALK_SPEED;
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = getPotionAmplifier(player, PotionEffectType.SPEED);
            max *= 1.0 + (0.2 * amplifier);
        }
        return max;
    }

    @Override
    public double getBaseAirSpeed(Player player) {
        return BASE_AIR_SPEED;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isBlocking(Player player) {
        return player.isBlocking();
    }

    @Override
    public int getServerVersion() {
        return 8;
    }

    private int getPotionAmplifier(Player player, PotionEffectType type) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(type)) {
                return effect.getAmplifier() + 1;
            }
        }
        return 0;
    }
}