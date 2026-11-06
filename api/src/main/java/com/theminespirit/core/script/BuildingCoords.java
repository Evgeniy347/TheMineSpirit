package com.theminespirit.core.script;

final class BuildingCoords {
    private BuildingCoords() {}

    static void checkX(int value) {
        if (value < -CatalogLimits.WORLD_HORIZONTAL_LIMIT || value > CatalogLimits.WORLD_HORIZONTAL_LIMIT) {
            throw new IllegalArgumentException("Координата x=" + value + " вне диапазона [-"
                    + CatalogLimits.WORLD_HORIZONTAL_LIMIT + ", " + CatalogLimits.WORLD_HORIZONTAL_LIMIT
                    + "]: точка вне мира, ставить там нечего");
        }
    }

    static void checkZ(int value) {
        if (value < -CatalogLimits.WORLD_HORIZONTAL_LIMIT || value > CatalogLimits.WORLD_HORIZONTAL_LIMIT) {
            throw new IllegalArgumentException("Координата z=" + value + " вне диапазона [-"
                    + CatalogLimits.WORLD_HORIZONTAL_LIMIT + ", " + CatalogLimits.WORLD_HORIZONTAL_LIMIT
                    + "]: точка вне мира, ставить там нечего");
        }
    }

    static void checkY(int value) {
        if (value < CatalogLimits.WORLD_Y_MIN || value > CatalogLimits.WORLD_Y_MAX) {
            throw new IllegalArgumentException("Координата y=" + value + " вне диапазона [" + CatalogLimits.WORLD_Y_MIN
                    + ", " + CatalogLimits.WORLD_Y_MAX + "]: точка вне высоты мира");
        }
    }
}
