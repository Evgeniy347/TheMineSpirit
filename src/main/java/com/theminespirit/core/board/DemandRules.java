package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.TableSlot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public record DemandRules(int bedsReserve, int storageFreePercentMin, int darkCellsMin, boolean stageEnabled) {
    public static final String RESOURCE = "data/theminespirit/board/settlement_demand.json";
    private static final TableSlot<DemandRules> SOURCE = new TableSlot<>(DemandRules::load);

    private static final Set<String> FIELDS =
            Set.of("bedsReserve", "storageFreePercentMin", "darkCellsMin", "stageEnabled");

    public enum Kind {
        BEDS,
        STORAGE,
        STAGE,
        LIGHT
    }

    public record Need(Kind kind, String subject, int level) {
        public Need {
            Objects.requireNonNull(kind, "Нужда не может быть null: без неё доска не выберет стройку");
            Objects.requireNonNull(subject, "Предмет нужды не может быть null: без него нечего строить");
            if (subject.isBlank()) {
                throw new IllegalArgumentException("Предмет нужды пуст: по пустому имени стройку не найти");
            }
            if (level < 1) {
                throw new IllegalArgumentException("Уровень нужды " + level + " меньше 1: уровни начинаются с первого");
            }
        }
    }

    public record Facts(
            int spirits,
            int beds,
            int storageSlots,
            int storageFreePercent,
            int houseLevel,
            int storageLevel,
            Map<String, Integer> stageMissing,
            int darkCells) {
        public Facts {
            if (spirits < 0 || beds < 0 || storageSlots < 0 || houseLevel < 0 || storageLevel < 0 || darkCells < 0) {
                throw new IllegalArgumentException("Факты нужды отрицательны: духи " + spirits + ", кровати " + beds
                        + ", слоты " + storageSlots + ", дом " + houseLevel + ", склад " + storageLevel
                        + ", тёмные клетки " + darkCells + " — количество не бывает отрицательным");
            }
            if (storageFreePercent < 0 || storageFreePercent > 100) {
                throw new IllegalArgumentException("Свободно в складе " + storageFreePercent
                        + "%: доля вне [0, 100], по ней не понять, нужен ли склад");
            }
            stageMissing = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(
                    stageMissing, "Остаток этапа не может быть null: пустой задают пустой картой")));
            for (Map.Entry<String, Integer> entry : stageMissing.entrySet()) {
                if (entry.getKey() == null
                        || entry.getKey().isBlank()
                        || entry.getValue() == null
                        || entry.getValue() < 1) {
                    throw new IllegalArgumentException("Остаток этапа '" + entry.getKey() + "' = " + entry.getValue()
                            + ": постройка называется непусто и требует уровня с первого");
                }
            }
        }
    }

    public DemandRules {
        if (bedsReserve < 0) {
            throw new IllegalArgumentException(
                    "Запас спальных мест " + bedsReserve + " отрицателен: игрок не отнимает кровать");
        }
        if (storageFreePercentMin < 0 || storageFreePercentMin > 100) {
            throw new IllegalArgumentException("Порог свободного склада " + storageFreePercentMin
                    + "% вне [0, 100]: по нему не понять, когда строить следующий склад");
        }
        if (darkCellsMin < 0) {
            throw new IllegalArgumentException(
                    "Порог тёмных клеток " + darkCellsMin + " отрицателен: свет ищут по неотрицательному числу");
        }
    }

    public static DemandRules loadDefault() {
        return current();
    }

    public static DemandRules current() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<DemandRules> supplier) {
        SOURCE.use(supplier);
    }

    public static DemandRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static DemandRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "settlement_demand.json");
        try {
            return new DemandRules(
                    JsonResource.integer(root.get("bedsReserve"), origin, "bedsReserve"),
                    JsonResource.integer(root.get("storageFreePercentMin"), origin, "storageFreePercentMin"),
                    JsonResource.integer(root.get("darkCellsMin"), origin, "darkCellsMin"),
                    flag(root.get("stageEnabled"), origin));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public Optional<Need> next(Facts facts) {
        Objects.requireNonNull(facts, "Факты нужды не могут быть null: без них нечего выбирать");
        if (facts.beds() < facts.spirits() + bedsReserve) {
            return Optional.of(new Need(Kind.BEDS, "house", nextLevel(BoardCase.BUILD_HOUSE, facts.houseLevel())));
        }
        if (facts.storageSlots() == 0 || facts.storageFreePercent() < storageFreePercentMin) {
            return Optional.of(
                    new Need(Kind.STORAGE, "storage", nextLevel(BoardCase.BUILD_WAREHOUSE, facts.storageLevel())));
        }
        if (stageEnabled && !facts.stageMissing().isEmpty()) {
            Map.Entry<String, Integer> first =
                    facts.stageMissing().entrySet().iterator().next();
            return Optional.of(new Need(Kind.STAGE, first.getKey(), first.getValue()));
        }
        if (facts.darkCells() >= Math.max(1, darkCellsMin)) {
            return Optional.of(new Need(Kind.LIGHT, BoardCase.LIGHTING.name(), 1));
        }
        return Optional.empty();
    }

    private static int nextLevel(BoardCase boardCase, int current) {
        int max = BuildingLevels.maxLevel(boardCase);
        return current >= max ? max : current + 1;
    }

    private static boolean flag(JsonNode node, String origin) {
        if (node == null || !node.isBoolean()) {
            throw new IllegalArgumentException("Файл '" + origin + "': у stageEnabled ожидается true или false");
        }
        return node.asBoolean();
    }
}
