package com.theminespirit.core.board.audit;

import java.util.Objects;

public record Wanted(Kind kind, String id, String state, int level) {
    public enum Kind {
        AIR,
        BLOCK,
        GROUND
    }

    public static final Wanted AIR = new Wanted(Kind.AIR, "", "", 0);
    public static final int NO_LEVEL = Integer.MIN_VALUE;

    public Wanted {
        Objects.requireNonNull(kind, "Вид ожидания не может быть null: без него не выбрать правило сверки");
        Objects.requireNonNull(id, "Id ожидания не может быть null: отсутствие блока — пустая строка, не null");
        Objects.requireNonNull(state, "Свойства ожидания не могут быть null: пустые — пустая строка");
        if (kind == Kind.BLOCK && id.isBlank()) {
            throw new IllegalArgumentException("Ожидание BLOCK с пустым id: сверка блока без имени ложна всегда, "
                    + "и дух получит задачу на клетку, которой нет в плане");
        }
        if (kind == Kind.GROUND && level == NO_LEVEL) {
            throw new IllegalArgumentException("Ожидание GROUND без уровня: грунт сверяется по опорной высоте "
                    + "поселения, без неё группа земли не соберётся");
        }
    }

    public static Wanted block(String id) {
        return block(id, "");
    }

    public static Wanted block(String id, String state) {
        Objects.requireNonNull(id, "Id блока ожидания не может быть null: блок без имени не сверить");
        Objects.requireNonNull(state, "Свойства блока не могут быть null: без свойств — пустая строка");
        return new Wanted(Kind.BLOCK, id, state, 0);
    }

    public static Wanted of(String blockWithState) {
        Objects.requireNonNull(blockWithState, "Строка блока не может быть null: из неё делают ожидание");
        int bracket = blockWithState.indexOf('[');
        if (bracket < 0) {
            return block(blockWithState, "");
        }
        return block(
                blockWithState.substring(0, bracket),
                blockWithState.substring(bracket + 1).replace("]", ""));
    }

    public static Wanted ground(int level) {
        return new Wanted(Kind.GROUND, "dirt", "", level);
    }

    public static Wanted air() {
        return AIR;
    }

    public String text() {
        return state.isBlank() ? id : id + "[" + state + "]";
    }
}
