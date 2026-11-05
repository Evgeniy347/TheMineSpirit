package com.theminespirit.core.perception;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record Sighting(String kind, String label, BlockCoord pos, String detail) {
    public static final String BLOCK = "block";
    public static final String TREE = "tree";
    public static final String CONTAINER = "container";
    public static final String ENTITY = "entity";
    public static final String AIR = "air";
    public static final String UNLOADED = "unloaded";

    public Sighting {
        requireText(kind, "kind", "вид находки, по нему ИИ выбирает, как её читать");
        Objects.requireNonNull(label, "label не может быть null: находка называет себя (id блока, имя точки)");
        Objects.requireNonNull(pos, "pos не может быть null: находка обязана назвать позицию");
        Objects.requireNonNull(detail, "detail не может быть null: пояснения нет — пустая строка");
    }

    private static void requireText(String value, String field, String purpose) {
        Objects.requireNonNull(value, field + " не может быть null: " + purpose);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Поле " + field + " пусто: " + purpose + " обязано быть непустым");
        }
    }
}
