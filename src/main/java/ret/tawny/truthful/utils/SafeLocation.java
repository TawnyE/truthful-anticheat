package ret.tawny.truthful.utils;

import org.bukkit.Location;
import org.bukkit.World;

public final class SafeLocation extends Location {
    public SafeLocation(final World world, final double x, final double y, final double z) {
        super(world, x, y, z);
    }
    public SafeLocation(final Location clone) {
        super(clone.getWorld(), clone.getX(), clone.getY(), clone.getZ());
    }

    public SafeLocation add(final Location vector) {
        return new SafeLocation(this.getWorld(), this.getX() + vector.getX(), this.getY() + vector.getY(), this.getZ() + vector.getZ());
    }
    public SafeLocation add(final SafeLocation vector) {
        return new SafeLocation(this.getWorld(), this.getX() + vector.getX(), this.getY() + vector.getY(), this.getZ() + vector.getZ());
    }
}
