package ret.tawny.truthful.checks.registry;

import org.bukkit.Bukkit;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.utils.reflection.Manager;

public final class CheckRegistry extends Manager<Class<? extends Check>, Check> {

    public CheckRegistry() {
        this.register(Truthful.getInstance().getPlugin(), Check.class, "ret.tawny.truthful.checks.impl", CheckData.class);
    }

    public void init() {
        // Register Bukkit events for all loaded checks.
        this.getCollection().forEach(check -> {
            if (check.isEnabled()) {
                Bukkit.getPluginManager().registerEvents(check, Truthful.getInstance().getPlugin());
            }
        });

        // Use the plugin's logger, as recommended by Paper.
        long enabledChecks = this.getCollection().stream().filter(Check::isEnabled).count();
        Truthful.getInstance().getPlugin().getLogger().info("Successfully loaded and registered " + enabledChecks + " checks.");
    }
}