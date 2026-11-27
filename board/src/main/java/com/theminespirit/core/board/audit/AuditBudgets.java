package com.theminespirit.core.board.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.api.JsonResource;
import com.theminespirit.core.reload.api.TableSlot;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public record AuditBudgets(int fillCellsPerTick, int verifyCellsPerTick, int drainCellsPerTick, int groupsPerCycle) {

    public static final String RESOURCE = "data/theminespirit/audit/audit.json";
    public static final AuditBudgets DEFAULT = new AuditBudgets(2048, 4096, 512, 64);

    private static final TableSlot<AuditBudgets> SOURCE = new TableSlot<>(AuditBudgets::load);
    private static final Set<String> FIELDS =
            Set.of("fillCellsPerTick", "verifyCellsPerTick", "drainCellsPerTick", "groupsPerCycle");

    public AuditBudgets {
        requirePositive(fillCellsPerTick, "fillCellsPerTick", "первичное чтение чанка");
        requirePositive(verifyCellsPerTick, "verifyCellsPerTick", "фоновая сверка кэша с миром");
        requirePositive(drainCellsPerTick, "drainCellsPerTick", "разбор очереди изменений за такт");
        requirePositive(groupsPerCycle, "groupsPerCycle", "пересборка групп на цикл графа");
    }

    public static AuditBudgets load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static AuditBudgets current() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<AuditBudgets> supplier) {
        SOURCE.use(supplier);
    }

    public static AuditBudgets parse(String json, String origin) {
        Objects.requireNonNull(json, "Текст файла аудита не может быть null: разбирать нечего");
        Objects.requireNonNull(origin, "Источник файла не может быть null: сообщение об ошибке называет путь");
        JsonNode root = JsonResource.parse(json, origin);
        if (!root.isObject()) {
            throw new IllegalStateException("Файл '" + origin
                    + "': ожидается объект бюджетов: массив или число не дадут ни одного лимита, и аудит"
                    + " пошёл бы по миру без узды");
        }
        JsonResource.checkFields(root, FIELDS, origin, "бюджеты аудита");
        return new AuditBudgets(
                JsonResource.integer(need(root, origin, "fillCellsPerTick"), origin, "fillCellsPerTick"),
                JsonResource.integer(need(root, origin, "verifyCellsPerTick"), origin, "verifyCellsPerTick"),
                JsonResource.integer(need(root, origin, "drainCellsPerTick"), origin, "drainCellsPerTick"),
                JsonResource.integer(need(root, origin, "groupsPerCycle"), origin, "groupsPerCycle"));
    }

    private static JsonNode need(JsonNode root, String origin, String field) {
        JsonNode value = root.get(field);
        if (value == null) {
            throw new IllegalStateException("Файл '" + origin + "': нет поля '" + field
                    + "' у бюджетов аудита: без поля аудит остался бы без лимита и завис бы на такте");
        }
        return value;
    }

    private static void requirePositive(int value, String field, String purpose) {
        if (value <= 0) {
            throw new IllegalArgumentException("Бюджет " + field + " = " + value + ": " + purpose
                    + " обязан быть положительным, иначе нулевой лимит остановит аудит поселения навсегда");
        }
    }
}
