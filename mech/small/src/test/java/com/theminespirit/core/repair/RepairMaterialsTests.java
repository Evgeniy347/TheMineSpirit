package com.theminespirit.core.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepairMaterialsTests {

    @Test
    void materialFor_IronTool_ReturnsIronIngot() {
        assertEquals(
                Optional.of("minecraft:iron_ingot"),
                RepairMaterials.materialFor("minecraft:iron_pickaxe"),
                "Железный инструмент чинится железным слитком: тип задаёт материал, иначе дух ищет не тот предмет и "
                        + "ремонт встаёт. При смене RepairMaterials.materialFor проверь строку алмаза ниже в тесте");
    }

    @Test
    void materialFor_DiamondTool_ReturnsDiamond() {
        assertEquals(
                Optional.of("minecraft:diamond"),
                RepairMaterials.materialFor("minecraft:diamond_axe"),
                "Алмазный инструмент чинится алмазом, иначе дух чинит железом и тратит не тот запас. При смене "
                        + "materialFor проверь строку дерева ниже в тесте");
    }

    @Test
    void materialFor_WoodenTool_ReturnsPlanks() {
        assertEquals(
                Optional.of("minecraft:oak_planks"),
                RepairMaterials.materialFor("minecraft:wooden_shovel"),
                "Деревянный инструмент чинится досками, иначе дух тратит слиток на деревянную лопату. При смене "
                        + "materialFor проверь строку namespace ниже в тесте");
    }

    @Test
    void materialFor_WithoutNamespace_StillMatches() {
        assertEquals(
                Optional.of("minecraft:iron_ingot"),
                RepairMaterials.materialFor("iron_sword"),
                "Материал ищется и по короткому имени: рецепты и запасы приводят id к короткому виду, иначе материал "
                        + "не находится и ремонт не начинается. При смене разбора id проверь строку неизвестного ниже "
                        + "в тесте");
    }

    @Test
    void materialFor_UnknownItem_Empty() {
        assertEquals(
                Optional.empty(),
                RepairMaterials.materialFor("minecraft:stone"),
                "У предмета без своего типа материала нет: чинить его нечем, иначе план берёт случайный материал. "
                        + "При смене materialFor проверь строку пустого ниже в тесте");
    }

    @Test
    void materialFor_BlankId_Empty() {
        assertEquals(
                Optional.empty(),
                RepairMaterials.materialFor("  "),
                "Пустое имя предмета не даёт материала: искать не по чему, иначе пустая строка даёт материал. При "
                        + "смене проверки пустого проверь строку выше в тесте");
    }
}
