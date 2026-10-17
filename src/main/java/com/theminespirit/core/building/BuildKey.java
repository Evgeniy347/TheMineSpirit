package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record BuildKey(String file, BlockCoord origin, BuildRotation rotation) {
    public BuildKey {
        Objects.requireNonNull(file, "Файл схемы не может быть null: без него отряды разных схем делят клетки");
        Objects.requireNonNull(origin, "Якорь не может быть null: без него стройки в разных местах делят клетки");
        Objects.requireNonNull(rotation,
                "Поворот не может быть null: одна схема в разных поворотах занимает разные клетки");
    }
}
