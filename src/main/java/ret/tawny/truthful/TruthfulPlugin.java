package ret.tawny.truthful;

import org.bukkit.plugin.java.JavaPlugin;
import ret.tawny.truthful.config.api.Configuration;

public final class TruthfulPlugin extends JavaPlugin {
    private Configuration configuration;

    @Override
    public void onEnable() {
        // This line is what requires config.yml to exist.
        this.configuration = new Configuration(this);
        Truthful.getInstance().start(this);
    }

    @Override
    public void onDisable() {
        Truthful.getInstance().shutdown();
    }

    public Configuration getConfiguration() {
        return configuration;
    }
}