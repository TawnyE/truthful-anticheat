package ret.tawny.truthful.utils.math;

import java.util.*;
import java.util.stream.Collectors;

public final class Statistics {

    private Statistics() {}

    public static double getAverage(Collection<? extends Number> data) {
        if (data.isEmpty()) return 0;
        double sum = 0;
        for (Number n : data) sum += n.doubleValue();
        return sum / data.size();
    }

    public static double getVariance(Collection<? extends Number> data) {
        if (data.isEmpty()) return 0;
        double mean = getAverage(data);
        double temp = 0;
        for (Number n : data) {
            temp += Math.pow(n.doubleValue() - mean, 2);
        }
        return temp / data.size();
    }

    public static double getStandardDeviation(Collection<? extends Number> data) {
        return Math.sqrt(getVariance(data));
    }

    // Shannon Entropy: Measures the "randomness" or "information density" of the aim.
    // Low entropy = Robotic/Generated. High entropy = Human/Noise.
    public static double getShannonEntropy(List<? extends Number> data) {
        if (data.isEmpty()) return 0.0;

        Map<Double, Integer> map = new HashMap<>();
        for (Number n : data) {
            double val = n.doubleValue();
            map.put(val, map.getOrDefault(val, 0) + 1);
        }

        double result = 0.0;
        int size = data.size();

        for (Integer count : map.values()) {
            double probability = (double) count / size;
            result -= probability * (Math.log(probability) / Math.log(2));
        }
        return result;
    }

    // Z-Score: Finds outliers (snaps) in a dataset.
    public static List<Double> getZScoreOutliers(List<? extends Number> data, double threshold) {
        List<Double> outliers = new ArrayList<>();
        if (data.isEmpty()) return outliers;

        double mean = getAverage(data);
        double stdDev = getStandardDeviation(data);

        for (Number n : data) {
            double z = (n.doubleValue() - mean) / stdDev;
            if (Math.abs(z) > threshold) {
                outliers.add(z);
            }
        }
        return outliers;
    }

    // IQR (Interquartile Range): Measures statistical dispersion
    public static double getIQR(List<? extends Number> data) {
        if (data.size() < 4) return 0.0;

        List<Double> sorted = data.stream().map(Number::doubleValue).sorted().collect(Collectors.toList());
        int size = sorted.size();

        double q1 = sorted.get(size / 4);
        double q3 = sorted.get((size * 3) / 4);

        return q3 - q1;
    }

    // Jiff (Delta): Calculates the difference between sequential elements
    public static List<Double> getJiffDelta(List<? extends Number> data) {
        List<Double> deltas = new ArrayList<>();
        if (data.size() < 2) return deltas;

        for (int i = 1; i < data.size(); i++) {
            double delta = Math.abs(data.get(i).doubleValue() - data.get(i - 1).doubleValue());
            deltas.add(delta);
        }
        return deltas;
    }

    public static int getDistinct(Collection<? extends Number> data) {
        return (int) data.stream().distinct().count();
    }
}