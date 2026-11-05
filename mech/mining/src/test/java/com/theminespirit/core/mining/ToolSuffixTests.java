package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ToolSuffixTests {
    @Test
    void ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix() {
        assertEquals("_pickaxe", ToolSuffix.ofItem("minecraft:wooden_pickaxe"), "Деревянная кирка — кирка");
        assertEquals("_pickaxe", ToolSuffix.ofItem("stone_pickaxe"), "Имя без namespace тоже распознаётся");
        assertEquals("_shovel", ToolSuffix.ofItem("minecraft:iron_shovel"), "Лопата — лопата");
        assertEquals("_axe", ToolSuffix.ofItem("minecraft:diamond_axe"), "Топор — топор");
        assertEquals("_hoe", ToolSuffix.ofItem("minecraft:netherite_hoe"), "Тяпка — тяпка");
    }

    @Test
    void ofItem_NotATool_GivesEmptySuffix() {
        assertEquals("", ToolSuffix.ofItem("minecraft:cobblestone"), "Камень не инструмент: замену не ищем");
        assertEquals("", ToolSuffix.ofItem("minecraft:bread"), "Еда не инструмент: замену не ищем");
    }
}
