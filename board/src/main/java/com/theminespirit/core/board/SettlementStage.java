package com.theminespirit.core.board;

import java.util.Map;
import java.util.Objects;

public final class SettlementStage {
    private SettlementStage() {}

    public static int of(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: этап читается по построенному");
        return rules().stageOf(ledger);
    }

    public static int of(StageWorld world) {
        Objects.requireNonNull(world, "Мир этапа не может быть null: по нему видно, что стоит на самом деле");
        return rules().stageOf(world);
    }

    public static Map<String, Integer> missing(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: остаток этапа читается по построенному");
        return rules().missingOf(ledger);
    }

    public static Map<String, Integer> missing(StageWorld world) {
        Objects.requireNonNull(world, "Мир этапа не может быть null: по нему видно, что стоит на самом деле");
        return rules().missingOf(world);
    }

    public static Map<String, Integer> missing(StageWorld world, int stage) {
        Objects.requireNonNull(world, "Мир этапа не может быть null: по нему видно, что стоит на самом деле");
        return rules().missingOf(world, stage);
    }

    public static StageGate gate(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: гейт этапа читается по нему");
        return new StageGate(of(ledger), missing(ledger));
    }

    public static StageGate gate(StageWorld world) {
        Objects.requireNonNull(world, "Мир этапа не может быть null: гейт этапа читается по нему");
        return new StageGate(of(world), missing(world));
    }

    public static StageGate gate(StageWorld world, int stage) {
        Objects.requireNonNull(world, "Мир этапа не может быть null: гейт этапа читается по нему");
        return new StageGate(stage, missing(world, stage));
    }

    public static boolean allows(BoardCase boardCase, int level, BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: этап читается по построенному");
        return allows(boardCase, level, of(ledger));
    }

    public static boolean allows(BoardCase boardCase, int level, int stage) {
        Objects.requireNonNull(boardCase, "Кейс постройки не может быть null: без него неизвестен её уровень");
        if (stage < 1) {
            throw new IllegalArgumentException("Этап " + stage + ": этапы считаются с первого");
        }
        if (level <= 1 || !BuildingLevels.hasLevels(boardCase)) {
            return true;
        }
        return level <= stage;
    }

    private static StageRules rules() {
        return StageRules.current();
    }
}
