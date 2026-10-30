package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.Map;
import java.util.Objects;

public final class SettlementStage {
    public static final int FIRST_STAGE_LOGS = 64;
    private static final int MAX_SHIFT = 16;

    private SettlementStage() {}

    public static int of(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: этап читается по построенному");
        return rules().stageOf(ledger);
    }

    public static Map<String, Integer> missing(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: остаток этапа читается по построенному");
        return rules().missingOf(ledger);
    }

    public static boolean allows(BoardCase boardCase, int level, BoardLedger ledger) {
        Objects.requireNonNull(boardCase, "Кейс постройки не может быть null: без него неизвестен её уровень");
        if (level <= 1 || !BuildingLevels.hasLevels(boardCase)) {
            return true;
        }
        return level <= of(ledger);
    }

    public static int logCap(int stage) {
        if (stage < 1) {
            throw new IllegalArgumentException("Этап " + stage + ": этапы считаются с первого");
        }
        return FIRST_STAGE_LOGS << Math.min(stage - 1, MAX_SHIFT);
    }

    private static StageRules rules() {
        return DataTables.INSTANCE.stages().current();
    }
}
