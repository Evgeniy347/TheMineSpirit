package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class QuarryGrid {
    public static final int STEP = 96;
    public static final int ANGLES = 16;
    private static final double FULL_TURN = Math.PI * 2;

    private QuarryGrid() {}

    public static List<BlockCoord> points(BlockCoord base, int min, int max) {
        Objects.requireNonNull(base, "Базовая точка не может быть null: кольца считаются от поселения");
        if (min < 0 || max < min) {
            throw new IllegalArgumentException(
                    "Диапазон поиска " + min + ".." + max + ": минимум неотрицателен и не больше максимума");
        }
        Set<BlockCoord> points = new LinkedHashSet<>();
        if (min == 0) {
            points.add(new BlockCoord(base.x(), base.y(), base.z()));
        }
        for (int distance = Math.max(min, STEP); distance <= max; distance += STEP) {
            for (int index = 0; index < ANGLES; index++) {
                double angle = FULL_TURN * index / ANGLES;
                int x = base.x() + (int) Math.round(Math.cos(angle) * distance);
                int z = base.z() + (int) Math.round(Math.sin(angle) * distance);
                int manhattan = Math.abs(x - base.x()) + Math.abs(z - base.z());
                if (manhattan < min || manhattan > max) {
                    continue;
                }
                points.add(new BlockCoord(x, base.y(), z));
            }
        }
        return List.copyOf(new ArrayList<>(points));
    }
}
