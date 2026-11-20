package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;

public final class PillarHeadroom {
    private PillarHeadroom() {}

    public interface View {
        String block(BlockCoord at);

        boolean replaceable(BlockCoord at);

        boolean fluid(BlockCoord at);
    }

    public record Blockage(BlockCoord cell, String block) {}

    public static Optional<Blockage> firstBlocked(View view, BlockCoord base, int height) {
        Objects.requireNonNull(view, "Мир столба не может быть null: без него неизвестен потолок");
        Objects.requireNonNull(base, "Основание столба не может быть null: от него считается высота");
        if (height < 1) {
            throw new IllegalArgumentException("Высота столба " + height + " меньше 1: проверять нечего");
        }
        for (int i = 0; i <= height; i++) {
            BlockCoord cell = new BlockCoord(base.x(), base.y() + i, base.z());
            if (view.fluid(cell) || !view.replaceable(cell)) {
                return Optional.of(new Blockage(cell, view.block(cell)));
            }
        }
        return Optional.empty();
    }
}
