package ret.tawny.truthful.debug;

public enum DebugStatus {
    CLEAR("§a[CLEAR]", "§a"),
    NEAR_FLAG("§e[NEAR_FLAG]", "§e"),
    FLAGGED("§c[FLAGGED]", "§c");

    private final String badge;
    private final String colorCode;

    DebugStatus(String badge, String colorCode) {
        this.badge = badge;
        this.colorCode = colorCode;
    }

    public String getBadge() {
        return badge;
    }

    public String getColorCode() {
        return colorCode;
    }

    public static DebugStatus getFromBuffer(double currentBuffer, double maxThreshold) {
        if (maxThreshold <= 0) return CLEAR;
        double ratio = currentBuffer / maxThreshold;
        if (ratio >= 1.0) return FLAGGED;
        if (ratio >= 0.3) return NEAR_FLAG;
        return CLEAR;
    }
}
