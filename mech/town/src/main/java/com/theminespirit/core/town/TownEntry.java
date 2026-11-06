package com.theminespirit.core.town;

import java.util.Locale;
import java.util.Objects;

public record TownEntry(String id, String name, int width, int depth, Quarter quarter, boolean level, boolean rotate) {
    public static final int MAX_SIDE = 64;

    public TownEntry {
        Objects.requireNonNull(id, "id постройки плана не может быть null: по нему этапы берут свой набор");
        Objects.requireNonNull(name, "Подпись постройки не может быть null: её показывает карта");
        Objects.requireNonNull(quarter, "Квартал постройки не может быть null: без квартала задают any");
        if (id.isBlank() || width < 1 || depth < 1 || width > MAX_SIDE || depth > MAX_SIDE) {
            throw new IllegalArgumentException("Постройка плана '" + id + "' " + width + "×" + depth
                    + ": нужен id и стороны 1.." + MAX_SIDE + ", иначе участок не разметить");
        }
    }

    public enum Quarter {
        CENTER,
        NW,
        NE,
        SW,
        SE,
        ANY;

        public static Quarter of(String text) {
            Objects.requireNonNull(text, "Квартал не может быть null");
            try {
                return valueOf(text.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException(
                        "Квартал '" + text + "' неизвестен: допустимы center, nw, ne, sw, se, any", unknown);
            }
        }

        public boolean holds(int dx, int dz) {
            return switch (this) {
                case NW -> dx <= 0 && dz <= 0;
                case NE -> dx >= 0 && dz <= 0;
                case SW -> dx <= 0 && dz >= 0;
                case SE -> dx >= 0 && dz >= 0;
                case CENTER, ANY -> true;
            };
        }
    }
}
