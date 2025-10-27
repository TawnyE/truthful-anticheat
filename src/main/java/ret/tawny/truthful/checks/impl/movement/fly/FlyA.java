package ret.tawny.truthful.checks.impl.movement.fly;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.player.PlayerUtils;
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'A', type = CheckType.FLY)
public final class FlyA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(6.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (playerData == null) return;

        // Exemptions
        if (player.getAllowFlight() || player.isFlying() || player.isGliding() || player.isInsideVehicle()) return;
        if (playerData.isTeleportTick() || !playerData.getVelocities().isEmpty()) return;
        if (playerData.isInLiquid() || WorldUtils.hasClimbableNearby(player)) return;
        if (PlayerUtils.getPotion(PotionEffectType.SLOW_FALLING, playerData) != null) return;

        // Logic
        final double deltaY = playerData.getDeltaY();
        final double lastDeltaY = playerData.getLastDeltaY();
        final double predicted = (lastDeltaY - PlayerUtils.GRAVITY_ACCELERATION) * PlayerUtils.AIR_DRAG;
        final double diff = Math.abs(deltaY - predicted);

        boolean suspicious = playerData.getTicksInAir() > 8
                && deltaY > -0.075D
                && diff > 0.05D
                && !WorldUtils.nearBlock(player);

        if (suspicious) {
            buffer.increase(player, 1.0);

            if (buffer.exceedsMax(player)) {
                String debug = String.format("Hovered. dY: %.3f, p: %.3f, diff: %.3f", deltaY, predicted, diff);
                flag(playerData, debug);
                buffer.reset(player, 3.0); // Reset buffer partially after a flag
            }
        } else {
            buffer.decrease(player, 1.0);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}