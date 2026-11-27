package com.theminespirit.core.board.audit;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record Expectation(BlockCoord cell, Wanted wanted, SourceRef source, Access access) {
    public Expectation {
        Objects.requireNonNull(cell, "Клетка ожидания не может быть null: без адреса сверять нечего");
        Objects.requireNonNull(wanted, "Ожидание не может быть null: план обязан сказать, что должно стоять");
        Objects.requireNonNull(source, "Источник ожидания не может быть null: иначе дефект не приписать");
        Objects.requireNonNull(access, "Право ожидания не может быть null: иначе чужое попадёт в работу");
    }

    public boolean groundLayer() {
        return wanted.kind() == Wanted.Kind.GROUND;
    }
}
