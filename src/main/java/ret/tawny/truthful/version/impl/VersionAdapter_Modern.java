package ret.tawny.truthful.version.impl;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ret.tawny.truthful.version.IVersionAdapter;

public final class VersionAdapter_Modern implements IVersionAdapter {

    private final int version;

    public VersionAdapter_Modern(int version) {
        this.version = version;
    }

    @Override
    public double getBaseGroundSpeed(Player player) {
        float attributeSpeed = player.getWalkSpeed();
        double effectiveSpeed = (attributeSpeed / 0.2F) * 0.215;
        if (player.isSprinting()) {
            effectiveSpeed *= 1.3;
        }
        return effectiveSpeed;
    }

    @Override
    public double getBaseAirSpeed(Player player) {
        return 0.38;
    }

    @Override
    public boolean isBlocking(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return player.isBlocking() && (mainHand.getType() == Material.SHIELD || offHand.getType() == Material.SHIELD);
    }

    @Override
    public int getServerVersion() {
        return this.version;
    }
}