package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Каталог построек демо-стройки: id подкоманды, название и уровни с файлами схем. Список фиксирован
 * (креативная демонстрация), тело схемы и материалы берутся из библиотеки пресетов по имени файла.
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

    private static final List<Building> ALL = List.of(
            new Building("family_house", "Дом семьи", BuildingType.HOUSING, List.of(new Level(0, "family_house"))),
            new Building("stone_house", "Каменный дом", BuildingType.HOUSING, List.of(new Level(0, "stone_house"))),
            new Building("storage_hut", "Склад-хижина", BuildingType.STORAGE, List.of(new Level(0, "storage_hut"))),
            new Building(
                    "storage",
                    "Склад",
                    BuildingType.STORAGE,
                    List.of(
                            new Level(1, "storage_v1"),
                            new Level(2, "storage_v2"),
                            new Level(3, "storage_v3"),
                            new Level(4, "storage_v4"))),
            new Building("well", "Колодец", BuildingType.INFRASTRUCTURE, List.of(new Level(0, "well"))),
            new Building(
                    "torch_post", "Факельный столб", BuildingType.INFRASTRUCTURE, List.of(new Level(0, "torch_post"))),
            new Building(
                    "palisade_section",
                    "Палисад-секция",
                    BuildingType.DEFENSE,
                    List.of(new Level(0, "palisade_section"))),
            new Building(
                    "pen",
                    "Загон",
                    BuildingType.FARM,
                    List.of(new Level(1, "pen_4x5"), new Level(2, "pen_7x7"), new Level(3, "pen_9x9"))),
            new Building(
                    "enchant_room",
                    "Чаровальня",
                    BuildingType.CRAFT,
                    List.of(new Level(1, "enchant_room_v1"), new Level(2, "enchant_room_v2"))),
            new Building(
                    "mine_entrance",
                    "Вход в шахту",
                    BuildingType.INFRASTRUCTURE,
                    List.of(new Level(1, "mine_entrance_v1"))));

    private DemoBuildings() {}

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
