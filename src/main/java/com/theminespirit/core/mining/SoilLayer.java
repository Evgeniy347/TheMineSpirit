package com.theminespirit.core.mining;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;

public final class SoilLayer {
    public static final int WATER_RADIUS = 10;
    public static final List<String> TURF_BLOCKS =
            List.of("minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium");

    public enum Ground {
        TURF,
        SOIL,
        STONE,
        WATER,
        OPEN,
        OTHER
    }

    public record Cell(int y, Ground ground, Ground above, Ground below, OptionalInt waterNear) {
        public Cell {
            Objects.requireNonNull(ground, "Грунт клетки не может быть null: по нему решается, брать ли её");
            Objects.requireNonNull(above, "Клетка над добычей не может быть null: верхний слой — под открытым верхом");
            Objects.requireNonNull(below, "Клетка под добычей не может быть null: под ней должна остаться земля");
            Objects.requireNonNull(waterNear, "Вода рядом не может быть null: нет воды — пустое значение");
        }
    }

    private SoilLayer() {}

    public static boolean dirt(String block) {
        Objects.requireNonNull(block, "Блок добычи не может быть null");
        String id = block.toLowerCase(Locale.ROOT);
        return "dirt".equals(id) || "minecraft:dirt".equals(id);
    }

    public static boolean takeable(Cell cell) {
        Objects.requireNonNull(cell, "Клетка добычи не может быть null");
        boolean surface = cell.ground() == Ground.TURF && cell.above() == Ground.OPEN;
        boolean soilStays = cell.below() == Ground.SOIL || cell.below() == Ground.TURF;
        return surface && soilStays && aboveWater(cell);
    }

    public static boolean takeableGranular(Cell cell) {
        Objects.requireNonNull(cell, "Клетка добычи не может быть null");
        boolean surface = cell.ground() == Ground.SOIL && cell.above() == Ground.OPEN;
        boolean belowStays = cell.below() == Ground.SOIL || cell.below() == Ground.TURF || cell.below() == Ground.STONE;
        return surface && belowStays && aboveWater(cell);
    }

    private static boolean aboveWater(Cell cell) {
        return cell.waterNear().isEmpty() || cell.y() > cell.waterNear().getAsInt();
    }
}
