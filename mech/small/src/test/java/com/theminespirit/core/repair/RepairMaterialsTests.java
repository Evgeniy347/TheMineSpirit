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
                "Железный инструмент чинится железным слитком: тип задаёт материал");
    }

    @Test
    void materialFor_DiamondTool_ReturnsDiamond() {
        assertEquals(
                Optional.of("minecraft:diamond"),
                RepairMaterials.materialFor("minecraft:diamond_axe"),
                "Алмазный инструмент чинится алмазом");
    }

    @Test
    void materialFor_WoodenTool_ReturnsPlanks() {
        assertEquals(
                Optional.of("minecraft:oak_planks"),
                RepairMaterials.materialFor("minecraft:wooden_shovel"),
                "Деревянный инструмент чинится досками");
    }

    @Test
    void materialFor_WithoutNamespace_StillMatches() {
        assertEquals(
                Optional.of("minecraft:iron_ingot"),
                RepairMaterials.materialFor("iron_sword"),
                "Материал ищется и по короткому имени: рецепты и запасы приводят id к короткому виду");
    }

    @Test
    void materialFor_UnknownItem_Empty() {
        assertEquals(
                Optional.empty(),
                RepairMaterials.materialFor("minecraft:stone"),
                "У предмета без своего типа материала нет: чинить его нечем");
    }

    @Test
    void materialFor_BlankId_Empty() {
        assertEquals(
                Optional.empty(),
                RepairMaterials.materialFor("  "),
                "Пустое имя предмета не даёт материала: искать не по чему");
    }
}
