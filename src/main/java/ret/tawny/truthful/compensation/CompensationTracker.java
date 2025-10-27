package ret.tawny.truthful.compensation;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.utils.tick.ITickable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CompensationTracker implements ITickable {
    /**
     * World In Compensation
     */
    private final World world;
    /**
     * HashMap of entities in range of the entity at world tick
     */
    private final HashMap<Entity, CompensatedEntity> compensationMap;

    public CompensationTracker(final World world) {
        this.world = world;
        this.compensationMap = new HashMap<>();
    }

    /**
     *
     * @return Compensation Map of Entities
     */
    public HashMap<Entity, CompensatedEntity> getCompensationMap() {
        return compensationMap;
    }

    // TODO
    // Cap Max Ping to prevent players potentially crashing the server
    // 2000?
    // Further Elaboration at https://github.com/Spinyfish/Refraction-Concepts/tree/main/concepts/compensation/ping
    @Override
    public void tick() {
        if(this.compensationMap.keySet().size() <= 1)
            return;

        final int max = (int) Truthful.getInstance().getDataManager().getHighestPing().getPing();
        final int worldTick = (int) this.world.getFullTime();

        /**
         * Cull and update CompensatedEntities location for time
         */
        this.compensationMap.forEach((entity, compensatedEntity) -> compensatedEntity.tick(max, worldTick, entity.getLocation()));
    }

    /**
     *
     */
    public static final class CompensatedEntity {
        private final LinkedHashMap<Integer, Location> history;

        public CompensatedEntity() {
            this.history = new LinkedHashMap<>();
        }

        public void tick(final int cap, final int time, final Location location) {
            this.cull(cap, time);
            this.updateLocation(time, location);
        }

        /**
         *
         * Removes the earliest stored positions until the cap limit is met
         */
        private void cull(final int cap, final int time) {
            final int size = this.history.size() - cap;
            for(int i = 0; i < size; ++i)
                this.history.remove(time - (size + i));
        }

        private void updateLocation(final int tick, final Location location) {
            this.history.put(tick, location);
        }

        public Map<Integer, Location> getHistory() {
            return history;
        }

    }
}
