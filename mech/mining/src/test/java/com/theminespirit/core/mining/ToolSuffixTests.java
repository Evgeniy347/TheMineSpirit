package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ToolSuffixTests {
    @Test
    void ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix() {
        assertEquals(
                "_pickaxe",
                ToolSuffix.ofItem("minecraft:wooden_pickaxe"),
                "Деревянная кирка — кирка"
                        + " Иначе тест ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix перестанет ловить эту поломку. При смене ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix проверь это утверждение.");
        assertEquals(
                "_pickaxe",
                ToolSuffix.ofItem("stone_pickaxe"),
                "Имя без namespace тоже распознаётся"
                        + " Иначе тест ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix перестанет ловить эту поломку. При смене ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix проверь это утверждение.");
        assertEquals(
                "_shovel",
                ToolSuffix.ofItem("minecraft:iron_shovel"),
                "Лопата — лопата"
                        + " Иначе тест ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix перестанет ловить эту поломку. При смене ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix проверь это утверждение.");
        assertEquals(
                "_axe",
                ToolSuffix.ofItem("minecraft:diamond_axe"),
                "Топор — топор"
                        + " Иначе тест ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix перестанет ловить эту поломку. При смене ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix проверь это утверждение.");
        assertEquals(
                "_hoe",
                ToolSuffix.ofItem("minecraft:netherite_hoe"),
                "Тяпка — тяпка"
                        + " Иначе тест ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix перестанет ловить эту поломку. При смене ofItem_AnyPickaxeShovelAxeHoe_GivesSuffix проверь это утверждение.");
    }

    @Test
    void ofItem_NotATool_GivesEmptySuffix() {
        assertEquals(
                "",
                ToolSuffix.ofItem("minecraft:cobblestone"),
                "Камень не инструмент: замену не ищем"
                        + " Иначе тест ofItem_NotATool_GivesEmptySuffix перестанет ловить эту поломку. При смене ofItem_NotATool_GivesEmptySuffix проверь это утверждение.");
        assertEquals(
                "",
                ToolSuffix.ofItem("minecraft:bread"),
                "Еда не инструмент: замену не ищем"
                        + " Иначе тест ofItem_NotATool_GivesEmptySuffix перестанет ловить эту поломку. При смене ofItem_NotATool_GivesEmptySuffix проверь это утверждение.");
    }
}
