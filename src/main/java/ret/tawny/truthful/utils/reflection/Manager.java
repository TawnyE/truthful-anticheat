package ret.tawny.truthful.utils.reflection;

import org.bukkit.plugin.Plugin;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import ret.tawny.truthful.utils.annot.Nullable;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class Manager<K, V> {
    protected final Map<K, V> map;

    protected Manager() {
        this.map = new HashMap<>();
    }

    /**
     * @param plugin The main plugin instance, used to get the correct classloader.
     * @param filter The type of class to look for.
     * @param pkge The package to look through.
     * @param annot The annotation that must be present in each class (can be null).
     */
    @SuppressWarnings("unchecked")
    protected final void register(final Plugin plugin, final Class<?> filter, final String pkge, @Nullable final Class<? extends Annotation> annot) {
        final ConfigurationBuilder configBuilder = new ConfigurationBuilder()
                .forPackage(pkge, plugin.getClass().getClassLoader()) // Provide the plugin's classloader
                .filterInputsBy(new FilterBuilder().includePackage(pkge));

        final Reflections reflect = new Reflections(configBuilder);

        for (final Class<?> clazz : reflect.getSubTypesOf(filter)) {
            if (annot != null && !clazz.isAnnotationPresent(annot)) {
                System.out.println(clazz + " is missing the @" + annot.getSimpleName());
                continue;
            }
            try {
                final V instance = (V) clazz.getDeclaredConstructor().newInstance();
                this.map.put((K) clazz, instance);
            } catch (final ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
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