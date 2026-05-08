package ret.tawny.truthful.util;

import org.bukkit.Bukkit;
import ret.tawny.truthful.Truthful;

/**
 * Threading helpers for Bukkit/Paper safety.
 */
public final class Threading {

    private Threading() {
    }

    /**
     * Run now when on main thread, otherwise schedule to main thread.
     */
    public static void runOnMain(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }

        Bukkit.getScheduler().runTask(Truthful.getInstance().getPlugin(), runnable);
    }

    /**
     * Run work asynchronously.
     */
    public static void runAsync(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(Truthful.getInstance().getPlugin(), runnable);
    }
}

