package com.theminespirit.core.ui;

import java.util.Objects;

public final class MineStyle {
    public static final int BACKGROUND = 0xFF15130F;
    public static final int RECT_BORDER = 0xFFE0D6C2;
    public static final int HOVER_BORDER = 0xFFFFF3B0;
    public static final int TEXT = 0xFFFFFFFF;
    private static final int PASSAGE = 0xFF3B342B;
    private static final int DESCENT = 0xFF9A8F7B;
    private static final int FLOOR = 0xFF6E5B41;
    private static final int ORE = 0xFFD9A441;
    private static final int LUCK_ORE = 0xFF54D6C8;
    private static final int WATER = 0xFF3A6AD6;
    private static final int LAVA = 0xFFE2672B;
    public static final int DIM = 0xFF24211C;

    private MineStyle() {}

    public static boolean ore(MineSnapshot.Kind kind) {
        Objects.requireNonNull(kind, "Вид клетки среза шахты не может быть null");
        return kind == MineSnapshot.Kind.ORE || kind == MineSnapshot.Kind.LUCK_ORE;
    }

    public static int fill(MineSnapshot.Kind kind, boolean oreOnly) {
        Objects.requireNonNull(kind, "Вид клетки среза шахты не может быть null");
        if (oreOnly && !ore(kind)) {
            return DIM;
        }
        return fill(kind);
    }

    public static int fill(MineSnapshot.Kind kind) {
        Objects.requireNonNull(kind, "Вид клетки среза шахты не может быть null");
        return switch (kind) {
            case PASSAGE -> PASSAGE;
            case DESCENT -> DESCENT;
            case FLOOR -> FLOOR;
            case ORE -> ORE;
            case LUCK_ORE -> LUCK_ORE;
            case WATER -> WATER;
            case LAVA -> LAVA;
        };
    }
}
