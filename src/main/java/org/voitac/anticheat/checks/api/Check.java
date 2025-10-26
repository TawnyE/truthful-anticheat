package org.voitac.anticheat.checks.api;

import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.event.Listener;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;
import org.bukkit.Bukkit;

public abstract class Check implements Listener {
    /**
     * Check Type Order, eg "Aim'A'"
     */
    private final char order;

    /**
     * Check Type, eg Aim'A' would have the CheckType CheckType.AIM
     */
    private final CheckType checkType;

    protected Check() {
        final CheckData checkData = this.getClass().getAnnotation(CheckData.class);

        this.order = checkData.order();
        this.checkType = checkData.type();
    }

    protected static void print(final String message) {
        Bukkit.getServer().broadcastMessage(message);
    }

    protected static void formattedFlag(final Check check, final PlayerData player) {
        Bukkit.getServer().broadcastMessage(format(check) + player.getPlayer().getName() + " VL[" + player.increment() + "]Ping[" + player.getPing() + "]" );
    }

    protected static void formattedFlag(final Check check, final PlayerData player, final Object message) {
        Bukkit.getServer().broadcastMessage(format(check) + player.getPlayer().getName() + " VL[" + player.increment() + "] Ping[" + player.getPing() + "] " + message);
    }

    private static String format(final Check check) {
        return "§3[" + check.checkType.getName(check) + "] §r";
    }

    public final char getOrder() {
        return this.order;
    }

    public final CheckType getCheckType() {
        return this.checkType;
    }

    public final String getFormattedName() {
        return this.checkType.getName(this);
    }

    public void onPacketPlaySend(final PacketEvent event) {}

    public void onPacketPlayerReceive(final PacketEvent event) {}

    public void onRelMove(final RelMovePacketWrapper event) {}

    public void onServerTick() {}
}
