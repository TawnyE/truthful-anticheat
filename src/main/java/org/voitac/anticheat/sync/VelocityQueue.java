package org.voitac.anticheat.sync;

import org.bukkit.util.Vector;
import org.voitac.anticheat.utils.tick.ITickable;

import java.util.concurrent.ConcurrentLinkedDeque;

public final class VelocityQueue extends ConcurrentLinkedDeque<VelocityQueue.Velocity> {
    public static final class Velocity implements ITickable {
        /**
         * Velocity Vector
         */
        private final Vector velocityVec;
        /**
         * Calculated from the players ping on velocity publish
         */
        private int initialDelay;

        public Velocity(final Vector velocityVec, final int initialDelay) {
            this.velocityVec = velocityVec;
            this.initialDelay = initialDelay;
        }

        /**
         *
         * @return Velocity Vector
         */
        public Vector getVelocityVec() {
            return velocityVec;
        }

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
}
