package com.theminespirit.core.board;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SettlementStage {
    private SettlementStage() {}

    public static int of(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: этап читается по построенному");
        return rules().stageOf(ledger);
    }

    public static int of(BoardLedger ledger, StageWorld world) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: этап читается по построенному");
        Objects.requireNonNull(world, "Мир этапа не может быть null: по нему видно, что стоит на самом деле");
        StageRules rules = rules();
        for (StageRules.Stage stage : rules.stages()) {
            if (!missing(rules, ledger, stage, world).isEmpty()) {
                return stage.stage();
            }
        }
        return rules.stages().size() + 1;
    }

    public static Map<String, Integer> missing(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: остаток этапа читается по построенному");
        return rules().missingOf(ledger);
    }

    public static Map<String, Integer> missing(BoardLedger ledger, StageWorld world) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: остаток этапа читается по построенному");
        Objects.requireNonNull(world, "Мир этапа не может быть null: по нему видно, что стоит на самом деле");
        StageRules rules = rules();
        int stage = of(ledger, world);
        if (stage > rules.stages().size()) {
            return Map.of();
        }
        return missing(rules, ledger, rules.stages().get(stage - 1), world);
    }

    public static StageGate gate(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: гейт этапа читается по нему");
        return new StageGate(of(ledger), missing(ledger));
    }

    public static StageGate gate(BoardLedger ledger, StageWorld world) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: гейт этапа читается по нему");
        Objects.requireNonNull(world, "Мир этапа не может быть null: по нему видно, что стоит на самом деле");
        return new StageGate(of(ledger, world), missing(ledger, world));
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

    private static Map<String, Integer> missing(
            StageRules rules, BoardLedger ledger, StageRules.Stage stage, StageWorld world) {
        Map<String, Integer> missing = new LinkedHashMap<>(rules.missingOf(ledger, stage.stage()));
        stage.buildings().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> !world.built(entry.getKey(), entry.getValue()))
                .forEach(entry -> missing.putIfAbsent(entry.getKey().name(), entry.getValue()));
        stage.requires().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> !world.builtPlan(entry.getKey(), entry.getValue()))
                .forEach(entry -> missing.putIfAbsent(entry.getKey(), entry.getValue()));
        return Map.copyOf(missing);
    }

    private static StageRules rules() {
        return StageRules.current();
    }
}
