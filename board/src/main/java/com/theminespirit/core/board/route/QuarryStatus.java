package com.theminespirit.core.board.route;

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record QuarryStatus(
        RouteResource resource,
        @Nullable QuarrySite site,
        int reserve,
        int dug,
        int remainingPercent,
        State state,
        boolean pathBuilt,
        boolean routeBuilt) {
    public static final int LOW_PERCENT = 10;
    private static final int PERCENT = 100;

    public enum State {
        ACTIVE,
        LOW,
        EMPTY,
        UNSET
    }

    public QuarryStatus {
        Objects.requireNonNull(resource, "Ресурс статуса не может быть null: у статуса один ресурс");
        Objects.requireNonNull(state, "Состояние места добычи не может быть null: карта и стратег читают его");
        if (reserve < 0 || dug < 0) {
            throw new IllegalArgumentException("Запас " + reserve + ", выкопано " + dug
                    + ": отрицательный запас — ошибка оценки, а не иссякший карьер");
        }
        if (remainingPercent < 0 || remainingPercent > PERCENT) {
            throw new IllegalArgumentException("Остаток " + remainingPercent + "%: остаток считается в 0..100");
        }
        if (state == State.UNSET && site != null) {
            throw new IllegalArgumentException("Состояние unset с точкой " + site.point()
                    + ": не заданное место не имеет координат — иначе карта нарисует несуществующий карьер");
        }
        if (state != State.UNSET && site == null) {
            throw new IllegalArgumentException("Состояние " + state + " без точки: место задано, но потеряно");
        }
    }

    public static QuarryStatus unset(RouteResource resource) {
        return new QuarryStatus(resource, null, 0, 0, 0, State.UNSET, false, false);
    }

    public static QuarryStatus of(QuarrySite site, int reserve, boolean pathBuilt, boolean routeBuilt) {
        Objects.requireNonNull(site, "Место добычи не может быть null: статус считается по установленному месту");
        int current = Math.max(0, reserve);
        int percent = site.initialReserve() == 0 ? 0 : Math.min(PERCENT, current * PERCENT / site.initialReserve());
        State state;
        if (current == 0 || site.initialReserve() == 0) {
            state = State.EMPTY;
        } else if (percent < LOW_PERCENT) {
            state = State.LOW;
        } else {
            state = State.ACTIVE;
        }
        return new QuarryStatus(
                site.resource(),
                site,
                current,
                Math.max(0, site.initialReserve() - current),
                percent,
                state,
                pathBuilt,
                routeBuilt);
    }

    public boolean low() {
        return state == State.LOW || state == State.EMPTY;
    }

    public String stateName() {
        return state.name().toLowerCase(Locale.ROOT);
    }
}
