package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;

public final class MoveCost {
    public static final int WALK_TICKS = 5;
    public static final int SPRINT_TICKS = 4;
    public static final int SNEAK_TICKS = 15;
    public static final int WATER_TICKS = 10;
    public static final int LADDER_UP_TICKS = 8;
    public static final int LADDER_DOWN_TICKS = 4;
    public static final int JUMP_TICKS = 8;
    public static final int FALL_LANDING_TICKS = 8;
    public static final int FALL_TICKS_PER_BLOCK = 2;

    private MoveCost() {
    }

    public static int costOf(MoveKind move, int height) {
        if (move == null) {
            throw new IllegalArgumentException("Вид движения не может быть null: без него неизвестно, какой тариф брать");
        }
        if (move == MoveKind.FALL) {
            if (height < 0) {
                throw new IllegalArgumentException("Высота падения " + height
                        + " отрицательна: вверх падать нельзя, проверьте знак высоты");
            }
            return FALL_LANDING_TICKS + FALL_TICKS_PER_BLOCK * height;
        }
        return switch (move) {
            case WALK -> WALK_TICKS;
            case WATER -> WATER_TICKS;
            case LADDER_UP -> LADDER_UP_TICKS;
            case LADDER_DOWN -> LADDER_DOWN_TICKS;
            case SNEAK -> SNEAK_TICKS;
            case SPRINT -> SPRINT_TICKS;
            case JUMP -> JUMP_TICKS;
            case FALL -> throw new IllegalStateException("Ветка падения обработана выше: сюда попасть нельзя");
        };
    }

    public static int fallDamage(int height) {
        if (height < 0) {
            throw new IllegalArgumentException("Высота падения " + height
                    + " отрицательна: вверх падать нельзя, проверьте знак высоты");
        }
        return Math.max(0, height - DropProfile.MAX_SAFE_DROP);
    }

    public static int walkTicks(BlockCoord from, BlockCoord to) {
        return (int) Math.ceil(WALK_TICKS * Goal.octile(from, to));
    }
}
