package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PillarMaterialTests {
    @Test
    void cheapBlock_DirtOrCobble_PreferredOverStone() {
        Map<String, Integer> stock = Map.of("minecraft:dirt", 2, "minecraft:stone", 9);
        assertEquals(
                "minecraft:dirt",
                PillarBlocks.cheapBlock(id -> stock.getOrDefault(id, 0)),
                "Для столба сначала берут землю: камень нужен на инструменты и стройку, и тратить его на "
                        + "подмости дорого, иначе дух остаётся без инструментов. При смене cheapBlock_DirtOrCobble_PreferredOverStone проверь порядок "
                        + "приоритетов");
        Map<String, Integer> cobble = Map.of("minecraft:cobblestone", 1, "minecraft:stone", 9);
        assertEquals(
                "minecraft:cobblestone",
                PillarBlocks.cheapBlock(id -> cobble.getOrDefault(id, 0)),
                "Булыжник идёт раньше камня: камень — сырьё для печей и инструментов, булыжник — дешёвый "
                        + "строительный мусор, иначе печи встанут. При смене cheapBlock_DirtOrCobble_PreferredOverStone проверь "
                        + "второй шаг списка");
    }

    @Test
    void cheapBlock_DeepslateOrGravel_AcceptedAsSpare() {
        assertEquals(
                "minecraft:cobbled_deepslate",
                PillarBlocks.cheapBlock(id -> "minecraft:cobbled_deepslate".equals(id) ? 5 : 0),
                "Глубинный булыжник годится на столб: у шахтёров это частый остаток, и без него честный подъём "
                        + "валился «нет материала» при полном сундуке камня, иначе билет падал. При смене "
                        + "cheapBlock_DeepslateOrGravel_AcceptedAsSpare проверь список запасных блоков");
        assertEquals(
                "minecraft:gravel",
                PillarBlocks.cheapBlock(id -> "minecraft:gravel".equals(id) ? 3 : 0),
                "Гравий годится на столб: гравийные карьеры дают его даром, а подмости живут секунды, иначе подъём встаёт. При смене "
                        + "cheapBlock_DeepslateOrGravel_AcceptedAsSpare проверь хвост списка");
    }

    @Test
    void cheapBlock_EmptyInventory_ReturnsNull() {
        assertNull(
                PillarBlocks.cheapBlock(id -> 0),
                "Пустой инвентарь — материала нет: скрипт обязан пойти добывать землю, а не начинать столб "
                        + "в воздухе, иначе подъём падает. При смене cheapBlock_EmptyInventory_ReturnsNull проверь возврат null");
    }
}
