package com.theminespirit.core.husbandry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record AnimalRef(
        String id,
        Species species,
        boolean baby,
        boolean breedReady,
        boolean woolReady,
        BlockCoord pos,
        boolean inPen) {
    public AnimalRef {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "id животного пустое: без него планировщик не отличит цели друг от друга");
        }
        Objects.requireNonNull(
                species, "Вид животного не может быть null: вид решается один раз, дальше только Species");
        Objects.requireNonNull(pos, "Позиция животного не может быть null: по ней считается близость к духу и загону");
    }
}
