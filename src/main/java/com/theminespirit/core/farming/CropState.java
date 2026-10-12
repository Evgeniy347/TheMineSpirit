package com.theminespirit.core.farming;

import java.util.Objects;

public record CropState(CropKind kind, int age) {
    public CropState {
        Objects.requireNonNull(kind, "Вид культуры не может быть null: без него неизвестна зрелость посева");
        if (kind == CropKind.ANY) {
            throw new IllegalArgumentException(
                    "Вид культуры не может быть any: any означает «любой вид», а в клетке растёт конкретный");
        }
        if (age < 0 || age > kind.maxAge()) {
            throw new IllegalArgumentException("Возраст " + age + " вне диапазона [0, " + kind.maxAge()
                    + "] для вида " + kind + ": такого значения свойства age у посева не бывает");
        }
    }

    public boolean isMature() {
        return age == kind.maxAge();
    }
}
