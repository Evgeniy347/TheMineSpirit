package com.theminespirit.core.board;

import java.util.Objects;

/**
 * Этап поселения для пределов запаса (владелец 23.10, рекомендация QUESTIONS-53): старший достроенный уровень дома
 * по журналу доски, до первого дома — этап 1. Предел брёвен — 64 на первом этапе и вдвое больше на каждом
 * следующем: рубка не копит брёвна сверх нужного этапу.
 */
public final class SettlementStage {
    public static final int FIRST_STAGE_LOGS = 64;
    private static final int MAX_SHIFT = 16;

    private SettlementStage() {}

    public static int of(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: этап читается по достроенным уровням дома");
        int stage = 1;
        for (int level = 1; level <= BuildingLevels.maxLevel(BoardCase.BUILD_HOUSE); level++) {
            if (ledger.has(BoardCase.BUILD_HOUSE, level)) {
                stage = level;
            }
        }
        return stage;
    }

    public static int logCap(int stage) {
        if (stage < 1) {
            throw new IllegalArgumentException("Этап " + stage + ": этапы считаются с первого");
        }
        return FIRST_STAGE_LOGS << Math.min(stage - 1, MAX_SHIFT);
    }
}
