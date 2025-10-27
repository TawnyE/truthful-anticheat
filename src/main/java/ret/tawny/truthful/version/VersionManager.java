package ret.tawny.truthful.version;

import org.bukkit.Bukkit;
import ret.tawny.truthful.version.impl.VersionAdapter_1_8;
import ret.tawny.truthful.version.impl.VersionAdapter_Modern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionManager {
    private IVersionAdapter adapter;

    public void load() {
        try {
            Pattern pattern = Pattern.compile("1\\.(\\d{1,2})");
            Matcher matcher = pattern.matcher(Bukkit.getServer().getBukkitVersion());

            if (matcher.find()) {
                int minorVersion = Integer.parseInt(matcher.group(1));

                if (minorVersion <= 8) {
                    this.adapter = new VersionAdapter_1_8();
                    Bukkit.getLogger().info("[Truthful] Loaded Version Adapter for Minecraft 1.8 compatibility.");
                } else {
                    this.adapter = new VersionAdapter_Modern(minorVersion);
                    Bukkit.getLogger().info("[Truthful] Loaded Version Adapter for modern Minecraft (1.9+).");
                }
            } else {
                throw new IllegalStateException("Could not determine server version from Bukkit version string: " + Bukkit.getServer().getBukkitVersion());
            }

        } catch (Exception e) {
            Bukkit.getLogger().severe("[Truthful] Failed to load a compatible version adapter! The plugin will be disabled.");
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(Bukkit.getPluginManager().getPlugin("Truthful"));
        }
    }

    public IVersionAdapter getAdapter() {
        if (this.adapter == null) {
            throw new IllegalStateException("Version adapter has not been loaded yet!");
        }
        return this.adapter;
    }
}