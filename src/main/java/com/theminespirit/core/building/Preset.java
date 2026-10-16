package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import java.util.Objects;

/**
 * Готовая постройка (пресет): метаданные поселения плюс тело схемы. Материалы посчитаны из блоков тела
 * при загрузке библиотеки, а не заданы в json вручную — иначе число в файле разошлось бы с блоками.
 */
public record Preset(String id, String name, int stage, BuildingType type, Schematic schematic, BlockCoord entry,
        Map<String, Integer> materials) {
    public static final int STAGE_MIN = 0;
    public static final int STAGE_MAX = 4;

    public Preset {
        Objects.requireNonNull(id, "id пресета не может быть null: по нему строят и ищут постройку");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id пресета пуст: по пустому id постройку не найти");
        }
        Objects.requireNonNull(name, "Название пресета не может быть null: его показывает /spirit presets");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Название пресета пусто: без него список построек нечитаем");
        }
        if (stage < STAGE_MIN || stage > STAGE_MAX) {
            throw new IllegalArgumentException("Этап пресета " + stage + " вне границ " + STAGE_MIN + ".." + STAGE_MAX
                    + ": этапы развития поселения заданы от 0 до 4");
        }
        Objects.requireNonNull(type, "Тип пресета не может быть null: по нему ищут постройки для поселения");
        Objects.requireNonNull(schematic, "Тело схемы пресета не может быть null: без него нечего строить");
        Objects.requireNonNull(entry, "Точка входа не может быть null: у постройки обязан быть вход");
        Objects.requireNonNull(materials, "Материалы не могут быть null: их считают из блоков пресета");
        materials = Map.copyOf(materials);
    }

    public int sizeX() {
        return schematic.sizeX();
    }

    public int sizeY() {
        return schematic.sizeY();
    }

    public int sizeZ() {
        return schematic.sizeZ();
    }
}