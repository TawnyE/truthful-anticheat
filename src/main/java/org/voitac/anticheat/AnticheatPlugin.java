package org.voitac.anticheat;

import org.bukkit.plugin.java.JavaPlugin;

public final class AnticheatPlugin extends JavaPlugin {

    @Override
    public void onLoad() {
    }

    @Override
    public void onEnable() {
        AntiCheat.getInstance().start(this);
    }

    @Override
    public void onDisable() {
        AntiCheat.getInstance().shutdown();
    }
}
