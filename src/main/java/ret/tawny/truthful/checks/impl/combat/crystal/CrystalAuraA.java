package ret.tawny.truthful.checks.impl.combat.crystal;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@CheckData(order = 'A', type = CheckType.CRYSTAL)
public final class CrystalAuraA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, Long> lastPlaceTime = new ConcurrentHashMap<>();

    // Store when crystals were spawned to detect ID Predict
    private final Map<Integer, Long> crystalSpawnTimes = new ConcurrentHashMap<>();

    // We need to clean up the spawn map periodically
    private long lastCleanup = System.currentTimeMillis();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null)
            return;

        // Cleanup cache every minute
        if (System.currentTimeMillis() - lastCleanup > 60000) {
            crystalSpawnTimes.clear();
            lastCleanup = System.currentTimeMillis();
        }

        // 1. Track Crystal Placement
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement placement = new WrapperPlayClientPlayerBlockPlacement(event);
            ItemStack stack = player.getInventory().getItemInMainHand();
            if (stack == null || stack.getType() != Material.END_CRYSTAL) {
                stack = player.getInventory().getItemInOffHand();
            }

            if (stack != null && stack.getType() == Material.END_CRYSTAL) {
                lastPlaceTime.put(player.getUniqueId(), System.currentTimeMillis());
            }
        }

        // 2. Track Attack on Crystal
        else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);

            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                long now = System.currentTimeMillis();

                // Logic 1: Place -> Break Speed (Self-Placement)
                long lastPlace = lastPlaceTime.getOrDefault(player.getUniqueId(), 0L);
                long diff = now - lastPlace;

                if (diff < 100) {
                    // 50ms = 1 tick. If Place -> Break is < 50ms, it's instant.
                    // ID Predict often does this in 0-10ms.
                    if (diff < 40) {
                        if (buffer.increase(player, 2.0) > 6.0) {
                            flag(data, String.format("Fast Crystal (Place->Break). Delay: %dms", diff));
                            // ID Predict / Fast Crystal is a hard combat cheat -> lagback
                            data.executeLagback();
                            buffer.reset(player, 3.0);
                        }
                    } else {
                        buffer.decrease(player, 0.25);
                    }
                } else {
                    buffer.decrease(player, 0.1);
                }


                Truthful.getInstance().getServerScheduler().runRegion(player, () -> {
                    if (!player.isOnline())
                        return;

                    Entity target = null;
                    int targetId = interact.getEntityId();


                    for (Entity e : player.getNearbyEntities(10, 10, 10)) {
                        if (e.getEntityId() == targetId) {
                            target = e;
                            break;
                        }
                    }

                    if (target == null) {
                        // If entity is null but client sent attack, they might be predicting an ID that
                        // doesn't exist yet!
                        // However, it could also be desync (already dead).
                        return;
                    }

                    if (target.getType() == EntityType.END_CRYSTAL) {
                        // If the crystal has existed for 0 ticks, it JUST spawned.
                        // Clients shouldn't be able to attack it that fast unless predictable.
                        if (target.getTicksLived() <= 0) {
                            if (buffer.increase(player, 1.5) > 5.0) {
                                flag(data, "Crystal ID Predict (0-Tick Attack)");
                                data.executeLagback();
                            }
                        }
                    }
                });
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        lastPlaceTime.remove(event.getPlayer().getUniqueId());
    }
}