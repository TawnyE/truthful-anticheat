package ret.tawny.truthful.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.DataManager;
import ret.tawny.truthful.data.ExemptionType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.server.position.SetPositionPacketWrapper;

@SuppressWarnings("deprecation")
public final class PlayerListener implements Listener {

    private final DataManager dataManager;

    public PlayerListener() {
        this.dataManager = Truthful.getInstance().getDataManager();
        Bukkit.getPluginManager().registerEvents(this, Truthful.getInstance().getPlugin());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            PlayerData data = this.dataManager.getPlayerData(player);
            if (data != null) data.setInventoryOpen(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            PlayerData data = this.dataManager.getPlayerData(player);
            if (data != null && !data.isInventoryOpen()) data.setInventoryOpen(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            PlayerData data = this.dataManager.getPlayerData(player);
            if (data != null) data.setInventoryOpen(false);
        }
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        Player player = event.getPlayer();
        this.dataManager.enter(player);

        if (TruthfulPacketListener.pendingBrands.containsKey(player.getUniqueId())) {
            String brand = TruthfulPacketListener.pendingBrands.remove(player.getUniqueId());
            TruthfulPacketListener.updatePlayerBrand(player, brand);
        }

        Truthful.getInstance().getServerScheduler().runRegionLater(player, () -> {
            if (!player.isOnline()) return;

            String brand = null;
            try {
                brand = player.getClientBrandName();
            } catch (Throwable ignored) {}

            if (brand != null && !brand.isEmpty()) {
                TruthfulPacketListener.updatePlayerBrand(player, brand);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PlayerData data = this.dataManager.getPlayerData(event.getPlayer());
        if (data != null) {
            data.handleWorldChange();
        }
    }

    @EventHandler
    public void onRiptide(PlayerRiptideEvent event) {
        PlayerData data = this.dataManager.getPlayerData(event.getPlayer());
        if (data != null) {
            data.setLastRiptideTick(data.getTicksTracked());
            data.setUsingRiptide(true);
            data.setExemption(ExemptionType.RIPTIDE, 60);
            data.setVelocityExemption(60);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.dataManager.eliminate(event.getPlayer());
        TruthfulPacketListener.pendingBrands.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(final PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData data = this.dataManager.getPlayerData(player);
        if (data != null) {
            Truthful.getInstance().getServerScheduler().runRegionLater(player, data::resetTransientState, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        PlayerData data = this.dataManager.getPlayerData(event.getPlayer());
        if (data != null) {
            data.updateStateCache();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        PlayerData data = this.dataManager.getPlayerData(event.getPlayer());
        if (data != null) {
            data.updateStateCache();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        PlayerData data = this.dataManager.getPlayerData(event.getPlayer());
        if (data != null) {
            data.updateStateCache();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.isCancelled()) return;

            boolean physicsDamage = switch (event.getCause()) {
                case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE, ENTITY_EXPLOSION, BLOCK_EXPLOSION, THORNS, CUSTOM -> true;
                default -> false;
            };

            if (physicsDamage) {
                PlayerData data = this.dataManager.getPlayerData(player);
                if (data != null) {
                    data.setLastDamageTick(data.getTicksTracked());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) {
            PlayerData data = this.dataManager.getPlayerData(player);
            if (data != null) data.setNearVehicle(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) {
            PlayerData data = this.dataManager.getPlayerData(player);
            if (data != null) {
                data.setLastVehicleExitTick(data.getTicksTracked());
                data.setPreExitVehicleSpeed(event.getVehicle().getVelocity().length());
                data.setNearVehicle(true);
            }
        }
    }

    @EventHandler
    public void onAttack(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (Truthful.getInstance().isBedrockPlayer(damager)) return;

        final PlayerData data = this.dataManager.getPlayerData(damager);
        if (data != null) {
            data.setLastTarget(event.getEntity());
            data.setLastHitTick(data.getTicksTracked());

            if (data.isInventoryOpen()) data.setInventoryOpen(false);

            ItemStack hand = damager.getInventory().getItemInMainHand();
            if (hand.getType() != Material.AIR && hand.getType().name().contains("MACE")) {
                if (damager.getFallDistance() > 1.5) {
                    data.setExemption(ExemptionType.MACE_SMASH, 30);

                    boolean hasWindBurst = hand.getEnchantments().keySet().stream()
                            .anyMatch(enchant -> enchant.getKey().getKey().contains("wind_burst"));

                    if (hasWindBurst) {
                        data.setExemption(ExemptionType.WIND_CHARGE, 40);
                        data.setVelocityExemption(40);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == Event.Result.DENY) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        final PlayerData data = this.dataManager.getPlayerData(player);
        if (data == null) return;

        if (data.isInventoryOpen()) data.setInventoryOpen(false);

        String typeName = item.getType().name();

        if (typeName.contains("FIREWORK") && player.isGliding()) {
            if (data.getTicksTracked() - data.getLastFireworkTick() > 10) {
                data.onFireworkInteraction();
                data.setLastFireworkTick(data.getTicksTracked());
            }
        }

        if (typeName.contains("WIND_CHARGE")) {
            // Let the server-sided explosion/velocity handle the movement physics.
            // Exempting right click directly allows abuse by just throwing it.
        }

        if (typeName.contains("SPEAR") || typeName.contains("TRIDENT") || typeName.contains("SWORD")) {
            if (item.hasItemMeta()) {
                String metaString = item.getItemMeta().toString().toLowerCase();
                String displayName = item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName().toLowerCase() : "";

                if (displayName.contains("spear") || metaString.contains("lunge")) {
                    data.setExemption(ExemptionType.SPEAR_LUNGE, 20);
                    data.setVelocityExemption(20);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) {
            PlayerData data = this.dataManager.getPlayerData(event.getPlayer());
            if (data != null) data.setLastGhostBlockTick(data.getTicksTracked());
        }
    }

    public void onPacket(final PacketReceiveEvent event, PacketType.Play.Client type) {
        final Player player = event.getPlayer();
        if (player == null || Truthful.getInstance().isBedrockPlayer(player)) return;

        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (playerData == null) return;

        if (type == PacketType.Play.Client.CLICK_WINDOW) {
            playerData.setLastWindowClick(System.currentTimeMillis());
            if (!playerData.isInventoryOpen()) playerData.setInventoryOpen(true);
        } else if (type == PacketType.Play.Client.CLIENT_STATUS) {
            WrapperPlayClientClientStatus cmd = new WrapperPlayClientClientStatus(event);
            if (cmd.getAction().name().equals("REQUEST_STATS")) playerData.setInventoryOpen(true);
        } else if (type == PacketType.Play.Client.CLOSE_WINDOW) {
            playerData.setInventoryOpen(false);
        } else if (type == PacketType.Play.Client.ANIMATION) {
            // Animation packet handled separately to not interfere
        } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            // FIXED: If they attack an entity, they are definitely NOT digging anymore.
            // This instantly unlocks all AutoClicker checks.
            if (playerData.isInventoryOpen()) playerData.setInventoryOpen(false);
            playerData.setDigging(false);
            playerData.setLastAttackPacketTick(playerData.getTicksTracked());
        } else if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            ret.tawny.truthful.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper cached = playerData.getCurrentBlockPlacement();
            if (cached != null) {
                Truthful.getInstance().getScheduler().onPacketReceive(cached);
                if (playerData.isHoldingBlock()) playerData.setUsingItem(true);
            }
        } else if (type == PacketType.Play.Client.USE_ITEM) {
            if (playerData.isHoldingBlock() || playerData.isHoldingCrystal()) playerData.setUsingItem(true);
        } else if (type == PacketType.Play.Client.STEER_VEHICLE) {
            playerData.setInsideVehicleCache(true);
            playerData.setLastVehicleTick(playerData.getTicksTracked());
        }

        if (type == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            WrapperPlayClientHeldItemChange itemSwitch = new WrapperPlayClientHeldItemChange(event);
            playerData.setLastSlot(playerData.getCurrentSlot());
            playerData.setCurrentSlot(itemSwitch.getSlot());
            playerData.setLastSlotSwitchTime(System.currentTimeMillis());
            playerData.setUsingItem(false);
        } else if (type == PacketType.Play.Client.USE_ITEM || type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            if (playerData.getCurrentBlockPlacement() != null) {
                playerData.setLastBlockPlaceTime(System.currentTimeMillis());
                playerData.setLastBlockPlaceTick(playerData.getTicksTracked());
            }
        } else if (type == PacketType.Play.Client.PLAYER_DIGGING) {
            try {
                WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
                if (dig.getAction() == DiggingAction.START_DIGGING) {
                    playerData.setDigging(true);
                } else if (dig.getAction() == DiggingAction.FINISHED_DIGGING || dig.getAction() == DiggingAction.CANCELLED_DIGGING) {
                    playerData.setDigging(false);
                } else if (dig.getAction() == DiggingAction.RELEASE_USE_ITEM || dig.getAction() == DiggingAction.DROP_ITEM || dig.getAction() == DiggingAction.DROP_ITEM_STACK) {
                    playerData.setUsingItem(false);
                }
            } catch (Throwable t) {
                Truthful.getInstance().getPlugin().getLogger().warning("[PlayerListener] Failed to parse PLAYER_DIGGING packet: " + t.getMessage());
            }
        } else if (type == PacketType.Play.Client.ENTITY_ACTION) {
            WrapperPlayClientEntityAction action = new WrapperPlayClientEntityAction(event);
            switch (action.getAction()) {
                case START_SPRINTING -> playerData.setSprinting(true);
                case STOP_SPRINTING -> playerData.setSprinting(false);
                case START_SNEAKING -> playerData.setSneaking(true);
                case STOP_SNEAKING -> playerData.setSneaking(false);
            }
        }
    }

    public void onPacket(final PacketSendEvent event) {
        final Player player = event.getPlayer();
        if (player == null || Truthful.getInstance().isBedrockPlayer(player)) return;
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (playerData == null) return;

        if (event.getPacketType() == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
            WrapperPlayServerPlayerPositionAndLook posLook = new WrapperPlayServerPlayerPositionAndLook(event);
            SetPositionPacketWrapper wrapper = new SetPositionPacketWrapper(posLook, player, PacketType.Play.Server.PLAYER_POSITION_AND_LOOK);
            playerData.acceptTeleport(wrapper);
        }
    }
}
