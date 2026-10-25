package com.theminespirit.core.scene;

import java.util.List;
import java.util.Objects;

public record ScenePlan(
        long seed, String wood, int radius, int bottom, List<Integer> heights, List<SceneBlock> blocks) {
    public ScenePlan {
        Objects.requireNonNull(wood, "Порода сцены не может быть null: деревья и старая ограда — из неё");
        Objects.requireNonNull(heights, "Высоты рельефа не могут быть null: без них колонки не поставить");
        int side = 2 * radius + 1;
        if (radius < 1 || heights.size() != side * side) {
            throw new IllegalArgumentException(
                    "Высот " + heights.size() + " на радиус " + radius + ": нужно " + side * side + " колонок");
        }
        heights = List.copyOf(heights);
        blocks = List.copyOf(blocks);
    }

    public int height(int x, int z) {
        if (Math.abs(x) > radius || Math.abs(z) > radius) {
            throw new IllegalArgumentException(
                    "Колонка (" + x + ", " + z + ") вне сцены радиуса " + radius + ": высоты там нет");
        }
        return heights.get((x + radius) * (2 * radius + 1) + z + radius);
    }
}
