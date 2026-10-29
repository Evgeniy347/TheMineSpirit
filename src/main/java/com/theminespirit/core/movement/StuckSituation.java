package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class StuckSituation {
    public static final int PASSAGE_HEIGHT = 2;

    public enum Kind {
        FREE,
        WALLED,
        DEEP_PIT
    }

    public record Verdict(Kind kind, @Nullable BlockCoord passage, PitEscape.@Nullable Exit pit) {
        public Verdict {
            Objects.requireNonNull(kind, "Вид застревания не может быть null: по нему решают, спасать ли духа");
            if (kind == Kind.FREE && passage == null) {
                throw new IllegalArgumentException("Свободный дух назван без прохода: свободный вид обязан "
                        + "называть сторону с двумя блоками воздуха");
            }
            if (kind == Kind.DEEP_PIT && pit == null) {
                throw new IllegalArgumentException(
                        "Яма названа без выхода: правило подъёма не сможет " + "подсказать, куда ставить ступени");
            }
        }

        public boolean stuck() {
            return kind != Kind.FREE;
        }
    }

    public interface Grid {
        boolean passable(BlockCoord cell);

        default boolean swimmable(BlockCoord cell) {
            return false;
        }

        int floorY(int x, int z);
    }

    private static final List<int[]> SIDES =
            List.of(new int[] {1, 0}, new int[] {-1, 0}, new int[] {0, 1}, new int[] {0, -1});

    private StuckSituation() {}

    public static Verdict detect(BlockCoord feet, Grid grid) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: от них считается проход");
        Objects.requireNonNull(grid, "Сетка клеток не может быть null: без неё прохода не видно");
        for (int[] side : SIDES) {
            BlockCoord lower = new BlockCoord(feet.x() + side[0], feet.y(), feet.z() + side[1]);
            BlockCoord upper = new BlockCoord(lower.x(), lower.y() + 1, lower.z());
            if (open(grid, lower) && open(grid, upper)) {
                return new Verdict(Kind.FREE, lower, null);
            }
        }
        PitEscape.Exit pit = PitEscape.exit((dx, dz) -> grid.floorY(feet.x() + dx, feet.z() + dz))
                .orElse(null);
        if (pit != null) {
            return new Verdict(Kind.DEEP_PIT, null, pit);
        }
        return new Verdict(Kind.WALLED, null, null);
    }

    private static boolean open(Grid grid, BlockCoord cell) {
        return grid.passable(cell) || grid.swimmable(cell);
    }
}
