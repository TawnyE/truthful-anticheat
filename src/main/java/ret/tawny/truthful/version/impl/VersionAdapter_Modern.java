package ret.tawny.truthful.version.impl;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ret.tawny.truthful.utils.world.PhysicsUtils;
import ret.tawny.truthful.version.IVersionAdapter;

public final class VersionAdapter_Modern implements IVersionAdapter {

    private final int version;

    public VersionAdapter_Modern(int version) {
        this.version = version;
    }

    @Override
    public double getBaseGroundSpeed(Player player) {
        AttributeInstance movementSpeed = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        double attributeSpeed = movementSpeed != null ? movementSpeed.getValue() : 0.1D;

        if (attributeSpeed <= 0.0D) {
            attributeSpeed = (player.getWalkSpeed() / 0.2F) * 0.1D;
        }

        double effectiveSpeed = (attributeSpeed / 0.1D) * 0.215D;

        if (player.isSprinting()) {
            effectiveSpeed *= 1.3D;
        }

        if (player.isSneaking()) {
            double sneakMultiplier = 0.3 * (1 + 0.15 * PhysicsUtils.getSwiftSneakLevel(player));
            effectiveSpeed *= sneakMultiplier;
        }

        return effectiveSpeed;
    }

    @Override
    public double getBaseAirSpeed(Player player) {
        if (player.isGliding()) return 3.0;
        return 0.36;
    }

    @Override
    public boolean isBlocking(Player player) {
        if (!player.isHandRaised()) return false;
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return (mainHand.getType() == Material.SHIELD || offHand.getType() == Material.SHIELD);
    }

    @Override
    public double getEntityInteractionRange(Player player) {
        try {
            Attribute attribute = Attribute.valueOf("PLAYER_ENTITY_INTERACTION_RANGE");
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) return instance.getValue();
        } catch (IllegalArgumentException | NullPointerException ignored) {}
        return player.getGameMode() == GameMode.CREATIVE ? 5.0 : 3.0;
    }

    @Override
    public double getBlockInteractionRange(Player player) {
        try {
            // 1.21+ Block Reach Attribute
            Attribute attribute = Attribute.valueOf("PLAYER_BLOCK_INTERACTION_RANGE");
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) return instance.getValue();
        } catch (IllegalArgumentException | NullPointerException ignored) {}

        // Default vanilla values
        return player.getGameMode() == GameMode.CREATIVE ? 5.0 : 4.5;
    }

    @Override
    public int getServerVersion() {
        return this.version;
    }
}