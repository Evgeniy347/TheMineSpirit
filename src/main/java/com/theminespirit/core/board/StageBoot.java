package com.theminespirit.core.board;

import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class StageBoot {
    private StageBoot() {}

    public static boolean apply(Optional<StageSite> site, Optional<SettlementPlot> plot, Consumer<String> action) {
        Objects.requireNonNull(site, "Место этапа не может быть null: пустое — Optional.empty()");
        Objects.requireNonNull(plot, "Участок не может быть null: пустой — Optional.empty()");
        Objects.requireNonNull(action, "Действие старта не может быть null: иначе гейт не поставить");
        if (site.isEmpty() || plot.isEmpty()) {
            return false;
        }
        action.accept(plot.orElseThrow().wood());
        return true;
    }
}
