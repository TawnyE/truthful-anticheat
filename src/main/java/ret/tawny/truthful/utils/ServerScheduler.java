package ret.tawny.truthful.utils;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public class ServerScheduler {

    private final FoliaLib foliaLib;

    public ServerScheduler(Plugin plugin) {
        this.foliaLib = new FoliaLib(plugin);
    }

    public FoliaLib getFoliaLib() {
        return foliaLib;
    }

    public void runRegion(Entity entity, Runnable runnable) {
        foliaLib.getImpl().runAtEntity(entity, task -> runnable.run());
    }

    public WrappedTask runRegionLater(Entity entity, Runnable runnable, long delayTicks) {
        return foliaLib.getImpl().runAtEntityLater(entity, runnable, delayTicks);
    }

    public WrappedTask runRegionTimer(Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        return foliaLib.getImpl().runAtEntityTimer(entity, runnable, delayTicks, periodTicks);
    }

    public void runGlobal(Runnable runnable) {
        foliaLib.getImpl().runNextTick(task -> runnable.run());
    }

    public WrappedTask runGlobalLater(Runnable runnable, long delayTicks) {
        return foliaLib.getImpl().runLater(runnable, delayTicks);
    }

    public WrappedTask runGlobalTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return foliaLib.getImpl().runTimer(runnable, delayTicks, periodTicks);
    }

    public void runAsync(Runnable runnable) {
        foliaLib.getImpl().runAsync(task -> runnable.run());
    }

    public WrappedTask runAsyncLater(Runnable runnable, long delayTicks) {
        return foliaLib.getImpl().runLaterAsync(runnable, delayTicks);
    }

    public WrappedTask runAsyncTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return foliaLib.getImpl().runTimerAsync(runnable, delayTicks, periodTicks);
    }

    public void cancelAll() {
        foliaLib.getImpl().cancelAllTasks();
    }
}
