package com.theminespirit.core.ui;

public record MapViewport(double centerX, double centerZ, double scale, int left, int top, int width, int height) {
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 24;
    public static final double WHEEL_STEP = 1.25;
    private static final int MARGIN = 8;

    public MapViewport {
        if (width < 1 || height < 1 || !(scale >= MIN_SCALE && scale <= MAX_SCALE)) {
            throw new IllegalArgumentException("Окно карты " + width + "×" + height + " в масштабе " + scale
                    + ": окно хотя бы в пиксель, масштаб " + MIN_SCALE + ".." + MAX_SCALE + " пикселя на блок");
        }
    }

    public static MapViewport fit(TownMapView.Bounds bounds, int left, int top, int width, int height) {
        double spanX = bounds.x2() - bounds.x1() + 1;
        double spanZ = bounds.z2() - bounds.z1() + 1;
        double scale = Math.min((width - 2.0 * MARGIN) / spanX, (height - 2.0 * MARGIN) / spanZ);
        return new MapViewport(
                (bounds.x1() + bounds.x2() + 1) / 2.0,
                (bounds.z1() + bounds.z2() + 1) / 2.0,
                clamp(scale),
                left,
                top,
                width,
                height);
    }

    public double screenX(double worldX) {
        return left + width / 2.0 + (worldX - centerX) * scale;
    }

    public double screenY(double worldZ) {
        return top + height / 2.0 + (worldZ - centerZ) * scale;
    }

    public double worldX(double screenX) {
        return centerX + (screenX - left - width / 2.0) / scale;
    }

    public double worldZ(double screenY) {
        return centerZ + (screenY - top - height / 2.0) / scale;
    }

    public boolean inside(double screenX, double screenY) {
        return screenX >= left && screenX < left + width && screenY >= top && screenY < top + height;
    }

    public MapViewport zoom(double screenX, double screenY, double wheel) {
        double next = clamp(scale * Math.pow(WHEEL_STEP, wheel));
        double anchorX = worldX(screenX);
        double anchorZ = worldZ(screenY);
        double centerAfterX = anchorX - (screenX - left - width / 2.0) / next;
        double centerAfterZ = anchorZ - (screenY - top - height / 2.0) / next;
        return new MapViewport(centerAfterX, centerAfterZ, next, left, top, width, height);
    }

    public MapViewport pan(double dragX, double dragY) {
        return new MapViewport(centerX - dragX / scale, centerZ - dragY / scale, scale, left, top, width, height);
    }

    private static double clamp(double scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }
}
