package com.theminespirit.core.scene;

import java.util.Objects;

/**
 * Блок плана сцены поверх рельефа: координаты от центра участка, {@code y} — от базового уровня поверхности.
 * {@code settlement} — блок старой постройки поселения: при постановке он попадает в реестр поставленных духами.
 */
public record SceneBlock(int x, int y, int z, String block, boolean settlement) {
    public SceneBlock {
        Objects.requireNonNull(block, "Блок плана сцены не может быть null: постановка не знала бы, что ставить");
        if (block.isBlank()) {
            throw new IllegalArgumentException("Пустой id блока сцены в (" + x + ", " + y + ", " + z + ")");
        }
    }

    static SceneBlock natural(int x, int y, int z, String block) {
        return new SceneBlock(x, y, z, block, false);
    }

    static SceneBlock settlement(int x, int y, int z, String block) {
        return new SceneBlock(x, y, z, block, true);
    }
}
