package ret.tawny.truthful.config.api;

import org.bukkit.configuration.file.FileConfiguration;
import ret.tawny.truthful.TruthfulPlugin;

public final class Configuration {

    private final FileConfiguration config;

    public Configuration(final TruthfulPlugin plugin) {
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public boolean isCheckEnabled(String checkType, String checkOrder) {
        // This path matches the structure of the config.yml I provided.
        return this.config.getBoolean("checks." + checkType + "." + checkOrder + ".enabled", true);
    }

    public boolean isLagbacks() {
        return this.config.getBoolean("options.lagback", true);
    }
}