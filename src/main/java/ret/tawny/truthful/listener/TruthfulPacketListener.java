package ret.tawny.truthful.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTeleportConfirm;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.registry.CheckRegistry;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.ChunkSnapshot;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TruthfulPacketListener extends PacketListenerAbstract {

    public static final Map<UUID, String> pendingBrands = new ConcurrentHashMap<>();
    private static final Set<String> chunkBuildsInFlight = ConcurrentHashMap.newKeySet();
    private final CheckRegistry checkManager;

    public TruthfulPacketListener(final CheckRegistry checkManager) {
        super(PacketListenerPriority.NORMAL);
        this.checkManager = checkManager;
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player)) return;
        final Player player = (Player) rawPlayer;

        final PacketTypeCommon type = event.getPacketType();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return;

        if (type == PacketType.Play.Client.TELEPORT_CONFIRM) {
            WrapperPlayClientTeleportConfirm confirm = new WrapperPlayClientTeleportConfirm(event);
            data.handleTeleportConfirm(confirm.getTeleportId());
        }

        if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            data.setCurrentBlockPlacement(
                    new ret.tawny.truthful.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper(event));
        }

        if (type == PacketType.Play.Client.PONG) {
            WrapperPlayClientPong pong = new WrapperPlayClientPong(event);
            final int id = pong.getId();
            if (id >= Short.MIN_VALUE && id <= Short.MAX_VALUE) {
                data.handleTransaction((short) id);
            }
        } else if (type == PacketType.Play.Client.WINDOW_CONFIRMATION) {
            WrapperPlayClientWindowConfirmation transaction = new WrapperPlayClientWindowConfirmation(event);
            data.handleTransaction(transaction.getActionId());
        }

        if (type instanceof PacketType.Play.Client) {
            Truthful.getInstance().getPlayerListener().onPacket(event, (PacketType.Play.Client) type);
        }

        final boolean isRelMove = RelMovePacketWrapper.isRelMove(type);

        if (!isRelMove) {
            checkManager.getPacketChecks().forEach(check -> {
                if (!check.canCheck(player)) return;
                try {
                    check.handlePacketPlayerReceive(event);
                } catch (Throwable t) {
                    Truthful.getInstance().getPlugin().getLogger().warning(
                            "[CheckDispatch] " + check.getClass().getSimpleName()
                                    + " failed in handlePacketPlayerReceive: " + t.getMessage());
                }
            });
        } else {
            if (type instanceof PacketType.Play.Client) {
                Object wrapper = createMovementWrapper(event, type);
                if (wrapper != null) {
                    final RelMovePacketWrapper relMovePacketWrapper = new RelMovePacketWrapper(
                            wrapper, player, (PacketType.Play.Client) type, data);

                    // FIXED: Always tick the internal clock regardless of position updates!
                    // This prevents exemptions and buffers from freezing when standing still.
                    data.update(relMovePacketWrapper);

                    if (!data.isServerFrozen()) {
                        checkManager.getMovementChecks().forEach(check -> {
                            if (check.canCheck(player)) {
                                check.handleRelMove(relMovePacketWrapper);
                            }
                        });
                    }
                    data.finishMovementTick();
                }
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player)) return;
        final Player player = (Player) rawPlayer;

        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return;

        Truthful.getInstance().getPlayerListener().onPacket(event);

        if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
            int chunkX = wrapper.getColumn().getX();
            int chunkZ = wrapper.getColumn().getZ();
            UUID worldUid = player.getWorld().getUID();

            final String inFlightKey = worldUid + ":" + chunkX + ":" + chunkZ;
            final boolean hasCachedChunk = Truthful.getInstance().getGlobalWorldCache().getChunk(worldUid, chunkX, chunkZ) != null;
            final boolean shouldBuild = !hasCachedChunk && chunkBuildsInFlight.add(inFlightKey);

            int minHeight = data.getWorldMinHeight();

            if (shouldBuild) {
                Truthful.getInstance().getServerScheduler().runAsync(() -> {
                    try {
                        if (Truthful.getInstance().getGlobalWorldCache().getChunk(worldUid, chunkX, chunkZ) == null) {
                            ChunkSnapshot snapshot = new ChunkSnapshot();
                            BaseChunk[] chunks = wrapper.getColumn().getChunks();

                            for (int i = 0; i < chunks.length; i++) {
                                BaseChunk section = chunks[i];
                                if (section == null) continue;
                                int sectionBaseY = (i << 4) + minHeight;

                                for (int x = 0; x < 16; x++) {
                                    int worldX = chunkX * 16 + x;
                                    for (int z = 0; z < 16; z++) {
                                        int worldZ = chunkZ * 16 + z;
                                        for (int y = 0; y < 16; y++) {
                                            int gid = section.get(x, y, z).getGlobalId();
                                            if (gid != 0) {
                                                snapshot.setBlock(worldX, sectionBaseY + y, worldZ, gid);
                                            }
                                        }
                                    }
                                }
                            }
                            Truthful.getInstance().getGlobalWorldCache().addChunk(worldUid, chunkX, chunkZ, snapshot);
                        }
                    } catch (Throwable t) {
                        Truthful.getInstance().getPlugin().getLogger()
                                .warning("[WorldCache] Failed to build chunk " + inFlightKey + ": " + t.getMessage());
                    } finally {
                        chunkBuildsInFlight.remove(inFlightKey);
                    }
                });
            }
        }

        else if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
            Vector3i pos = wrapper.getBlockPosition();
            data.getWorldCache().updateBlock(pos.getX(), pos.getY(), pos.getZ(), wrapper.getBlockId());
        }

        else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
            for (WrapperPlayServerMultiBlockChange.EncodedBlock block : wrapper.getBlocks()) {
                data.getWorldCache().updateBlock(block.getX(), block.getY(), block.getZ(), block.getBlockId());
            }
        }

        else if (event.getPacketType() == PacketType.Play.Server.UNLOAD_CHUNK) {
            WrapperPlayServerUnloadChunk wrapper = new WrapperPlayServerUnloadChunk(event);
            data.getWorldCache().removeChunk(wrapper.getChunkX(), wrapper.getChunkZ());
        }

        if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            WrapperPlayServerSpawnPlayer wrapper = new WrapperPlayServerSpawnPlayer(event);
            Truthful.getInstance().getCompensationTracker().handleSpawn(wrapper.getEntityId(), wrapper.getUUID(),
                    wrapper.getPosition().x, wrapper.getPosition().y, wrapper.getPosition().z, 0.6, 1.8, true);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);
            UUID uuid = wrapper.getUUID().orElse(UUID.randomUUID());
            Truthful.getInstance().getCompensationTracker().handleSpawn(wrapper.getEntityId(), uuid,
                    wrapper.getPosition().x, wrapper.getPosition().y, wrapper.getPosition().z, 0.6, 1.8, false);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity wrapper = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity(
                    event);
            Truthful.getInstance().getCompensationTracker().handleSpawn(wrapper.getEntityId(), UUID.randomUUID(),
                    wrapper.getPosition().x, wrapper.getPosition().y, wrapper.getPosition().z, 0.6, 1.8, false);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport wrapper = new WrapperPlayServerEntityTeleport(event);
            Truthful.getInstance().getCompensationTracker().handleTeleport(wrapper.getEntityId(),
                    wrapper.getPosition().x, wrapper.getPosition().y, wrapper.getPosition().z);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove wrapper = new WrapperPlayServerEntityRelativeMove(event);
            Truthful.getInstance().getCompensationTracker().handleRelMove(wrapper.getEntityId(),
                    wrapper.getDeltaX(), wrapper.getDeltaY(), wrapper.getDeltaZ());
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation wrapper = new WrapperPlayServerEntityRelativeMoveAndRotation(
                    event);
            Truthful.getInstance().getCompensationTracker().handleRelMove(wrapper.getEntityId(),
                    wrapper.getDeltaX(), wrapper.getDeltaY(), wrapper.getDeltaZ());
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            WrapperPlayServerDestroyEntities wrapper = new WrapperPlayServerDestroyEntities(event);
            for (int id : wrapper.getEntityIds()) {
                Truthful.getInstance().getCompensationTracker().handleDestroy(id);
            }
        }

        if (event.getPacketType() == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity velocity = new WrapperPlayServerEntityVelocity(event);
            if (velocity.getEntityId() == player.getEntityId()) {
                com.github.retrooper.packetevents.util.Vector3d vel = velocity.getVelocity();
                if (Math.abs(vel.x) > 0.001 || Math.abs(vel.y) > 0.001 || Math.abs(vel.z) > 0.001) {
                    handleVelocity(player, data, new Vector(vel.x, vel.y, vel.z), event, false);
                }
            } else {
                com.github.retrooper.packetevents.util.Vector3d v = velocity.getVelocity();
                Truthful.getInstance().getCompensationTracker().handleVelocity(velocity.getEntityId(), v.x, v.y, v.z);
            }
        }

        if (event.getPacketType() == PacketType.Play.Server.EXPLOSION) {
            data.markExplosionPacket();
            WrapperPlayServerExplosion explosion = new WrapperPlayServerExplosion(event);
            com.github.retrooper.packetevents.util.Vector3f push = explosion.getPlayerMotion();

            if (push != null && (Math.abs(push.x) > 0.001 || Math.abs(push.y) > 0.001 || Math.abs(push.z) > 0.001)) {
                handleVelocity(player, data, new Vector(push.x, push.y, push.z), event, true);
            }
        }

        checkManager.getSendPacketChecks().forEach(check -> {
            if (check.canCheck(player)) {
                check.handlePacketPlaySend(event);
            }
        });
    }

    private void handleVelocity(Player player, PlayerData data, Vector vel, PacketSendEvent event, boolean isExplosion) {
        int startId = data.getNextTransactionId();
        int endId = data.getNextTransactionId();

        event.getTasksAfterSend().add(() -> {
            if (player.isOnline()) {
                data.recordTransactionSent((short) startId);
                sendTransactionPacket(player, startId);
                data.recordTransactionSent((short) endId);
                sendTransactionPacket(player, endId);
            }
        });

        data.getVelocities().addVelocity(vel, startId, endId, isExplosion);
        data.setLastVelocityTick(data.getTicksTracked());
    }

    private void sendTransactionPacket(Player player, int id) {
        if (Truthful.USE_MODERN_PING) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPing(id));
        } else {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation(0,
                            (short) id, false));
        }
    }

    public static void updatePlayerBrand(Player player, String brand) {
        if (player == null || brand == null || brand.isBlank()) return;

        final Truthful truthful = Truthful.getInstance();
        if (truthful.getPlugin() == null || truthful.getDataManager() == null) {
            pendingBrands.put(player.getUniqueId(), brand);
            return;
        }

        PlayerData data = truthful.getDataManager().getPlayerData(player);
        if (data == null) {
            pendingBrands.put(player.getUniqueId(), brand);
            return;
        }

        String currentBrand = data.getClientBrand();
        if (!(currentBrand.equals("Unknown")
                || (!brand.equalsIgnoreCase("vanilla") && !brand.equalsIgnoreCase(currentBrand)))) {
            return;
        }

        Configuration config = truthful.getConfiguration();
        if (config != null && config.isClientBlockerEnabled()) {
            String checkBrand = brand.toLowerCase();
            for (String blockedBrand : config.getBlockedBrands()) {
                if (checkBrand.contains(blockedBrand.toLowerCase())) {
                    Truthful.getInstance().getServerScheduler().runRegion(player, () -> {
                        if (player.isOnline()) {
                            player.kickPlayer(config.getClientBlockerMessage());
                        }
                    });
                    return;
                }
            }
        }

        data.setClientBrand(brand);
        broadcastBrand(player, brand);
    }

    private Object createMovementWrapper(PacketReceiveEvent event, PacketTypeCommon type) {
        if (type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION)
            return new WrapperPlayClientPlayerPositionAndRotation(event);
        else if (type == PacketType.Play.Client.PLAYER_POSITION)
            return new WrapperPlayClientPlayerPosition(event);
        else if (type == PacketType.Play.Client.PLAYER_ROTATION)
            return new WrapperPlayClientPlayerRotation(event);
        else if (type == PacketType.Play.Client.PLAYER_FLYING)
            return new WrapperPlayClientPlayerFlying(event);
        else if (type == PacketType.Play.Client.VEHICLE_MOVE)
            return new WrapperPlayClientVehicleMove(event);
        return null;
    }

    public static void broadcastBrand(Player player, String brand) {
        if (brand == null || brand.equals("Unknown") || brand.equals("vanilla"))
            return;
        Truthful.getInstance().getServerScheduler().runGlobal(() -> {
            String msg = Truthful.getInstance().getConfiguration().getBrandMessage()
                    .replace("%player%", player.getName()).replace("%brand%", brand);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("truthful.alerts")) {
                    PlayerData staffData = Truthful.getInstance().getDataManager().getPlayerData(staff);
                    if (staffData != null && staffData.isAlertsEnabled())
                        staff.sendMessage(msg);
                }
            }
        });
    }
}