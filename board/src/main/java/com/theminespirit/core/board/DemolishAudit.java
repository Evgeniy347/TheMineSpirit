package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DemolishAudit {
    private static final int SHOWN = 4;

    private DemolishAudit() {}

    public static Optional<String> stillStanding(int planned, List<BlockCoord> standing) {
        Objects.requireNonNull(
                standing, "Список оставшихся блоков не может быть null: по нему решают, убран ли разбор");
        if (planned < 0 || standing.size() > planned) {
            throw new IllegalArgumentException("Аудит разбора: оставшихся " + standing.size() + " при плане " + planned
                    + " — остаток не может быть отрицательным или больше плана, проверьте подсчёт клеток");
        }
        if (planned == 0) {
            return Optional.of("разбор закрыт без факта мира: в плане 0 блоков — сверять нечего");
        }
        if (standing.isEmpty()) {
            return Optional.empty();
        }
        List<BlockCoord> shown = standing.stream().limit(SHOWN).toList();
        return Optional.of("разбор закрыт без факта мира: не убрано " + standing.size() + " из " + planned
                + ", остались: " + shown + (standing.size() > SHOWN ? " и ещё " + (standing.size() - SHOWN) : ""));
    }
}
