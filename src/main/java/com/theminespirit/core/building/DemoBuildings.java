package com.theminespirit.core.building;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Каталог семей построек (данные {@code schematics/buildings.json}): id подкоманды демо, название, тип и уровни
 * с файлами схем; тело схемы и материалы берутся из библиотеки пресетов по имени файла.
 *
 * <p>У постройки без уровней один уровень {@code 0}; у загона уровни 1–3, у чаровальни 1–2.
 */
public final class DemoBuildings {
    public record Level(int level, String scheme) {
        public Level {
            Objects.requireNonNull(scheme, "Файл схемы уровня не может быть null: без него нечего строить");
            if (scheme.isBlank()) {
                throw new IllegalArgumentException("Файл схемы уровня пуст: по пустому имени постройку не найти");
            }
            if (level < 0) {
                throw new IllegalArgumentException("Уровень " + level + " отрицателен: уровни начинаются с 1");
            }
        }
    }

    public record Building(String id, String name, BuildingType type, List<Level> levels) {
        public Building {
            Objects.requireNonNull(id, "id постройки не может быть null: по нему находят подкоманду");
            Objects.requireNonNull(name, "Название постройки не может быть null: его показывает список");
            Objects.requireNonNull(type, "Тип постройки не может быть null: от него зависят правила качества схем");
            Objects.requireNonNull(levels, "Уровни не могут быть null: без них нечего строить");
            if (levels.isEmpty()) {
                throw new IllegalArgumentException("У постройки '" + id + "' нет ни одного уровня: строить нечего");
            }
            levels = List.copyOf(levels);
        }

        public boolean leveled() {
            return levels.size() > 1 || levels.get(0).level() > 0;
        }

        public Level level(int requested) {
            int target = requested == 0 ? levels.get(0).level() : requested;
            for (Level level : levels) {
                if (level.level() == target) {
                    return level;
                }
            }
            throw new IllegalArgumentException("У постройки '" + id + "' нет уровня " + target + ": доступны "
                    + levels.stream().map(l -> Integer.toString(l.level())).toList());
        }
    }

    public static final String CATALOG = PresetLibrary.SCHEMATICS_ROOT + "buildings.json";
    private static final Set<String> BUILDING_FIELDS = Set.of("id", "name", "type", "levels");
    private static final Set<String> LEVEL_FIELDS = Set.of("level", "scheme");

    private static final List<Building> ALL = parse(JsonResource.readClasspath(CATALOG), CATALOG);

    private DemoBuildings() {}

    public static List<Building> parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, Set.of("buildings"), origin, "каталога построек");
        List<Building> buildings = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode item : JsonResource.array(root.get("buildings"), origin, "списка buildings")) {
            JsonResource.checkFields(item, BUILDING_FIELDS, origin, "постройки каталога");
            String id = JsonResource.text(item.get("id"), origin, "поля id постройки");
            String typeId = JsonResource.text(item.get("type"), origin, "поля type постройки '" + id + "'");
            BuildingType type = BuildingType.byId(typeId)
                    .orElseThrow(() -> new IllegalArgumentException("У постройки '" + id + "' в " + origin
                            + " неизвестный тип '" + typeId + "': по нему не найти правила качества"));
            List<Level> levels = new ArrayList<>();
            for (JsonNode level : JsonResource.array(item.get("levels"), origin, "уровней постройки '" + id + "'")) {
                JsonResource.checkFields(level, LEVEL_FIELDS, origin, "уровня постройки '" + id + "'");
                levels.add(new Level(
                        JsonResource.integer(level.get("level"), origin, "номера уровня '" + id + "'"),
                        JsonResource.text(level.get("scheme"), origin, "схемы уровня '" + id + "'")));
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Постройка '" + id + "' объявлена в " + origin
                        + " дважды: подкоманда /spirit demo взяла бы одну из них наугад");
            }
            buildings.add(new Building(
                    id, JsonResource.text(item.get("name"), origin, "названия '" + id + "'"), type, levels));
        }
        return List.copyOf(buildings);
    }

    public static List<Building> all() {
        return ALL;
    }

    public static List<String> ids() {
        List<String> ids = new ArrayList<>(ALL.size());
        for (Building building : ALL) {
            ids.add(building.id());
        }
        return List.copyOf(ids);
    }

    public static Optional<Building> byId(String id) {
        Objects.requireNonNull(id, "id постройки не может быть null: по нему находят подкоманду");
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (Building building : ALL) {
            if (building.id().equals(normalized)) {
                return Optional.of(building);
            }
        }
        return Optional.empty();
    }

    public static Building require(String id) {
        return byId(id).orElseThrow(
                        () -> new IllegalArgumentException("Неизвестная постройка '" + id + "': допустимы " + ids()));
    }
}
