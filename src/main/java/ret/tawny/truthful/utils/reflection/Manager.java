package ret.tawny.truthful.utils.reflection;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class Manager<K, V> {
    protected final Map<K, V> map;

    protected Manager() {
        this.map = new HashMap<>();
    }

    // New simple method to add instances manually
    protected final void register(K key, V value) {
        this.map.put(key, value);
    }

    public final Map<K, V> getMap() {
        return this.map;
    }

    public final Collection<V> getCollection() {
        return this.map.values();
    }

    public final V getValue(final K k) {
        return this.map.get(k);
    }
}