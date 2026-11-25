package com.theminespirit.core.town;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record TownMap(MapPoint anchor, List<MapPlot> plots, List<MapStreet> streets, List<WallSegment> wall) {
    public TownMap {
        Objects.requireNonNull(anchor, "Якорь карты не может быть null: от него отсчитываются координаты");
        plots = List.copyOf(
                Objects.requireNonNull(plots, "Участки карты не могут быть null: пустая карта — пустой список"));
        streets = List.copyOf(
                Objects.requireNonNull(streets, "Улицы карты не могут быть null: пустая карта — пустой список"));
        wall = List.copyOf(
                Objects.requireNonNull(wall, "Стена карты не может быть null: пустая карта — пустой список"));
    }

    public record MapPoint(int x, int z) {}

    public record MapPlot(String id, String building, int x1, int z1, int x2, int z2, String facing) {
        public MapPlot {
            Objects.requireNonNull(id, "id участка не может быть null: по нему стратег двигает участок");
            Objects.requireNonNull(building, "Постройка участка не может быть null: по ней берётся размер");
            Objects.requireNonNull(facing, "Сторона двери не может быть null: от неё поворот схемы");
        }

        public int width() {
            return Math.abs(x2 - x1) + 1;
        }

        public int depth() {
            return Math.abs(z2 - z1) + 1;
        }

        public int minX() {
            return Math.min(x1, x2);
        }

        public int minZ() {
            return Math.min(z1, z2);
        }

        public Placement.Facing door() {
            return Placement.Facing.valueOf(facing.toUpperCase(Locale.ROOT));
        }
    }

    public record MapStreet(String id, String kind, int x1, int z1, int x2, int z2) {
        public MapStreet {
            Objects.requireNonNull(id, "id улицы не может быть null: по нему видно, что двигают");
            Objects.requireNonNull(kind, "Вид улицы не может быть null: главная или боковая");
        }

        public int width() {
            return Math.abs(x2 - x1) + 1;
        }

        public int depth() {
            return Math.abs(z2 - z1) + 1;
        }

        public int minX() {
            return Math.min(x1, x2);
        }

        public int minZ() {
            return Math.min(z1, z2);
        }

        public int maxX() {
            return Math.max(x1, x2);
        }

        public int maxZ() {
            return Math.max(z1, z2);
        }
    }

    public record WallSegment(int x1, int z1, int x2, int z2) {
        public boolean horizontal() {
            return z1 == z2;
        }

        public int minX() {
            return Math.min(x1, x2);
        }

        public int maxX() {
            return Math.max(x1, x2);
        }

        public int minZ() {
            return Math.min(z1, z2);
        }

        public int maxZ() {
            return Math.max(z1, z2);
        }
    }
}
