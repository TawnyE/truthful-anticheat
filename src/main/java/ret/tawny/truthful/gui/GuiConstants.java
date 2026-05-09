package ret.tawny.truthful.gui;

import org.bukkit.Material;
import ret.tawny.truthful.checks.api.data.CheckType;

import java.util.EnumMap;
import java.util.Map;

public final class GuiConstants {

    public static final String PRIMARY = "\u00A7c";
    public static final String SECONDARY = "\u00A7e";
    public static final String ACCENT = "\u00A7b";
    public static final String SUCCESS = "\u00A7a";
    public static final String ERROR = "\u00A7c";
    public static final String WARNING = "\u00A76";
    public static final String MUTED = "\u00A77";
    public static final String DARK = "\u00A78";
    public static final String HIGHLIGHT = "\u00A7f";
    public static final String PURPLE = "\u00A75";
    public static final String LIGHT_PURPLE = "\u00A7d";
    public static final String AQUA = "\u00A73";
    public static final String BLUE = "\u00A79";
    public static final String BOLD = "\u00A7l";
    public static final String ITALIC = "\u00A7o";
    public static final String RESET = "\u00A7r";
    public static final String STRIKE = "\u00A7m";

    public static final String ARROW = ">";
    public static final String BULLET = "-";
    public static final String CHECK = "ON";
    public static final String CROSS = "OFF";
    public static final String LINE = "|";
    public static final String SYM_ARROW = ARROW;
    public static final String SYM_BULLET = BULLET;
    public static final String SYM_CHECK = CHECK;
    public static final String SYM_CROSS = CROSS;
    public static final String SYM_CIRCLE = "*";
    public static final String SYM_LINE = LINE;
    public static final String SYM_DASH = "-";
    public static final String SYM_HEART = "HP";
    public static final String SYM_STAR = "*";
    public static final String SYM_DIAMOND = "*";

    private static final Map<CheckType, Material> CHECK_ICONS = new EnumMap<>(CheckType.class);
    private static final Map<CheckType, String> TYPE_DESCRIPTIONS = new EnumMap<>(CheckType.class);

    static {
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

        TYPE_DESCRIPTIONS.put(CheckType.SIMULATION, "Movement prediction");
        TYPE_DESCRIPTIONS.put(CheckType.VELOCITY, "Knockback verification");
        TYPE_DESCRIPTIONS.put(CheckType.SPOOF, "Ground state validation");
        TYPE_DESCRIPTIONS.put(CheckType.PHASE, "Block clipping detection");
        TYPE_DESCRIPTIONS.put(CheckType.TIMER, "Packet timing balance");
        TYPE_DESCRIPTIONS.put(CheckType.KILLAURA, "Combat automation");
        TYPE_DESCRIPTIONS.put(CheckType.AIM, "Rotation analysis");
        TYPE_DESCRIPTIONS.put(CheckType.HITBOX, "Hitbox expansion");
        TYPE_DESCRIPTIONS.put(CheckType.REACH, "Attack distance limits");
        TYPE_DESCRIPTIONS.put(CheckType.AUTOCLICKER, "Click timing analysis");
        TYPE_DESCRIPTIONS.put(CheckType.RAYCAST, "Line-of-sight validation");
        TYPE_DESCRIPTIONS.put(CheckType.PACKET_ORDER, "Packet sequencing");
        TYPE_DESCRIPTIONS.put(CheckType.CRYSTAL, "Crystal combat checks");
        TYPE_DESCRIPTIONS.put(CheckType.ANCHOR, "Anchor combat checks");
        TYPE_DESCRIPTIONS.put(CheckType.SCAFFOLD, "Block placement patterns");
        TYPE_DESCRIPTIONS.put(CheckType.FAST_BREAK, "Break speed limits");
        TYPE_DESCRIPTIONS.put(CheckType.BAD_PACKET, "Protocol sanity checks");
        TYPE_DESCRIPTIONS.put(CheckType.SPRINT, "Sprint state checks");
        TYPE_DESCRIPTIONS.put(CheckType.CRASHER, "Crash exploit guards");
        TYPE_DESCRIPTIONS.put(CheckType.INVENTORY, "Inventory movement checks");
        TYPE_DESCRIPTIONS.put(CheckType.BARITONE, "Automation profiles");
        TYPE_DESCRIPTIONS.put(CheckType.BEDROCK, "Geyser/Bedrock checks");
        TYPE_DESCRIPTIONS.put(CheckType.INVALID, "Invalid packet checks");
    }

    public static Material getIcon(CheckType type) {
        return CHECK_ICONS.getOrDefault(type, getMat("PAPER"));
    }

    public static String getTypeDescription(CheckType type) {
        return TYPE_DESCRIPTIONS.getOrDefault(type, "Detection check");
    }

    public static String formatTitle(String section) {
        return PRIMARY + "Truthful " + DARK + "> " + MUTED + section;
    }

    public static String status(boolean enabled) {
        return enabled ? SUCCESS + CHECK : ERROR + CROSS;
    }

    public static String buildProgressBar(int current, int total, int length) {
        if (total <= 0) return MUTED + "No checks";
        double ratio = Math.max(0.0D, Math.min(1.0D, (double) current / (double) total));
        int filled = (int) Math.round(ratio * length);
        String color = ratio >= 1.0D ? SUCCESS : ratio >= 0.5D ? SECONDARY : ERROR;

        StringBuilder builder = new StringBuilder(color);
        for (int i = 0; i < length; i++) {
            builder.append(i < filled ? '|' : '.');
        }
        builder.append(' ').append(HIGHLIGHT).append((int) Math.round(ratio * 100.0D)).append('%');
        return builder.toString();
    }

    public static String metric(String label, String value) {
        return DARK + LINE + " " + MUTED + label + " " + HIGHLIGHT + value;
    }

    public static String separator() {
        return DARK + STRIKE + "------------------------------";
    }

    public static Material getMat(String... names) {
        for (String name : names) {
            try {
                Material material = Material.getMaterial(name);
                if (material != null) return material;
            } catch (Throwable ignored) {
            }
        }
        return Material.STONE;
    }

    private GuiConstants() {
    }
}
