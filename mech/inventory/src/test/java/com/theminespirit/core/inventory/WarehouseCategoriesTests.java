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
        assertEquals(
                List.of(
                        "руда",
                        "слитки",
                        "камень",
                        "дерево",
                        "еда",
                        "урожай",
                        "инструменты",
                        "оружие и броня",
                        "ценное",
                        "прочее"),
                CATEGORIES.names(),
                "Категории склада заданы данными в порядке чеклиста: порядок задаёт и раскладку, и подписи, "
                        + "иначе сундуки сортируются не по правилам. При смене файла проверь порядок 10 категорий");
    }

    @Test
    void categoryOf_KnownItems_GoToTheirCategory() {
        assertEquals(
                "руда",
                CATEGORIES.categoryOf("raw_iron"),
                "Сырье — руда: иначе руда легла бы в сундук «прочее» и склад потерял бы порядок "
                        + "раскладки по категориям. При смене шаблонов проверь raw_iron");
        assertEquals(
                "слитки",
                CATEGORIES.categoryOf("iron_ingot"),
                "Слиток — слитки, иначе слитки смешаются с рудой и раскладка по сундукам сломается, "
                        + "а забор вернёт не тот сундук. При смене шаблонов проверь iron_ingot");
        assertEquals(
                "камень",
                CATEGORIES.categoryOf("cobblestone"),
                "Булыжник — камень, иначе стройматериал уйдёт в «прочее» и строитель пойдёт "
                        + "искать его по всем сундукам. При смене шаблонов проверь cobblestone");
        assertEquals(
                "дерево",
                CATEGORIES.categoryOf("oak_log"),
                "Бревно — дерево, иначе строевой лес потеряется среди мусора и плотник останется "
                        + "без материала. При смене шаблонов проверь oak_log");
        assertEquals(
                "еда",
                CATEGORIES.categoryOf("bread"),
                "Хлеб — еда, иначе еда ляжет в «прочее» и запас поселения размажется по складу "
                        + "без учёта. При смене шаблонов проверь bread");
        assertEquals(
                "урожай",
                CATEGORIES.categoryOf("wheat"),
                "Пшеница — урожай, иначе зерно смешается с едой и сломает учёт запасов поселения, "
                        + "а запас еды перекосится. При смене шаблонов проверь wheat");
        assertEquals(
                "инструменты",
                CATEGORIES.categoryOf("iron_pickaxe"),
                "Кирка — инструменты, иначе снаряжение попадёт в общий сундук и шахтёр останется "
                        + "без инструмента. При смене шаблонов проверь iron_pickaxe");
        assertEquals(
                "оружие и броня",
                CATEGORIES.categoryOf("iron_sword"),
                "Меч — оружие и броня, иначе бой пройдёт без снаряжения и дух потеряет предметы в бою, "
                        + "не донеся добычу. При смене шаблонов проверь iron_sword");
        assertEquals(
                "ценное",
                CATEGORIES.categoryOf("diamond"),
                "Алмаз — ценное, иначе ценности смешаются с камнем и разгрузка выбросит дорогое "
                        + "в контейнер без учёта. При смене шаблонов проверь diamond");
        assertEquals(
                "прочее",
                CATEGORIES.categoryOf("dirt"),
                "Земля не описана отдельно и попадает в прочее, иначе земля блокирует раскладку "
                        + "и занимает чужие сундуки. При смене шаблонов проверь dirt");
    }

    @Test
    void categoryOfSlots_EmptyChest_HasNoCategory() {
        assertEquals(
                "",
                CATEGORIES.categoryOfSlots(Arrays.asList(null, null)),
                "Пустой сундук без таблички ещё не выбрал категорию: её задаст первый предмет, иначе "
                        + "сортировка поставит его вслепую. При смене categoryOfSlots проверь пустой список");
    }

    @Test
    void categoryOfSlots_FirstItem_GivesCategoryToEmptyChest() {
        assertEquals(
                "руда",
                CATEGORIES.categoryOfSlots(Arrays.asList(null, new Stack("raw_iron", 0, 3, 64))),
                "Пустой сундук получает категорию первого положенного предмета: по ней его найдут "
                        + "следующие раскладки, иначе сундук останется безымянным. При смене проверь null + raw_iron");
    }

    @Test
    void categoryOf_UnknownItem_FallsIntoLastCategory() {
        assertEquals(
                "прочее",
                CATEGORIES.categoryOf("minecraft:some_unknown_item"),
                "Последняя категория с '*' обязана ловить любой предмет: иначе раскладка упала бы "
                        + "на незнакомом предмете и сдача встала. При смене шаблонов проверь неизвестный id");
    }

    @Test
    void parse_WithoutTrailingWildcard_IsRejected() {
        String json = "{\"version\":1,\"categories\":[" + "{\"name\":\"руда\",\"match\":[\"*_ore\"]}]}";

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> WarehouseCategories.parse(json, "тестовые категории"),
                "Файл без последней категории '*' обязан отвергаться: предмет вне категорий "
                        + "остался бы без сундука и потерялся при раскладке, иначе сдача встанет. "
                        + "При смене parse проверь отсутствие '*'");

        assertTrue(
                error.getMessage().contains("'*'"),
                "Ошибка обязана называть отсутствующий шаблон '*': без него непонятно, что чинить, "
                        + "иначе причину не исправить; фактически: " + error.getMessage()
                        + ". При смене текста проверь звёздочку в причине");
    }

    @Test
    void parse_DuplicateNames_AreRejected() {
        String json = "{\"version\":1,\"categories\":["
                + "{\"name\":\"руда\",\"match\":[\"*_ore\"]},"
                + "{\"name\":\"руда\",\"match\":[\"*\"]}]}";

        assertThrows(
                IllegalStateException.class,
                () -> WarehouseCategories.parse(json, "тестовые категории"),
                "Повтор имени категории делает раскладку неопределённой и обязан отвергаться, иначе предметы "
                        + "уходят в случайный сундук. При смене parse проверь дубли имён");
    }
}
