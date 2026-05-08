package ret.tawny.truthful.data.processor;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.PhysicsUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// CHANGE: DECOMP-2 - Extracted StateCacheProcessor from PlayerData god class
public final class StateCacheProcessor {

    // CHANGE: PERF-4 - Changed from HashMap to ConcurrentHashMap for thread safety
    private final Map<PotionEffectType, Integer> potionCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> enchantmentCache = new ConcurrentHashMap<>();
    private double walkSpeedCache = 0.1;
    private int foodLevelCache = 20;
    private boolean allowFlightCache = false;
    private boolean flyingCache = false;
    private boolean glidingCache = false;
    private boolean insideVehicleCache = false;
    private int worldMinHeightCache = -64;
    private int worldMaxHeightCache = 320;
    private boolean holdingBlockCache = false;
    private boolean wearingElytraCache = false;
    private boolean holdingCrystalCache = false;
    private boolean slowItemCache = false;
    private boolean tridentCache = false;
    private int lastCacheUpdate = -100;

    // CHANGE: PERF-5 - Coalescing flag for state update requests
    private final AtomicBoolean stateUpdatePending = new AtomicBoolean(false);

    public int getPotionLevel(PotionEffectType type) {
        return potionCache.getOrDefault(type, 0);
    }

    public boolean hasPotionEffect(PotionEffectType type) {
        return potionCache.containsKey(type);
    }

    public double getWalkSpeed() {
        return walkSpeedCache;
    }

    public boolean isAllowFlight() {
        return allowFlightCache;
    }

    public boolean isFlying() {
        return flyingCache;
    }

    public boolean isGliding() {
        return glidingCache;
    }

    public boolean isInsideVehicle() {
        return insideVehicleCache;
    }

    public int getWorldMinHeight() {
        return worldMinHeightCache;
    }

    public int getWorldMaxHeight() {
        return worldMaxHeightCache;
    }

    public boolean isHoldingBlock() {
        return holdingBlockCache;
    }

    public boolean isWearingElytra() {
        return wearingElytraCache;
    }

    public boolean isHoldingCrystal() {
        return holdingCrystalCache;
    }

    public boolean isSlowItem() {
        return slowItemCache;
    }

    public boolean isTrident() {
        return tridentCache;
    }

    public int getEnchantLevel(String key) {
        return enchantmentCache.getOrDefault(key, 0);
    }

    public int getFoodLevel() {
        return foodLevelCache;
    }

    public int getLastCacheUpdate() {
        return lastCacheUpdate;
    }

    // CHANGE: PERF-5 - Added coalescing for main-thread scheduling
    public void update(PlayerData data) {
        if (!Bukkit.isPrimaryThread()) {
            if (stateUpdatePending.compareAndSet(false, true)) {
                ret.tawny.truthful.util.Threading.runOnMain(() -> {
                    stateUpdatePending.set(false);
                    update(data);
                });
            }
            return;
        }

        Player player = data.getPlayer();
        if (player == null || !player.isOnline()) return;

        potionCache.clear();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            potionCache.put(effect.getType(), effect.getAmplifier() + 1);
        }

        enchantmentCache.clear();
        updateEnchantment(player.getInventory().getBoots(), "soul_speed", org.bukkit.enchantments.Enchantment.SOUL_SPEED);
        updateEnchantment(player.getInventory().getBoots(), "depth_strider", org.bukkit.enchantments.Enchantment.DEPTH_STRIDER);
        updateEnchantment(player.getInventory().getLeggings(), "swift_sneak", org.bukkit.enchantments.Enchantment.SWIFT_SNEAK);
        updateEnchantment(player.getInventory().getItemInMainHand(), "efficiency", org.bukkit.enchantments.Enchantment.EFFICIENCY);

        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            walkSpeedCache = attr.getValue();
        }

        foodLevelCache = player.getFoodLevel();
        allowFlightCache = player.getAllowFlight();
        flyingCache = player.isFlying();
        glidingCache = player.isGliding();
        insideVehicleCache = player.isInsideVehicle();
        worldMinHeightCache = player.getWorld().getMinHeight();
        worldMaxHeightCache = player.getWorld().getMaxHeight();

        org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
        holdingBlockCache = (hand != null && hand.getType().isBlock() && hand.getType() != org.bukkit.Material.AIR);

        org.bukkit.inventory.ItemStack chest = player.getInventory().getChestplate();
        wearingElytraCache = (chest != null && chest.getType() == org.bukkit.Material.ELYTRA);

        holdingCrystalCache = (hand != null && hand.getType().name().contains("END_CRYSTAL"));

        org.bukkit.inventory.ItemStack off = player.getInventory().getItemInOffHand();
        slowItemCache = PhysicsUtils.shouldSlowDown(hand, player) ||
                        PhysicsUtils.shouldSlowDown(off, player);

        tridentCache = (hand != null && hand.getType() == org.bukkit.Material.TRIDENT) ||
                       (off != null && off.getType() == org.bukkit.Material.TRIDENT);

        lastCacheUpdate = data.getTicksTracked();
    }

    private void updateEnchantment(org.bukkit.inventory.ItemStack item, String key, org.bukkit.enchantments.Enchantment enchant) {
        if (item != null && item.getType() != org.bukkit.Material.AIR) {
            int level = item.getEnchantmentLevel(enchant);
            if (level > 0) enchantmentCache.put(key, level);
        }
    }
}
