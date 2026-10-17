package com.theminespirit.core.intellect;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record InventoryNormTable(String origin, List<CurvePoint> occupiedCurve, List<CurvePoint> extraCurve,
        Map<ActivityId, Integer> releaseFreeSlots) {

    public static final String RESOURCE = "data/theminespirit/inventory/inventory_norm.json";

    private static final Set<String> ROOT_FIELDS = Set.of("demand", "release_free_slots");
    private static final Set<String> DEMAND_FIELDS = Set.of("occupied_curve", "extra_curve");
    private static final Set<String> POINT_FIELDS = Set.of("at", "value");

    public record CurvePoint(double at, double value) {
        public CurvePoint {
            if (Double.isNaN(at) || at < 0 || at > 1) {
                throw new IllegalArgumentException("Точка кривой спроса at = " + at
                        + ": доля обязана лежать в 0..1, иначе кривую не с чем сравнивать");
            }
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
                throw new IllegalArgumentException("Точка кривой спроса at = " + at + " со значением " + value
                        + ": спрос обязан быть конечным и >= 0, иначе распределение сравнивает несравнимое");
            }
        }
    }

    public InventoryNormTable {
        Objects.requireNonNull(origin, "Источник таблицы не может быть null: сообщение называет файл");
        occupiedCurve = checkCurve(occupiedCurve, "occupied_curve");
        extraCurve = checkCurve(extraCurve, "extra_curve");
        Objects.requireNonNull(releaseFreeSlots, "Пороги освобождения не могут быть null: "
                + "по ним решают, когда дух бросает задачу");
        Map<ActivityId, Integer> checked = new EnumMap<>(ActivityId.class);
        for (ActivityId id : ActivityId.values()) {
            Integer threshold = releaseFreeSlots.get(id);
            if (threshold == null) {
                throw new IllegalArgumentException("У деятельности " + id + " нет порога освобождения: "
                        + "без него дух не знает, до какой ячейки держать задачу");
            }
            if (threshold < 0) {
                throw new IllegalArgumentException("Порог освобождения " + id + " = " + threshold
                        + ": порог — сколько свободных ячеек дух ещё терпит, отрицательного не бывает");
            }
            checked.put(id, threshold);
        }
        releaseFreeSlots = Map.copyOf(checked);
    }

    public static InventoryNormTable loadDefault() {
        return DataTables.INSTANCE.inventoryNorm().current();
    }

    public static InventoryNormTable readClasspath() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE + " (мод)");
    }

    public static InventoryNormTable load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static InventoryNormTable parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, ROOT_FIELDS, origin, "файла нормы инвентаря");
        JsonNode demand = root.get("demand");
        if (demand == null || !demand.isObject()) {
            throw new IllegalStateException("Файл '" + origin + "': у demand ожидается объект с кривыми спроса");
        }
        JsonResource.checkFields(demand, DEMAND_FIELDS, origin, "блока demand");
        List<CurvePoint> occupied = parseCurve(demand.get("occupied_curve"), origin, "occupied_curve");
        List<CurvePoint> extra = parseCurve(demand.get("extra_curve"), origin, "extra_curve");
        Map<ActivityId, Integer> release = parseRelease(root.get("release_free_slots"), origin);
        try {
            return new InventoryNormTable(origin, occupied, extra, release);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public int releaseFreeSlots(ActivityId activity) {
        Objects.requireNonNull(activity, "Деятельность не может быть null: порог ищут по id");
        return releaseFreeSlots.get(activity);
    }

    private static List<CurvePoint> parseCurve(JsonNode node, String origin, String what) {
        List<JsonNode> points = JsonResource.array(node, origin, what);
        List<CurvePoint> parsed = new ArrayList<>(points.size());
        for (JsonNode point : points) {
            if (!point.isObject()) {
                throw new IllegalStateException("Файл '" + origin + "': точка кривой " + what
                        + " — не объект с полями at и value");
            }
            JsonResource.checkFields(point, POINT_FIELDS, origin, "точки кривой " + what);
            parsed.add(new CurvePoint(JsonResource.decimal(point.get("at"), origin, "поля at кривой " + what),
                    JsonResource.decimal(point.get("value"), origin, "поля value кривой " + what)));
        }
        return parsed;
    }

    private static Map<ActivityId, Integer> parseRelease(JsonNode node, String origin) {
        if (node == null || !node.isObject() || node.isEmpty()) {
            throw new IllegalStateException("Файл '" + origin
                    + "': у release_free_slots ожидается непустой объект с порогами по деятельностям");
        }
        Set<String> allowed = new HashSet<>();
        for (ActivityId id : ActivityId.values()) {
            allowed.add(id.name());
        }
        JsonResource.checkFields(node, allowed, origin, "блока release_free_slots");
        Map<ActivityId, Integer> release = new EnumMap<>(ActivityId.class);
        for (ActivityId id : ActivityId.values()) {
            JsonNode threshold = node.get(id.name());
            if (threshold != null) {
                release.put(id, JsonResource.integer(threshold, origin, "порога " + id));
            }
        }
        return release;
    }

    private static List<CurvePoint> checkCurve(List<CurvePoint> curve, String what) {
        Objects.requireNonNull(curve, "Кривая " + what + " не может быть null: без неё спрос не считается");
        if (curve.isEmpty()) {
            throw new IllegalArgumentException("Кривая " + what + " пуста: без точек спрос не задать");
        }
        List<CurvePoint> checked = List.copyOf(curve);
        if (checked.get(0).at() != 0.0 || checked.get(0).value() != 0.0) {
            throw new IllegalArgumentException("Кривая " + what
                    + " обязана начинаться точкой (0, 0): пустой инвентарь не создаёт спроса");
        }
        if (checked.get(checked.size() - 1).at() != 1.0) {
            throw new IllegalArgumentException("Кривая " + what + " обязана заканчиваться при at = 1: "
                    + "иначе у полного инвентаря спрос не определён");
        }
        for (int index = 1; index < checked.size(); index++) {
            CurvePoint previous = checked.get(index - 1);
            CurvePoint current = checked.get(index);
            if (current.at() <= previous.at()) {
                throw new IllegalArgumentException("Кривая " + what + ": доли точек не растут ("
                        + previous.at() + " -> " + current.at() + "): кривая обязана быть упорядочена");
            }
            if (current.value() < previous.value()) {
                throw new IllegalArgumentException("Кривая " + what + ": спрос падает ("
                        + previous.value() + " -> " + current.value() + ") при росте заполнения: "
                        + "чем полнее инвентарь, тем сильнее нужда его освободить");
            }
        }
        return checked;
    }
}
