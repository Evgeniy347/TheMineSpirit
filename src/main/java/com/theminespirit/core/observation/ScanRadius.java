package com.theminespirit.core.observation;

public record ScanRadius(int value) {
    public static final int MIN = 1;
    public static final int MAX = 32;
    public static final ScanRadius DEFAULT = new ScanRadius(8);

    public ScanRadius {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("Радиус сканирования " + value + " вне диапазона [" + MIN + ", " + MAX
                    + "]: больший скан пересобирает мир по блокам каждый вызов и кладёт серверный тик");
        }
    }

    public static ScanRadius of(int value) {
        return new ScanRadius(value);
    }
}
