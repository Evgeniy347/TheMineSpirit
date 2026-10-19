package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.ValueTable;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutsideRulesTests {
    private static final String VALID = """
            {"partyMin":2,"escortRadius":8.0,
            "mineKit":[{"match":["*_pickaxe"],"count":1},{"match":["torch"],"count":8},
            {"category":"food","count":3}]}
            """;

    @Test
    void parse_ValidJson_BuildsPartyAndKit() {
        OutsideRules rules = OutsideRules.parse(VALID, "тест");

        assertEquals(
                2,
                rules.partyMin(),
                "Порог группы обязан разобраться из файла: иначе число духов не управляется данными");
        assertEquals(
                8.0,
                rules.escortRadius(),
                "Радиус сопровождения читается из файла: иначе «рядом с игроком» стало бы константой в коде");
        assertEquals(3, rules.mineKit().size(), "Набор шахты из файла обязан содержать все три нужды");
        assertTrue(
                rules.mineKit().get(1).covers("minecraft:torch", ValueTable.load()),
                "Нужда «torch» совпадает с факелом: иначе проверка набора слепа");
        assertTrue(
                rules.mineKit().get(0).covers("minecraft:iron_pickaxe", ValueTable.load()),
                "Нужда «*_pickaxe» совпадает с любой киркой");
        assertTrue(
                rules.mineKit().get(2).covers("minecraft:bread", ValueTable.load()),
                "Нужда категории «food» совпадает с едой склада");
    }

    @Test
    void load_ShippedFile_MatchesOwnerDecision() {
        OutsideRules rules = OutsideRules.load();

        assertEquals(
                2, rules.partyMin(), "В одиночку за границу не выходят: шахта, отлов и поход начинаются от двух духов");
        assertEquals(
                3,
                rules.mineKit().size(),
                "Перед спуском проверяются кирка, факелы и еда: без них шахта в темноте и без инструмента");
    }

    @Test
    void parse_PartyBelowTwo_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> OutsideRules.parse(VALID.replace("\"partyMin\":2", "\"partyMin\":1"), "порог.json"),
                "Порог ниже двух отменяет решение владельца: один дух за границу не выходит");

        assertTrue(
                error.getMessage().contains("порог ниже двух")
                        || error.getMessage().contains("меньше 2"),
                "Ошибка обязана объяснять, что порог обязан быть не меньше двух, фактически: " + error.getMessage());
    }

    @Test
    void parse_NeedWithoutMatchOrCategory_Throws() {
        String broken = """
                {"partyMin":2,"escortRadius":8.0,"mineKit":[{"count":1}]}
                """;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> OutsideRules.parse(broken, "нужда.json"),
                "Нужда без match и category не описывает ни один предмет: такой файл не читается");

        assertTrue(
                error.getMessage().contains("match") && error.getMessage().contains("category"),
                "Ошибка называет оба поля выбора предмета, фактически: " + error.getMessage());
    }

    @Test
    void need_BothMatchAndCategory_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutsideRules.Need(List.of("torch"), "food", 1),
                "Два способа выбора предмета делают требование неопределённым");
    }

    @Test
    void need_ZeroCount_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutsideRules.Need(List.of("torch"), null, 0),
                "Нулевая нужда не проверяет ничего: количество обязано быть не меньше единицы");
    }
}
