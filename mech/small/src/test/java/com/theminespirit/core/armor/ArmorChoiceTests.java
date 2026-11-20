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

    private static ArmorPiece piece(
            String item, ArmorSlot slot, int defense, double toughness, int protection, double durability) {
        return new ArmorPiece(item, slot, defense, toughness, protection, durability);
    }

    @Test
    void decide_LeatherAndIronChest_ChoosesIron() {
        List<ArmorPiece> inventory = List.of(
                piece("minecraft:leather_chestplate", ArmorSlot.CHEST, 3, 0, 0, 1.0),
                piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0));

        Map<ArmorSlot, ArmorPiece> chosen = ArmorChoice.decide(inventory, Map.of(), TABLE);

        assertEquals(
                "minecraft:iron_chestplate",
                chosen.get(ArmorSlot.CHEST).item(),
                "Из кожаной и железной кирас выбирается железная: 6 очков брони против 3, иначе защита ниже. "
                        + "Ошибка здесь оставила бы духа в худшей броне при лучшей в инвентаре. При смене "
                        + "ArmorChoice.decide проверь строку алмаза ниже в тесте");
    }

    @Test
    void decide_DiamondHelmetInInventory_ChoosesDiamond() {
        ArmorPiece worn = piece("minecraft:leather_helmet", ArmorSlot.HEAD, 1, 0, 0, 1.0);
        ArmorPiece candidate = piece("minecraft:diamond_helmet", ArmorSlot.HEAD, 3, 2.0, 0, 1.0);

        Map<ArmorSlot, ArmorPiece> chosen = ArmorChoice.decide(List.of(candidate), Map.of(ArmorSlot.HEAD, worn), TABLE);

        assertEquals(
                "minecraft:diamond_helmet",
                chosen.get(ArmorSlot.HEAD).item(),
                "Надета кожаная шапка, в инвентаре алмазный шлем: надевается алмазный — 3 очка и крепость 2 против 1 "
                        + "очка кожаной, иначе выбор неверен. Ошибка оставила бы духа в коже. При смене decide проверь "
                        + "строку сломанной ниже в тесте");
    }

    @Test
    void decide_NearlyBrokenWorn_ReplacesWithWhole() {
        ArmorPiece worn = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 0.05);
        ArmorPiece candidate = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);

        Map<ArmorSlot, ArmorPiece> chosen =
                ArmorChoice.decide(List.of(candidate), Map.of(ArmorSlot.CHEST, worn), TABLE);

        assertEquals(
                1.0,
                chosen.get(ArmorSlot.CHEST).durabilityFraction(),
                "Почти сломанная кираса (доля 0.05 ниже порога 0.1) уступает целой такой же, иначе дух остался бы в "
                        + "броне, которая вот-вот порвётся, при целом запасном в инвентаре. При смене порога "
                        + "worn_fraction проверь строку равного ниже в тесте");
    }

    @Test
    void decide_EqualWornAndInventory_KeepsWorn() {
        ArmorPiece worn = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);
        ArmorPiece candidate = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);

        Map<ArmorSlot, ArmorPiece> chosen =
                ArmorChoice.decide(List.of(candidate), Map.of(ArmorSlot.CHEST, worn), TABLE);

        assertEquals(
                worn,
                chosen.get(ArmorSlot.CHEST),
                "Равный по всем признакам предмет не вытесняет надетый, иначе дух дёргался бы между двумя "
                        + "одинаковыми кирасами каждый тик и менял слоты без выгоды. При смене decide проверь строку "
                        + "isStrictlyBetter ниже в тесте");
    }

    @Test
    void isStrictlyBetter_EqualPieces_ReturnsFalse() {
        ArmorPiece candidate = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);
        ArmorPiece worn = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);

        assertFalse(
                ArmorChoice.isStrictlyBetter(candidate, worn, TABLE),
                "Замена только при строгом улучшении: равный предмет не повод снимать надетый, иначе рефлекс "
                        + "дёргается между равными без выгоды. При смене isStrictlyBetter проверь строку зачарования "
                        + "ниже в тесте");
    }

    @Test
    void isStrictlyBetter_ProtectionBreaksTie_ReturnsTrue() {
        ArmorPiece candidate = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 2, 1.0);
        ArmorPiece worn = piece("minecraft:iron_chestplate", ArmorSlot.CHEST, 6, 0, 0, 1.0);

        assertTrue(
                ArmorChoice.isStrictlyBetter(candidate, worn, TABLE),
                "Зачарование защиты при равных очках и крепости делает предмет строго лучше, иначе дух игнорировал "
                        + "бы зачарованную броню в пользу простой. При смене isStrictlyBetter проверь строку пустого "
                        + "ниже в тесте");
    }

    @Test
    void isStrictlyBetter_NoWorn_ReturnsTrue() {
        ArmorPiece candidate = piece("minecraft:leather_boots", ArmorSlot.FEET, 1, 0, 0, 1.0);

        assertTrue(
                ArmorChoice.isStrictlyBetter(candidate, null, TABLE),
                "Пустой слот означает «надеть можно что угодно»: без надетого нет причины отказываться от лучшего "
                        + "предмета, иначе слот пустует. При смене isStrictlyBetter проверь строку слотов ниже в тесте");
    }

    @Test
    void compare_DifferentSlots_Throws() {
        ArmorPiece helmet = piece("minecraft:iron_helmet", ArmorSlot.HEAD, 2, 0, 0, 1.0);
        ArmorPiece boots = piece("minecraft:iron_boots", ArmorSlot.FEET, 2, 0, 0, 1.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> ArmorChoice.compare(helmet, boots, TABLE),
                "Сравнение предметов разных слотов обязано быть ошибкой: броня надевается только в свой слот, и "
                        + "«лучший шлем» не заменяет ботинки, иначе броня уходит не в тот слот. При смене compare "
                        + "проверь строку порога ниже в тесте");
    }

    @Test
    void parse_WornFractionOutOfRange_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ArmorTable.parse("""
                        {"worn_fraction": 1.5,
                         "material_order": [{"match": ["leather_helmet"], "rank": 0}]}
                        """, ORIGIN),
                "Порог worn_fraction вне (0,1) обязан быть ошибкой: порог 1.5 сделал бы почти сломанным любой "
                        + "предмет, и дух менял бы броню постоянно, иначе баланс ломается. При смене parse проверь "
                        + "строку имени ниже в тесте");

        assertTrue(
                error.getMessage().contains("1.5"),
                "Сообщение обязано называть неверный порог 1.5: по нему правят файл, иначе опечатка не найдена. "
                        + "Фактическое: " + error.getMessage() + ". При смене текста проверь строку интервала ниже в "
                        + "тесте");
    }

    @Test
    void loadedTable_CheckInterval_IsPositive() {
        assertTrue(
                TABLE.checkIntervalTicks() > 0,
                "Интервал проверки брони из файла обязан быть положительным, иначе политика не поняла бы, когда "
                        + "проверять, фактически " + TABLE.checkIntervalTicks() + ". При смене checkIntervalTicks "
                        + "проверь строку нулевого интервала ниже в тесте");
    }

    @Test
    void parse_ZeroCheckInterval_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ArmorTable.parse("""
                        {"worn_fraction": 0.1, "check_interval_ticks": 0,
                         "material_order": [{"match": ["leather_helmet"], "rank": 0}]}
                        """, ORIGIN),
                "Нулевой интервал обязан быть ошибкой: он превратил бы проверку брони в каждый тик и нагрузил бы "
                        + "серверный тик, иначе лаг. При смене parse проверь строку имени ниже в тесте");

        assertTrue(
                error.getMessage().contains("check_interval_ticks"),
                "Сообщение обязано называть поле check_interval_ticks, иначе правка слепа. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь строку пустого порядка ниже в тесте");
    }

    @Test
    void parse_EmptyMaterialOrder_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ArmorTable.parse("""
                        {"worn_fraction": 0.1, "material_order": []}
                        """, ORIGIN),
                "Пустой порядок материалов обязан быть ошибкой: без него равные по очкам и крепости предметы нечем "
                        + "различить, и выбор стал бы случайным, иначе дух меняет броню хаотично. При смене parse "
                        + "проверь строку имени ниже в тесте");

        assertTrue(
                error.getMessage().contains("material_order"),
                "Сообщение обязано называть список material_order, иначе правка слепа. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь строку выше в тесте");
    }
}
