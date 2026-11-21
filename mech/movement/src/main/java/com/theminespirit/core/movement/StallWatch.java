package com.theminespirit.core.movement;

public final class StallWatch {
    private static final double MIN_SHIFT = 0.7;
    private static final double MIN_PATH = 0.6;

    private final double[] xs;
    private final double[] zs;
    private final double[] travelled;
    private int size;
    private int head;
    private double total;
    private double lastX;
    private double lastZ;
    private boolean primed;

    public StallWatch(int window) {
        if (window < 2) {
            throw new IllegalArgumentException(
                    "Окно топтания " + window + " меньше двух: по одной точке смещение не измерить");
        }
        this.xs = new double[window];
        this.zs = new double[window];
        this.travelled = new double[window];
    }

    public boolean observe(double x, double z) {
        if (primed) {
            total += Math.hypot(x - lastX, z - lastZ);
        }
        primed = true;
        lastX = x;
        lastZ = z;
        if (size < xs.length) {
            xs[size] = x;
            zs[size] = z;
            travelled[size] = total;
            size++;
            return false;
        }
        int oldest = head;
        double shift = Math.hypot(x - xs[oldest], z - zs[oldest]);
        double path = total - travelled[oldest];
        xs[oldest] = x;
        zs[oldest] = z;
        travelled[oldest] = total;
        head = (oldest + 1) % xs.length;
        return shift < MIN_SHIFT && path >= MIN_PATH;
    }

    public void reset() {
        size = 0;
        head = 0;
        total = 0;
        primed = false;
    }
}
