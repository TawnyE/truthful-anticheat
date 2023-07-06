package org.voitac.anticheat.sync;

import org.bukkit.Location;
import org.bukkit.World;
import org.voitac.anticheat.utils.tick.ITickable;
import org.voitac.anticheat.utils.world.WorldUtils;

import java.util.ArrayList;

public final class TeleportQueue extends ArrayList<TeleportQueue.Teleport> {
    public static final class Teleport implements ITickable {
        /**
         * Velocity Vector
         */
        private final Location location;
        /**
         * Calculated from the players ping on velocity publish
         */
        private int initialDelay;

        public Teleport(final Location location, final int initialDelay) {
            this.location = location;
            this.initialDelay = initialDelay;
        }

        public Location getLocation() {
            return this.location;
        }

        /**
         *
         * @return Velocity Vector
         */


        @Override
        public void tick() {
            --this.initialDelay;
        }

        /**
         * @return Returns true if the velocity should have effected the client this tick
         */
        public boolean hasReceived() {
            return this.initialDelay <= 0;
        }
    }
    public static class TeleportBuffer {
        /**
         * Buffer time out before it should be destroyed
         */
        private static final int TIMEOUT = 2;

        private final int releaseTick;

        private final World world;

        public TeleportBuffer(final World world) {
            this.world = world;
            this.releaseTick = WorldUtils.getWorldTicks(this.world);
        }

        /**
         *
         * @return Returns true if the time this buffer has existed is greater than the timeout
         */
        public boolean tick() {
            return WorldUtils.getWorldTicks(this.world) - this.releaseTick > TIMEOUT;
        }
    }
}
