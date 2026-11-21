package com.theminespirit.core.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InventoryTextTests {
    @Test
    void of_ItemsSortedByName_GivesReadableList() {
        Map<String, Integer> inventory = new LinkedHashMap<>();
        inventory.put("oak_log", 25);
        inventory.put("cobblestone", 2);
        inventory.put("oak_planks", 86);

        assertEquals(
                "{cobblestone=2, oak_log=25, oak_planks=86}",
                InventoryText.of(inventory),
                "Инвентарь в строке провала обязан идти по именам: одинаковый набор даёт одинаковый текст"
                        + " Иначе тест of_ItemsSortedByName_GivesReadableList перестанет ловить эту поломку. При смене of_ItemsSortedByName_GivesReadableList проверь это утверждение.");
    }

    @Test
    void of_EmptyInventory_SaysEmpty() {
        assertEquals(
                "пусто",
                InventoryText.of(Map.of()),
                "Пустой инвентарь называется словом, а не пустыми скобками"
                        + " Иначе тест of_EmptyInventory_SaysEmpty перестанет ловить эту поломку. При смене of_EmptyInventory_SaysEmpty проверь это утверждение.");
    }
}
