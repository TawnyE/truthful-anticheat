package ret.tawny.truthful.gui;

import org.bukkit.Material;
import ret.tawny.truthful.checks.api.data.CheckType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enterprise GUI Constants
 * Centralized design tokens, icons, and formatting constants.
 */
public final class GuiConstants {

    // ═══════════════════════════════════════════════
    // COLOR PALETTE
    // ═══════════════════════════════════════════════

    public static final String PRIMARY = "§c"; // Red accent (brand)
    public static final String SECONDARY = "§e"; // Gold
    public static final String ACCENT = "§b"; // Cyan/Aqua
    public static final String SUCCESS = "§a"; // Green
    public static final String ERROR = "§c"; // Red
    public static final String WARNING = "§6"; // Orange
    public static final String MUTED = "§7"; // Gray
    public static final String DARK = "§8"; // Dark Gray
    public static final String HIGHLIGHT = "§f"; // White
    public static final String PURPLE = "§5"; // Purple
    public static final String LIGHT_PURPLE = "§d"; // Light Purple
    public static final String AQUA = "§3"; // Dark Aqua
    public static final String BLUE = "§9"; // Blue
    public static final String BOLD = "§l";
    public static final String ITALIC = "§o";
    public static final String RESET = "§r";
    public static final String STRIKE = "§m";

    // ═══════════════════════════════════════════════
    // UNICODE SYMBOLS
    // ═══════════════════════════════════════════════

    public static final String SYM_ARROW = "»";
    public static final String SYM_BULLET = "▸";
    public static final String SYM_CHECK = "✔";
    public static final String SYM_CROSS = "✖";
    public static final String SYM_HEART = "❤";
    public static final String SYM_STAR = "★";
    public static final String SYM_DIAMOND = "◆";
    public static final String SYM_CIRCLE = "●";
    public static final String SYM_LINE = "│";
    public static final String SYM_DASH = "━";
    public static final String BAR_FULL = "█";
    public static final String BAR_HALF = "▌";
    public static final String BAR_EMPTY = "░";

    // ═══════════════════════════════════════════════
    // CHECK TYPE ICONS
    // ═══════════════════════════════════════════════

    private static final Map<CheckType, Material> CHECK_ICONS = new LinkedHashMap<>();
    private static final Map<CheckType, String> TYPE_DESCRIPTIONS = new LinkedHashMap<>();

    static {
        initIcons();
        initDescriptions();
    }

    private static void initIcons() {
        CHECK_ICONS.put(CheckType.SIMULATION, getMat("COMPASS"));
        CHECK_ICONS.put(CheckType.VELOCITY, getMat("SLIME_BLOCK", "SLIME_BALL"));
        CHECK_ICONS.put(CheckType.SPOOF, getMat("MAGMA_CREAM"));
        CHECK_ICONS.put(CheckType.PHASE, getMat("ENDER_PEARL"));
        CHECK_ICONS.put(CheckType.TIMER, getMat("CLOCK", "WATCH"));
        CHECK_ICONS.put(CheckType.KILLAURA, getMat("IRON_SWORD"));
        CHECK_ICONS.put(CheckType.AIM, getMat("ENDER_EYE"));
        CHECK_ICONS.put(CheckType.HITBOX, getMat("IRON_CHESTPLATE"));
        CHECK_ICONS.put(CheckType.REACH, getMat("FISHING_ROD"));
        CHECK_ICONS.put(CheckType.AUTOCLICKER, getMat("TRIPWIRE_HOOK"));
        CHECK_ICONS.put(CheckType.RAYCAST, getMat("SPECTRAL_ARROW", "ARROW"));
        CHECK_ICONS.put(CheckType.PACKET_ORDER, getMat("COMPARATOR", "REDSTONE_COMPARATOR"));
        CHECK_ICONS.put(CheckType.CRYSTAL, getMat("END_CRYSTAL", "EYE_OF_ENDER"));
        CHECK_ICONS.put(CheckType.ANCHOR, getMat("RESPAWN_ANCHOR", "OBSIDIAN"));
        CHECK_ICONS.put(CheckType.SCAFFOLD, getMat("SCAFFOLDING", "LADDER"));
        CHECK_ICONS.put(CheckType.FAST_BREAK, getMat("GOLDEN_PICKAXE", "GOLD_PICKAXE"));
        CHECK_ICONS.put(CheckType.BAD_PACKET, getMat("BARRIER"));
        CHECK_ICONS.put(CheckType.SPRINT, getMat("LEATHER_BOOTS"));
        CHECK_ICONS.put(CheckType.CRASHER, getMat("TNT"));
        CHECK_ICONS.put(CheckType.INVENTORY, getMat("CHEST"));
        CHECK_ICONS.put(CheckType.BARITONE, getMat("COMPASS"));
        CHECK_ICONS.put(CheckType.BEDROCK, getMat("BEDROCK"));
    }

    private static void initDescriptions() {
        TYPE_DESCRIPTIONS.put(CheckType.SIMULATION, "Advanced movement simulation");
        TYPE_DESCRIPTIONS.put(CheckType.VELOCITY, "Knockback verification");
        TYPE_DESCRIPTIONS.put(CheckType.KILLAURA, "Combat pattern analysis");
        TYPE_DESCRIPTIONS.put(CheckType.SCAFFOLD, "Block placement analysis");
        TYPE_DESCRIPTIONS.put(CheckType.BAD_PACKET, "Protocol violations");
        TYPE_DESCRIPTIONS.put(CheckType.PHASE, "Block clipping detection");
        TYPE_DESCRIPTIONS.put(CheckType.TIMER, "Game speed manipulation");
        TYPE_DESCRIPTIONS.put(CheckType.BARITONE, "Automation detection");
        TYPE_DESCRIPTIONS.put(CheckType.BEDROCK, "Bedrock edition profiles");
        TYPE_DESCRIPTIONS.put(CheckType.HITBOX, "Hitbox expansion");
        TYPE_DESCRIPTIONS.put(CheckType.REACH, "Attack reach limits");
        TYPE_DESCRIPTIONS.put(CheckType.SPRINT, "Sprint violations");
        TYPE_DESCRIPTIONS.put(CheckType.INVENTORY, "Inventory exploits");
        TYPE_DESCRIPTIONS.put(CheckType.CRYSTAL, "Crystal aura");
        TYPE_DESCRIPTIONS.put(CheckType.ANCHOR, "Anchor aura");
        TYPE_DESCRIPTIONS.put(CheckType.PACKET_ORDER, "Packet sequencing");
        TYPE_DESCRIPTIONS.put(CheckType.CRASHER, "Crash exploits");
        TYPE_DESCRIPTIONS.put(CheckType.INVALID, "Invalid packets");
        TYPE_DESCRIPTIONS.put(CheckType.FAST_BREAK, "Fast break detection");
    }

    // ═══════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════

    public static Material getIcon(CheckType type) {
        return CHECK_ICONS.getOrDefault(type, getMat("PAPER"));
    }

    public static String getTypeDescription(CheckType type) {
        return TYPE_DESCRIPTIONS.getOrDefault(type, "Detection check");
    }

    /**
     * Build a premium progress bar string.
     * Example: §a████████§7░░ §f80%
     */
    public static String buildProgressBar(int current, int total, int barLength) {
        if (total <= 0)
            return MUTED + "N/A";
        double ratio = (double) current / total;
        int filled = (int) Math.round(ratio * barLength);
        int empty = barLength - filled;

        String fillColor = ratio >= 1.0 ? SUCCESS : ratio >= 0.5 ? SECONDARY : ERROR;

        StringBuilder sb = new StringBuilder();
        sb.append(fillColor);
        for (int i = 0; i < filled; i++)
            sb.append(BAR_FULL);
        sb.append(DARK);
        for (int i = 0; i < empty; i++)
            sb.append(BAR_EMPTY);
        sb.append(" ").append(HIGHLIGHT).append(Math.round(ratio * 100)).append("%");
        return sb.toString();
    }

    /**
     * Format a separator line for lore.
     */
    public static String separator() {
        return DARK + STRIKE + "                              ";
    }

    /**
     * Safe material lookup with fallback chain.
     */
    public static Material getMat(String... names) {
        for (String name : names) {
            try {
                Material mat = Material.getMaterial(name);
                if (mat != null)
                    return mat;
            } catch (Throwable ignored) {
            }
        }
        return Material.STONE;
    }

    private GuiConstants() {
    }
}
