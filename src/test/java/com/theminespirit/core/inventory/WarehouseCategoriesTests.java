package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseCategoriesTests {
    private static final WarehouseCategories CATEGORIES = WarehouseCategories.load();

    @Test
    void names_RealData_MatchChecklistOrder() {
        assertEquals(List.of("руда", "слитки", "камень", "дерево", "еда", "урожай", "инструменты",
                "оружие и броня", "ценное", "прочее"), CATEGORIES.names(),
                "Категории склада заданы данными в порядке чеклиста: порядок задаёт и раскладку, и подписи");
    }

    @Test
    void categoryOf_KnownItems_GoToTheirCategory() {
        assertEquals("руда", CATEGORIES.categoryOf("raw_iron"),
                "Сырье — руда: иначе руда легла бы в сундук «прочее»");
        assertEquals("слитки", CATEGORIES.categoryOf("iron_ingot"),
                "Слиток — слитки");
        assertEquals("камень", CATEGORIES.categoryOf("cobblestone"),
                "Булыжник — камень");
        assertEquals("дерево", CATEGORIES.categoryOf("oak_log"),
                "Бревно — дерево");
        assertEquals("еда", CATEGORIES.categoryOf("bread"),
                "Хлеб — еда");
        assertEquals("урожай", CATEGORIES.categoryOf("wheat"),
                "Пшеница — урожай");
        assertEquals("инструменты", CATEGORIES.categoryOf("iron_pickaxe"),
                "Кирка — инструменты");
        assertEquals("оружие и броня", CATEGORIES.categoryOf("iron_sword"),
                "Меч — оружие и броня");
        assertEquals("ценное", CATEGORIES.categoryOf("diamond"),
                "Алмаз — ценное");
        assertEquals("прочее", CATEGORIES.categoryOf("dirt"),
                "Земля не описана отдельно и попадает в прочее");
    }

    @Test
    void categoryOfSlots_EmptyChest_HasNoCategory() {
        assertEquals("", CATEGORIES.categoryOfSlots(Arrays.asList(null, null)),
                "Пустой сундук без таблички ещё не выбрал категорию: её задаст первый предмет");
    }

    @Test
    void categoryOfSlots_FirstItem_GivesCategoryToEmptyChest() {
        assertEquals("руда", CATEGORIES.categoryOfSlots(Arrays.asList(null,
                        new Stack("raw_iron", 0, 3, 64))),
                "Пустой сундук получает категорию первого положенного предмета: по ней его найдут "
                        + "следующие раскладки");
    }

    @Test
    void categoryOf_UnknownItem_FallsIntoLastCategory() {
        assertEquals("прочее", CATEGORIES.categoryOf("minecraft:some_unknown_item"),
                "Последняя категория с '*' обязана ловить любой предмет: иначе раскладка упала бы "
                        + "на незнакомом предмете");
    }

    @Test
    void parse_WithoutTrailingWildcard_IsRejected() {
        String json = "{\"version\":1,\"categories\":["
                + "{\"name\":\"руда\",\"match\":[\"*_ore\"]}]}";

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> WarehouseCategories.parse(json, "тестовые категории"),
                "Файл без последней категории '*' обязан отвергаться: предмет вне категорий "
                        + "остался бы без сундука");

        assertTrue(error.getMessage().contains("'*'"),
                "Ошибка обязана называть отсутствующий шаблон '*': без него непонятно, что чинить; "
                        + "фактически: " + error.getMessage());
    }

    @Test
    void parse_DuplicateNames_AreRejected() {
        String json = "{\"version\":1,\"categories\":["
                + "{\"name\":\"руда\",\"match\":[\"*_ore\"]},"
                + "{\"name\":\"руда\",\"match\":[\"*\"]}]}";

        assertThrows(IllegalStateException.class,
                () -> WarehouseCategories.parse(json, "тестовые категории"),
                "Повтор имени категории делает раскладку неопределённой и обязан отвергаться");
    }
}
