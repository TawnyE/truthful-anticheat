package ret.tawny.truthful.utils.math;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RunningMode - Statistical Mode Tracker
 *
 * Tracks a window of samples and identifies the most frequent value.
 * Used to identify a player's constant mouse sensitivity.
 */
public class RunningMode {
    private final int capacity;
    private final List<Double> samples = new ArrayList<>();
    private final double threshold = 0.0001;

    public RunningMode(int capacity) {
        this.capacity = capacity;
    }

    public void add(double value) {
        samples.add(value);
        if (samples.size() > capacity) {
            samples.remove(0);
        }
    }

    public double getMode() {
        if (samples.isEmpty()) return 0.0;

        Map<Double, Integer> frequencies = new HashMap<>();
        for (double sample : samples) {
            boolean found = false;
            for (double key : frequencies.keySet()) {
                if (Math.abs(key - sample) < threshold) {
                    frequencies.put(key, frequencies.get(key) + 1);
                    found = true;
                    break;
                }
            }
            if (!found) {
                frequencies.put(sample, 1);
            }
        }

        double mode = 0.0;
        int maxFreq = 0;
        for (Map.Entry<Double, Integer> entry : frequencies.entrySet()) {
            if (entry.getValue() > maxFreq) {
                maxFreq = entry.getValue();
                mode = entry.getKey();
            }
        }
        return (maxFreq > capacity / 4) ? mode : 0.0;
    }

    public int size() {
        return samples.size();
    }
}