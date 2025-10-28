package ret.tawny.truthful.utils.math;

import java.util.ArrayDeque;
import java.util.Queue;

public final class RollingAverage {
    private final Queue<Double> samples = new ArrayDeque<>();
    private final int size;
    private double total = 0.0;

    public RollingAverage(int size) {
        this.size = size;
    }

    public void add(double x) {
        total += x;
        samples.add(x);
        if (samples.size() > size) {
            total -= samples.poll();
        }
    }

    public double getAverage() {
        if (samples.isEmpty()) {
            return 0.0;
        }
        return total / samples.size();
    }

    public boolean isFull() {
        return samples.size() >= size;
    }
}