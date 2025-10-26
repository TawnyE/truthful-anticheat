package org.voitac.anticheat.utils.reflection;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.voitac.anticheat.utils.annot.Nullable;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

public abstract class Manager<K, V> {
    protected final Map<K, V> map;

    protected Manager() {
        this.map = new HashMap<>();
    }

    /**
     * @param filter, the type of class to look for
     * @param pkge, the package to look through
     * @param annot, the annot that must be present in each class, if you do not want a specified annotation, this can be null
     */
    protected final void register(final Class<?> filter, final String pkge, @Nullable final Class<? extends Annotation> annot) {
        final Reflections reflect = new Reflections(new ConfigurationBuilder()
                .forPackage(pkge).filterInputsBy(
                        new FilterBuilder().includePackage(pkge)));

        for(final Class<?> clazz : reflect.getSubTypesOf(filter)) {
            if(annot != null) {
                if(!clazz.isAnnotationPresent(annot)) {
                    System.out.println(clazz + " is missing the @" + annot.getSimpleName());
                    continue;
                }
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
