package com.theminespirit.core.script.api;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

public record CrossingSpan(
        int width,
        int deepest,
        @Nullable BlockCoord dry,
        @Nullable BlockCoord wet) {
    public CrossingSpan {
        if (width < 0 || deepest < 0) {
            throw new IllegalArgumentException("Ширина " + width + " и глубина " + deepest
                    + " не могут быть отрицательными: это длины по линии переправы");
        }
    }

    public boolean crosses() {
        return width > 0 && wet != null;
    }

    public static CrossingSpan of(
            BlockCoord from, BlockCoord to, Predicate<BlockCoord> water, ToIntFunction<BlockCoord> depth) {
        Objects.requireNonNull(water, "Признак воды не может быть null: без него линия не читается");
        return walk(from, to, cell -> water.test(cell) ? cell : null, depth);
    }

    public static CrossingSpan ofSurface(
            BlockCoord from,
            BlockCoord to,
            Function<BlockCoord, @Nullable BlockCoord> surface,
            ToIntFunction<BlockCoord> depth) {
        Objects.requireNonNull(surface, "Поиск поверхности воды не может быть null: без него не видно переправы");
        return walk(from, to, surface, depth);
    }

    private static CrossingSpan walk(
            BlockCoord from,
            BlockCoord to,
            Function<BlockCoord, @Nullable BlockCoord> wetAt,
            ToIntFunction<BlockCoord> depth) {
        Objects.requireNonNull(from, "Начало линии переправы не может быть null: с него читается вода");
        Objects.requireNonNull(to, "Цель линии переправы не может быть null: к ней ищется вода");
        Objects.requireNonNull(depth, "Глубина воды не может быть null: без неё не видно заплыва");
        int x0 = from.x();
        int z0 = from.z();
        int x1 = to.x();
        int z1 = to.z();
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = Integer.compare(x1, x0);
        int sz = Integer.compare(z1, z0);
        int err = dx - dz;
        int x = x0;
        int z = z0;
        int run = 0;
        int widest = 0;
        int deepest = 0;
        BlockCoord dry = null;
        BlockCoord wet = null;
        BlockCoord previous = null;
        while (true) {
            BlockCoord cell = new BlockCoord(x, from.y(), z);
            BlockCoord wetCell = wetAt.apply(cell);
            if (wetCell != null) {
                run++;
                widest = Math.max(widest, run);
                deepest = Math.max(deepest, depth.applyAsInt(wetCell));
                if (wet == null) {
                    wet = wetCell;
                    dry = previous;
                }
            } else {
                run = 0;
            }
            previous = cell;
            if (x == x1 && z == z1) {
                break;
            }
            int doubled = 2 * err;
            if (doubled > -dz) {
                err -= dz;
                x += sx;
            }
            if (doubled < dx) {
                err += dx;
                z += sz;
            }
        }
        return new CrossingSpan(widest, deepest, dry, wet);
    }
}
