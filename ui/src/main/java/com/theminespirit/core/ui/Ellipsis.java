package com.theminespirit.core.ui;

import java.util.Objects;
import java.util.function.ToIntFunction;

public final class Ellipsis {
    public static final String MARK = "…";

    private Ellipsis() {}

    public record Fitted(String text, boolean trimmed) {
        public Fitted {
            Objects.requireNonNull(text, "Обрезанный текст не может быть null: рисуется как есть");
        }
    }

    public static Fitted fit(String text, int width, ToIntFunction<String> measure) {
        Objects.requireNonNull(text, "Текст для обрезки не может быть null: пустую строку задают явно");
        Objects.requireNonNull(measure, "Измеритель ширины не может быть null: без него не узнать, влезает ли текст");
        if (text.isEmpty() || width <= 0) {
            return new Fitted("", !text.isEmpty());
        }
        if (measure.applyAsInt(text) <= width) {
            return new Fitted(text, false);
        }
        if (measure.applyAsInt(MARK) > width) {
            return new Fitted("", true);
        }
        int codePoints = text.codePointCount(0, text.length());
        int low = 0;
        int high = codePoints;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            String prefix = text.substring(0, text.offsetByCodePoints(0, middle));
            if (measure.applyAsInt(prefix + MARK) <= width) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return new Fitted(text.substring(0, text.offsetByCodePoints(0, low)) + MARK, true);
    }
}
