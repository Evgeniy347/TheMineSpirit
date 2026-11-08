package com.theminespirit.core.board;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.TableSlot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public record StageRules(List<Stage> stages, Map<BoardCase, List<String>> schemes, Map<String, BoardCase> planCases) {
    public static final String RESOURCE = "data/theminespirit/board/stages.json";

    private static final TableSlot<StageRules> SOURCE = new TableSlot<>(StageRules::load);

    public record Stage(
            int stage,
            Map<BoardCase, Integer> buildings,
            List<String> plan,
            Map<String, Integer> requires,
            Map<String, Integer> optional) {
        public Stage(int stage, Map<BoardCase, Integer> buildings) {
            this(stage, buildings, List.of(), Map.of(), Map.of());
        }

        public Stage(int stage, Map<BoardCase, Integer> buildings, List<String> plan) {
            this(stage, buildings, plan, Map.of(), Map.of());
        }

        public Stage(int stage, Map<BoardCase, Integer> buildings, List<String> plan, Map<String, Integer> requires) {
            this(stage, buildings, plan, requires, Map.of());
        }

        public Stage {
            buildings = Map.copyOf(Objects.requireNonNull(
                    buildings, "Состав этапа не может быть null: этап без построек задают пустой картой"));
            plan = List.copyOf(Objects.requireNonNull(
                    plan, "Набор этапа в плане не может быть null: этап без мест задают пустым списком"));
            requires = Map.copyOf(Objects.requireNonNull(
                    requires, "Требования этапа не могут быть null: этап без списка задают пустой картой"));
            optional = Map.copyOf(Objects.requireNonNull(
                    optional, "Желательные постройки этапа не могут быть null: без них задают пустую карту"));
            for (Map.Entry<BoardCase, Integer> entry : buildings.entrySet()) {
                if (entry.getValue() < 1) {
                    throw new IllegalArgumentException("Этап " + stage + ": уровень " + entry.getKey() + " = "
                            + entry.getValue() + ", а журнал доски отмечает уровни с первого");
                }
            }
            for (Map.Entry<String, Integer> entry : requires.entrySet()) {
                if (entry.getKey().isBlank() || entry.getValue() < 1) {
                    throw new IllegalArgumentException("Этап " + stage + ": требование '" + entry.getKey() + "' = "
                            + entry.getValue() + ", а постройка плана называется непусто и с уровня 1");
                }
            }
            for (Map.Entry<String, Integer> entry : optional.entrySet()) {
                if (entry.getKey().isBlank() || entry.getValue() < 1) {
                    throw new IllegalArgumentException("Этап " + stage + ": желательная постройка '" + entry.getKey()
                            + "' = " + entry.getValue()
                            + ", а постройка плана называется непусто и с уровня 1");
                }
                if (requires.containsKey(entry.getKey())) {
                    throw new IllegalArgumentException("Этап " + stage + ": постройка '" + entry.getKey()
                            + "' одновременно обязательна и желательна: переход решает только обязательное");
                }
            }
        }
    }

    public StageRules(List<Stage> stages) {
        this(stages, Map.of(), Map.of());
    }

    public StageRules(List<Stage> stages, Map<BoardCase, List<String>> schemes) {
        this(stages, schemes, Map.of());
    }

    public StageRules {
        stages = List.copyOf(Objects.requireNonNull(stages, "Этапы не могут быть null"));
        Map<BoardCase, List<String>> copied = new LinkedHashMap<>();
        Objects.requireNonNull(schemes, "Схемы уровней не могут быть null: без схем задают пустую карту")
                .forEach((boardCase, files) -> copied.put(boardCase, List.copyOf(files)));
        schemes = Map.copyOf(copied);
        planCases = Map.copyOf(Objects.requireNonNull(
                planCases, "Кейсы построек плана не могут быть null: без них задают пустую карту"));
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("Этапов нет: поселение начинается с этапа 1, его состав обязателен");
        }
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).stage() != i + 1) {
                throw new IllegalArgumentException("Этап на месте " + (i + 1) + " назван "
                        + stages.get(i).stage() + ": этапы идут подряд с первого, иначе переход перепрыгнет этап");
            }
        }
    }

    public List<List<String>> plans() {
        return stages.stream().map(Stage::plan).toList();
    }

    public int stageOf(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: этап читается по построенному");
        for (Stage stage : stages) {
            if (!missing(stage, ledger).isEmpty()) {
                return stage.stage();
            }
        }
        return stages.size() + 1;
    }

    public int stageOf(StageWorld world) {
        Objects.requireNonNull(world, "Мир этапа не может быть null: по нему видно, что стоит на самом деле");
        for (Stage stage : stages) {
            if (!missing(stage, world).isEmpty()) {
                return stage.stage();
            }
        }
        return stages.size() + 1;
    }

    public Optional<String> scheme(BoardCase boardCase, int level) {
        Objects.requireNonNull(boardCase, "Кейс постройки не может быть null: без него неизвестна схема уровня");
        List<String> files = schemes.getOrDefault(boardCase, List.of());
        return level >= 1 && level <= files.size() ? Optional.of(files.get(level - 1)) : Optional.empty();
    }

    public Map<String, Integer> missingOf(BoardLedger ledger) {
        return missingOf(ledger, stageOf(ledger));
    }

    public Map<String, Integer> missingOf(BoardLedger ledger, int stage) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: остаток этапа читается по построенному");
        if (stage < 1) {
            throw new IllegalArgumentException("Этап " + stage + ": этапы считаются с первого");
        }
        return stage > stages.size() ? Map.of() : missing(stages.get(stage - 1), ledger);
    }

    public Map<String, Integer> missingOf(StageWorld world) {
        Objects.requireNonNull(world, "Мир этапа не может быть null: по нему видно, что стоит на самом деле");
        return missingOf(world, stageOf(world));
    }

    public Map<String, Integer> missingOf(StageWorld world, int stage) {
        Objects.requireNonNull(world, "Мир этапа не может быть null: по нему видно, что стоит на самом деле");
        if (stage < 1) {
            throw new IllegalArgumentException("Этап " + stage + ": этапы считаются с первого");
        }
        return stage > stages.size() ? Map.of() : missing(stages.get(stage - 1), world);
    }

    private Map<String, Integer> missing(Stage stage, BoardLedger ledger) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        stage.buildings().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> !ledger.has(entry.getKey(), entry.getValue()))
                .forEach(entry -> missing.put(entry.getKey().name(), entry.getValue()));
        stage.requires().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> !built(entry.getKey(), entry.getValue(), ledger))
                .forEach(entry -> missing.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(missing);
    }

    private Map<String, Integer> missing(Stage stage, StageWorld world) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        stage.buildings().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> !world.built(entry.getKey(), entry.getValue()))
                .forEach(entry -> missing.put(entry.getKey().name(), entry.getValue()));
        stage.requires().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> !world.builtPlan(entry.getKey(), entry.getValue()))
                .forEach(entry -> missing.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(missing);
    }

    private boolean built(String id, int level, BoardLedger ledger) {
        BoardCase boardCase = planCases.get(id);
        if (boardCase != null) {
            return ledger.has(boardCase, level);
        }
        return ledger.hasPlan(id, level);
    }

    public static StageRules parse(String json, String origin) {
        return StageRulesJson.parse(json, origin);
    }

    public static StageRules current() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<StageRules> supplier) {
        SOURCE.use(supplier);
    }

    public static StageRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }
}
