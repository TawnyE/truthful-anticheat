package ret.tawny.truthful.sync;

import org.bukkit.Location;
import org.bukkit.World;
import ret.tawny.truthful.utils.tick.ITickable;
import ret.tawny.truthful.utils.world.WorldUtils;
import java.util.ArrayList;

public final class TeleportQueue extends ArrayList<TeleportQueue.Teleport> {
    public static final class Teleport implements ITickable {
        private final Location location;
        private int initialDelay;

        public Teleport(final Location location, final int initialDelay) {
            this.location = location;
            this.initialDelay = initialDelay;
        }

        public Location getLocation() {
            return this.location;
        }

        @Override
        public void tick() {
            --this.initialDelay;
        }

        public boolean hasReceived() {
            return this.initialDelay <= 0;
        }
    }

    public static class TeleportBuffer {
        private static final int TIMEOUT = 2;
        private final int releaseTick;
        private final World world;

        public TeleportBuffer(final World world) {
            this.world = world;
            this.releaseTick = WorldUtils.getWorldTicks(this.world);
        }

        public boolean tick() {
            return WorldUtils.getWorldTicks(this.world) - this.releaseTick > TIMEOUT;
        }
    }
}