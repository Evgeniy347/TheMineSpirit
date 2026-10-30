package com.theminespirit.core.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record RepairData(
        int maxDurabilityPercent,
        int auditIntervalTicks,
        int cellBudgetPerTick,
        int warnTicks,
        int warnRepeatTicks,
        int rebuildPercent,
        List<String> softBlocks,
        Priorities priorities) {
    public static final String RESOURCE = "data/theminespirit/repair/repair.json";

    public static final int PERCENT_MIN = 0;
    public static final int PERCENT_MAX = 100;
    public static final int PERCENT_DEFAULT = 90;
    public static final int AUDIT_INTERVAL_DEFAULT = 24000;
    public static final int CELL_BUDGET_DEFAULT = 256;
    public static final int WARN_TICKS_DEFAULT = 3600;
    public static final int WARN_REPEAT_DEFAULT = 1200;
    public static final int REBUILD_PERCENT_DEFAULT = 50;

    private static final Set<String> FIELDS = Set.of(
            "maxDurabilityPercent",
            "auditIntervalTicks",
            "cellBudgetPerTick",
            "warnTicks",
            "warnRepeatTicks",
            "rebuildPercent",
            "softBlocks",
            "priorities");
    private static final Set<String> PRIORITY_FIELDS = Set.of("wall", "housing", "craft", "decor");
    private static final List<String> SOFT_DEFAULT = List.of(
            "poppy",
            "dandelion",
            "short_grass",
            "tall_grass",
            "wheat",
            "carrots",
            "potatoes",
            "beetroots",
            "oak_sapling",
            "vine",
            "snow",
            "white_carpet");
    private static final RepairData DEFAULTS = new RepairData(
            PERCENT_DEFAULT,
            AUDIT_INTERVAL_DEFAULT,
            CELL_BUDGET_DEFAULT,
            WARN_TICKS_DEFAULT,
            WARN_REPEAT_DEFAULT,
            REBUILD_PERCENT_DEFAULT,
            SOFT_DEFAULT,
            new Priorities(95, 70, 60, 30));

    public record Priorities(int wall, int housing, int craft, int decor) {
        public Priorities {
            checkPercent(wall, "Стена и ворота");
            checkPercent(housing, "Жильё");
            checkPercent(craft, "Ремесло и склады");
            checkPercent(decor, "Двор и улицы");
        }
    }

    public RepairData {
        checkDurability(maxDurabilityPercent);
        checkPercent(rebuildPercent, "Порог перестройки");
        checkPositive(auditIntervalTicks, "Интервал обхода");
        checkPositive(cellBudgetPerTick, "Бюджет клеток на тик");
        checkPositive(warnTicks, "Срок предупреждения");
        checkPositive(warnRepeatTicks, "Повтор предупреждения");
        if (warnRepeatTicks > warnTicks) {
            throw new IllegalArgumentException("Повтор предупреждения " + warnRepeatTicks + " тиков дольше срока "
                    + warnTicks + ": игрок не успел бы увидеть напоминание до сноса");
        }
        softBlocks = List.copyOf(Objects.requireNonNull(
                softBlocks, "Мягкие блоки не могут быть null: пустой список задают пустым списком"));
        for (String block : softBlocks) {
            if (block == null || block.isBlank()) {
                throw new IllegalArgumentException("Мягкий блок пуст: ремонт не узнает, что не требует");
            }
        }
        Objects.requireNonNull(priorities, "Приоритеты ремонта не могут быть null: без них доска не выберет");
    }

    public RepairData(int maxDurabilityPercent) {
        this(
                maxDurabilityPercent,
                DEFAULTS.auditIntervalTicks(),
                DEFAULTS.cellBudgetPerTick(),
                DEFAULTS.warnTicks(),
                DEFAULTS.warnRepeatTicks(),
                DEFAULTS.rebuildPercent(),
                DEFAULTS.softBlocks(),
                DEFAULTS.priorities());
    }

    public static RepairData defaults() {
        return DEFAULTS;
    }

    public static RepairData loadDefault() {
        return DataTables.INSTANCE.repair().current();
    }

    public static RepairData load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static RepairData parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "repair.json");
        int percent = JsonResource.integer(root.get("maxDurabilityPercent"), origin, "maxDurabilityPercent");
        try {
            return new RepairData(
                    percent,
                    optionalInteger(root, "auditIntervalTicks", origin, DEFAULTS.auditIntervalTicks()),
                    optionalInteger(root, "cellBudgetPerTick", origin, DEFAULTS.cellBudgetPerTick()),
                    optionalInteger(root, "warnTicks", origin, DEFAULTS.warnTicks()),
                    optionalInteger(root, "warnRepeatTicks", origin, DEFAULTS.warnRepeatTicks()),
                    optionalInteger(root, "rebuildPercent", origin, DEFAULTS.rebuildPercent()),
                    softBlocks(root, origin),
                    priorities(root.get("priorities"), origin));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    private static int optionalInteger(JsonNode root, String field, String origin, int fallback) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? fallback : JsonResource.integer(node, origin, field);
    }

    private static List<String> softBlocks(JsonNode root, String origin) {
        JsonNode node = root.get("softBlocks");
        return node == null || node.isNull()
                ? DEFAULTS.softBlocks()
                : JsonResource.strings(node, origin, "мягких блоков ремонта");
    }

    private static Priorities priorities(@Nullable JsonNode node, String origin) {
        if (node == null || node.isNull()) {
            return DEFAULTS.priorities();
        }
        JsonResource.checkFields(node, PRIORITY_FIELDS, origin, "приоритетов ремонта");
        return new Priorities(
                JsonResource.integer(node.get("wall"), origin, "wall"),
                JsonResource.integer(node.get("housing"), origin, "housing"),
                JsonResource.integer(node.get("craft"), origin, "craft"),
                JsonResource.integer(node.get("decor"), origin, "decor"));
    }

    private static void checkDurability(int value) {
        if (value < 1 || value > PERCENT_MAX) {
            throw new IllegalArgumentException("Порог прочности " + value + "% вне диапазона [1, " + PERCENT_MAX
                    + "]: выше 100% не чинилось бы ничего, а ноль чинил бы любое");
        }
    }

    private static void checkPercent(int value, String what) {
        if (value < PERCENT_MIN || value > PERCENT_MAX) {
            throw new IllegalArgumentException(what + " " + value + "% вне диапазона [" + PERCENT_MIN + ", "
                    + PERCENT_MAX + "]: доля выше 100% не сработала бы, а ноль не оставил бы ремонта");
        }
    }

    private static void checkPositive(int value, String what) {
        if (value < 1) {
            throw new IllegalArgumentException(
                    what + " " + value + ": срок и бюджет обхода положительны, иначе обход не идёт");
        }
    }
}
