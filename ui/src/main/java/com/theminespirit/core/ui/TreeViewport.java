package com.theminespirit.core.ui;

public record TreeViewport(int areaWidth, int areaHeight, int contentWidth, int contentHeight, int steps) {
    public static final double STEP = 1.25;
    public static final int MAX_STEPS = 5;
    private static final int PAD_X = 24;
    private static final int PAD_Y = 12;

    public TreeViewport {
        if (areaWidth < 1 || areaHeight < 1) {
            throw new IllegalArgumentException(
                    "Область древа " + areaWidth + "×" + areaHeight + ": рамка не рисуется в пустоте");
        }
        if (contentWidth < 1 || contentHeight < 1) {
            throw new IllegalArgumentException(
                    "Содержимое древа " + contentWidth + "×" + contentHeight + ": пустую раскладку не вписать");
        }
        if (steps < 0 || steps > MAX_STEPS) {
            throw new IllegalArgumentException(
                    "Шаг зума " + steps + " вне 0.." + MAX_STEPS + ": дальше дерево не читается");
        }
    }

    public static TreeViewport fit(int areaWidth, int areaHeight, int contentWidth, int contentHeight) {
        return new TreeViewport(areaWidth, areaHeight, contentWidth, contentHeight, 0);
    }

    public double fitScale() {
        return Math.min(
                Math.min((areaWidth - PAD_X) / (double) contentWidth, (areaHeight - PAD_Y) / (double) contentHeight),
                1);
    }

    public double scale() {
        return fitScale() * Math.pow(STEP, steps);
    }

    public TreeViewport zoomIn() {
        return new TreeViewport(areaWidth, areaHeight, contentWidth, contentHeight, Math.min(MAX_STEPS, steps + 1));
    }

    public TreeViewport zoomOut() {
        return new TreeViewport(areaWidth, areaHeight, contentWidth, contentHeight, Math.max(0, steps - 1));
    }

    public int percent() {
        return (int) Math.round(scale() * 100);
    }

    public int scaledWidth() {
        return (int) Math.ceil(contentWidth * scale());
    }

    public int scaledHeight() {
        return (int) Math.ceil(contentHeight * scale());
    }

    public static double clampOffset(double offset, int area, int content) {
        if (content <= area) {
            return 0;
        }
        return Math.max(area - content, Math.min(0, offset));
    }
}
