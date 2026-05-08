package ret.tawny.truthful.version;

import org.bukkit.Bukkit;
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

                if (minorVersion < 20) {
                    Bukkit.getLogger().warning("[Truthful] Detected legacy server version (1." + minorVersion + ").");
                    Bukkit.getLogger().warning("[Truthful] TruthfulAC v2.3+ is designed for 1.20+. Stability is not guaranteed.");
                }

                // We now strictly use the Modern adapter for everything.
                // 1.8/Legacy support has been dropped to streamline physics and attribute handling.
                this.adapter = new VersionAdapter_Modern(minorVersion);
                Bukkit.getLogger().info("[Truthful] Loaded Modern Version Adapter (1." + minorVersion + "+).");

            } else {
                throw new IllegalStateException("Could not determine server version from Bukkit version string: " + Bukkit.getServer().getBukkitVersion());
            }

        } catch (Exception e) {
            Bukkit.getLogger().severe("[Truthful] Failed to load version adapter! The plugin may not function correctly.");
            e.printStackTrace();
            // Fallback to modern if parsing fails, assuming 1.20+ context
            this.adapter = new VersionAdapter_Modern(20);
        }
    }

    public IVersionAdapter getAdapter() {
        if (this.adapter == null) {
            // Should not happen if load() is called on startup
            this.adapter = new VersionAdapter_Modern(20);
        }
        return this.adapter;
    }
}