package com.theminespirit.core.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArmorChoiceTests {
    private static final String ORIGIN = "data/theminespirit/armor/armor_table.json";
    private static final ArmorTable TABLE = ArmorTable.load();

    private static ArmorPiece piece(String item, ArmorSlot slot, int defense, double toughness, int protection,
            double durability) {
        return new ArmorPiece(item, slot, defense, toughness, protection, durability);
    }

    @Test
    void decide_LeatherAndIronChest_ChoosesIron() {
        List<ArmorPiece> inventory = List.of(
                piece("minecraft:leather_chestplate", ArmorSlot.CHEST, 3, 0, 0, 1.0),
                piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0));

        Map<ArmorSlot, ArmorPiece> chosen = ArmorChoice.decide(inventory, Map.of(), TABLE);

        assertEquals("minecraft:iron_chestplate", chosen.get(ArmorSlot.CHEST).item(),
                "Из кожаной и железной кирас выбирается железная: 6 очков брони против 3. Ошибка здесь "
                        + "оставила бы духа в худшей броне при лучшей в инвентаре");
    }

    @Test
    void decide_DiamondHelmetInInventory_ChoosesDiamond() {
        ArmorPiece worn = piece("minecraft:leather_helmet", ArmorSlot.HEAD, 1, 0, 0, 1.0);
        ArmorPiece candidate = piece("minecraft:diamond_helmet", ArmorSlot.HEAD, 3, 2.0, 0, 1.0);

        Map<ArmorSlot, ArmorPiece> chosen = ArmorChoice.decide(List.of(candidate), Map.of(ArmorSlot.HEAD, worn),
                TABLE);

        assertEquals("minecraft:diamond_helmet", chosen.get(ArmorSlot.HEAD).item(),
                "Надета кожаная шапка, в инвентаре алмазный шлем: надевается алмазный — 3 очка и крепость 2 "
                        + "против 1 очка кожаной. Ошибка оставила бы духа в коже");
    }

    @Test
    void decide_NearlyBrokenWorn_ReplacesWithWhole() {
        ArmorPiece worn = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 0.05);
        ArmorPiece candidate = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);

        Map<ArmorSlot, ArmorPiece> chosen = ArmorChoice.decide(List.of(candidate), Map.of(ArmorSlot.CHEST, worn),
                TABLE);

        assertEquals(1.0, chosen.get(ArmorSlot.CHEST).durabilityFraction(),
                "Почти сломанная кираса (доля 0.05 ниже порога 0.1) уступает целой такой же: иначе дух "
                        + "остался бы в броне, которая вот-вот порвётся, при целом запасном в инвентаре");
    }

    @Test
    void decide_EqualWornAndInventory_KeepsWorn() {
        ArmorPiece worn = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);
        ArmorPiece candidate = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);

        Map<ArmorSlot, ArmorPiece> chosen = ArmorChoice.decide(List.of(candidate), Map.of(ArmorSlot.CHEST, worn),
                TABLE);

        assertEquals(worn, chosen.get(ArmorSlot.CHEST),
                "Равный по всем признакам предмет не вытесняет надетый: иначе дух дёргался бы между двумя "
                        + "одинаковыми кирасами каждый тик и менял слоты без выгоды");
    }

    @Test
    void isStrictlyBetter_EqualPieces_ReturnsFalse() {
        ArmorPiece candidate = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);
        ArmorPiece worn = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);

        assertFalse(ArmorChoice.isStrictlyBetter(candidate, worn, TABLE),
                "Замена только при строгом улучшении: равный предмет не повод снимать надетый, иначе "
                        + "рефлекс дёргается между равными без выгоды");
    }

    @Test
    void isStrictlyBetter_ProtectionBreaksTie_ReturnsTrue() {
        ArmorPiece candidate = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 2, 1.0);
        ArmorPiece worn = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);

        assertTrue(ArmorChoice.isStrictlyBetter(candidate, worn, TABLE),
                "Зачарование защиты при равных очках и крепости делает предмет строго лучше: иначе дух "
                        + "игнорировал бы зачарованную броню в пользу простой");
    }

    @Test
    void isStrictlyBetter_NoWorn_ReturnsTrue() {
        ArmorPiece candidate = piece("minecraft:leather_boots", ArmorSlot.FEET, 1, 0, 0, 1.0);

        assertTrue(ArmorChoice.isStrictlyBetter(candidate, null, TABLE),
                "Пустой слот означает «надеть можно что угодно»: без надетого нет причины отказываться "
                        + "от лучшего предмета");
    }

    @Test
    void compare_DifferentSlots_Throws() {
        ArmorPiece helmet = piece("minecraft:iron_helmet", ArmorSlot.HEAD, 2, 0, 0, 1.0);
        ArmorPiece boots = piece("minecraft:iron_boots", ArmorSlot.FEET, 2, 0, 0, 1.0);

        assertThrows(IllegalArgumentException.class, () -> ArmorChoice.compare(helmet, boots, TABLE),
                "Сравнение предметов разных слотов обязано быть ошибкой: броня надевается только в свой слот, "
                        + "и «лучший шлем» не заменяет ботинки");
    }

    @Test
    void parse_WornFractionOutOfRange_Throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ArmorTable.parse("""
                        {"worn_fraction": 1.5,
                         "material_order": [{"match": ["leather_helmet"], "rank": 0}]}
                        """, ORIGIN),
                "Порог worn_fraction вне (0,1) обязан быть ошибкой: порог 1.5 сделал бы почти сломанным "
                        + "любой предмет, и дух менял бы броню постоянно");

        assertTrue(error.getMessage().contains("1.5"),
                "Сообщение обязано называть неверный порог 1.5: по нему правят файл. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void parse_EmptyMaterialOrder_Throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ArmorTable.parse("""
                        {"worn_fraction": 0.1, "material_order": []}
                        """, ORIGIN),
                "Пустой порядок материалов обязан быть ошибкой: без него равные по очкам и крепости "
                        + "предметы нечем различить, и выбор стал бы случайным");

        assertTrue(error.getMessage().contains("material_order"),
                "Сообщение обязано называть список material_order. Фактическое: " + error.getMessage());
    }
}