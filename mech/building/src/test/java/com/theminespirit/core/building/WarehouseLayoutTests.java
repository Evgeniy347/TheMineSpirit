package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.inventory.WarehouseCategories;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseLayoutTests {
    private static final String ROOT = PresetLibrary.SCHEMATICS_ROOT;

    private static WarehouseLayout layout(int level) {
        String file = "storage_v" + level;
        String path = ROOT + file + SchematicTextParser.EXTENSION;
        return WarehouseLayout.of(SchematicTextParser.parse(JsonResource.readClasspath(path), path)
                .schematic());
    }

    @Test
    void levels_DoubleChests_MatchChecklist() {
        List<Integer> expected = List.of(9, 27, 81, 243);
        for (int level = 1; level <= 4; level++) {
            WarehouseLayout layout = layout(level);
            assertEquals(
                    expected.get(level - 1),
                    layout.doubleChests(),
                    "Склад уровня " + level + " обязан вмещать " + expected.get(level - 1)
                            + " двойных сундуков по чеклисту: другое число разошлось бы с раскладкой "
                            + "категорий и вместимостью склада");
            assertEquals(
                    expected.get(level - 1) / 3,
                    layout.stacks().size(),
                    "Каждая стопка уровня " + level + " — три двойных сундука: число стопок обязано "
                            + "быть втрое меньше числа двойных сундуков");
        }
    }

    @Test
    void levels_InteriorHeight_IsFour() {
        for (int level = 1; level <= 4; level++) {
            assertEquals(
                    4,
                    layout(level).interiorHeight(),
                    "Внутренняя высота склада уровня " + level + " — 4 блока: над верхним сундуком "
                            + "обязан остаться воздух, иначе он не открывается");
        }
    }

    @Test
    void levelsTwoAndUp_EveryStack_HasCategorySign() {
        WarehouseCategories categories = WarehouseCategories.load();
        for (int level = 2; level <= 4; level++) {
            WarehouseLayout layout = layout(level);
            assertEquals(
                    layout.stacks().size(),
                    layout.signs().size(),
                    "У склада уровня " + level + " на каждую стопку сундуков обязана быть табличка: "
                            + "без неё дух не знает, куда класть предмет");
            assertEquals(
                    List.of(),
                    layout.stacksWithoutSign(),
                    "У каждой стопки склада уровня " + level + " обязана быть табличка справа: "
                            + "пустые стопки без подписи не получат категорию");
            for (WarehouseLayout.Sign sign : layout.signs()) {
                assertTrue(
                        categories.known(sign.text()),
                        "Табличка склада называет категорию из таблицы данных, фактически '" + sign.text()
                                + "': неизвестная надпись не даст раскладке выбрать сундук");
                assertFalse(sign.text().isBlank(), "Надпись на табличке не может быть пустой");
            }
        }
    }

    @Test
    void levelOne_WithoutChests_HasNoCategorySigns() {
        WarehouseLayout layout = layout(1);

        assertEquals(
                List.of(),
                layout.signs(),
                "Уровень 1 — три стопки без колонн и табличек: подпись появилась бы раньше категорий");
    }
}
