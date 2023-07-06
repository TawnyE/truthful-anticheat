package org.voitac.anticheat.checks.registry;

import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.utils.reflection.Manager;
import org.bukkit.Bukkit;

public final class CheckRegistry extends Manager<Class<? extends Check>, Check> {
    public CheckRegistry() {
        this.register(Check.class, "org.faithful.anticheat.checks.impl", CheckData.class);
    }

    public void init() {
        this.getCollection().forEach(check -> Bukkit.getPluginManager().registerEvents(check, AntiCheat.getInstance().getPlugin()));
    }
}
