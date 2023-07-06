package org.voitac.anticheat.config.api;

// TODO Reformat this to a specific configuration per category
public final class Configuration {
    private int minGcdThreshold = 20,
    maxGcdData = 40;

    // It is recommended that the minimum matches is half the minimum threshold
    private int minAimBMatches = 10;

    private enum Options {
        CENSOR,
        LAGBACK;

        private boolean enabled;

        Options(final boolean enabled) {
            this.enabled = enabled;
        }

        Options() {}

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static boolean isLagbacks() {
        return Options.LAGBACK.enabled;
    }

    public int getMinGcdThreshold() {
        return minGcdThreshold;
    }

    public void setMinGcdThreshold(final int minGcdThreshold) {
        this.minGcdThreshold = minGcdThreshold;
    }

    public int getMaxGcdData() {
        return maxGcdData;
    }

    public void setMaxGcdData(final int maxGcdData) {
        this.maxGcdData = maxGcdData;
    }

    public int getMinAimBMatches() {
        return minAimBMatches;
    }

    public void setMinAimBMatches(final int minAimBMatches) {
        this.minAimBMatches = minAimBMatches;
    }
}
