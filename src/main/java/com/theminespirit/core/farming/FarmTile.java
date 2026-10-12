package com.theminespirit.core.farming;

import java.util.Objects;
import java.util.Optional;

public record FarmTile(Ground ground, Above above, Optional<CropState> crop) {
    public FarmTile {
        Objects.requireNonNull(ground, "Земля клетки не может быть null: без неё неизвестно, что под посевом");
        Objects.requireNonNull(above, "Верх клетки не может быть null: без него неизвестно, что над землёй");
        Objects.requireNonNull(crop, "Посев не может быть null: отсутствие посева задают пустым Optional");
        if (above == Above.CROP && crop.isEmpty()) {
            throw new IllegalArgumentException(
                    "Верх CROP без посева: раз над землёй посев, нужны его вид и возраст для проверки зрелости");
        }
        if (above != Above.CROP && crop.isPresent()) {
            throw new IllegalArgumentException("Посев " + crop.orElseThrow() + " при верхе " + above
                    + ": посев растёт только над землёй с верхом CROP, иначе планировщик увидит урожай там, где его нет");
        }
    }
}
