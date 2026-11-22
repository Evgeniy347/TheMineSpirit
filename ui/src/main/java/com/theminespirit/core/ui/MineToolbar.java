package com.theminespirit.core.ui;

public record MineToolbar(int minY, int maxY, int y) {
    public MineToolbar {
        if (minY > maxY) {
            throw new IllegalArgumentException(
                    "Диапазон высот шахты " + minY + ".." + maxY + " перевёрнут: верх не может быть ниже низа");
        }
        if (y < minY || y > maxY) {
            throw new IllegalArgumentException(
                    "Слой " + y + " вне диапазона " + minY + ".." + maxY + ": срез не рисуется");
        }
    }

    public static MineToolbar of(int minY, int maxY, int y) {
        return new MineToolbar(minY, maxY, Math.max(minY, Math.min(maxY, y)));
    }

    public int layers() {
        return maxY - minY + 1;
    }

    public int layer() {
        return maxY - y + 1;
    }

    public MineToolbar step(int delta) {
        return new MineToolbar(minY, maxY, Math.max(minY, Math.min(maxY, y + delta)));
    }

    public double fraction() {
        return maxY == minY ? 0 : (double) (maxY - y) / (maxY - minY);
    }

    public int yAt(double fraction) {
        double clamped = Math.max(0, Math.min(1, fraction));
        return (int) Math.round(maxY - clamped * (maxY - minY));
    }
}
