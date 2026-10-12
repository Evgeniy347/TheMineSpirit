package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BaseKitTests {
    private static final String ORIGIN = "data/theminespirit/inventory/base_kit.json";

    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = BaseKit.load();

    @Test
    void surplus_TwoAxes_KeepsBestAndGivesAwayWorse() {
        List<Stack> inventory = List.of(
                new Stack("wooden_axe", 0, 1, 64),
                new Stack("iron_axe", 0, 1, 1));

        assertEquals(List.of(new Slice(0, 1)), KIT.surplus(inventory, VALUES),
                "Из двух топоров набор оставляет лучший по ценности: железный (80) дороже деревянного (5), "
                        + "поэтому в контейнер уходит деревянный. Ошибка здесь заставит духа тащить худший "
                        + "инструмент в поход");
    }

    @Test
    void surplus_ThreePickaxes_KeepsMostValuable() {
        List<Stack> inventory = List.of(
                new Stack("stone_pickaxe", 0, 1, 1),
                new Stack("diamond_pickaxe", 0, 1, 1),
                new Stack("iron_pickaxe", 0, 1, 1));

        assertEquals(List.of(new Slice(0, 1), new Slice(2, 1)), KIT.surplus(inventory, VALUES),
                "Из трёх кирок набор оставляет алмазную (400), железная (80) и каменная (10) уходят: "
                        + "при переполнении инвентаря дух берёт самое ценное, а дешёвое отдаёт");
    }

    @Test
    void surplus_TwentyBread_GivesAwayFour() {
        List<Stack> inventory = List.of(new Stack("bread", 0, 20, 64));

        assertEquals(List.of(new Slice(0, 4)), KIT.surplus(inventory, VALUES),
                "Квота еды — 16 штук: из 20 хлеба 4 лишних. Квота спасает духа от голода в походе, "
                        + "но не даёт тащить еду вместо алмаза");
    }

    @Test
    void surplus_MixedFood_CountsWholeGroupAgainstQuota() {
        List<Stack> inventory = List.of(
                new Stack("bread", 0, 8, 64),
                new Stack("cooked_beef", 0, 20, 64));

        assertEquals(List.of(new Slice(0, 8), new Slice(1, 4)), KIT.surplus(inventory, VALUES),
                "Еда считается одной группой на квоту 16: 8 хлеба + 20 жареной говядины дают 12 лишних, "
                        + "квота уходит более ценной жареной говядине (10), хлеб (8) уходит целиком");
    }

    @Test
    void surplus_CobblestoneAboveQuota_GivesAwayExcess() {
        List<Stack> inventory = List.of(new Stack("cobblestone", 0, 64, 64));

        assertEquals(List.of(new Slice(0, 32)), KIT.surplus(inventory, VALUES),
                "Строительный блок для опоры держится в количестве 32: из стопки 64 в контейнер удут 32, "
                        + "чтобы после разгрузки дух мог ставить блоки под себя");
    }

    @Test
    void surplus_ItemOutsideKit_IsGivenAwayWhole() {
        List<Stack> inventory = List.of(new Stack("oak_log", 0, 12, 64));

        assertEquals(List.of(new Slice(0, 12)), KIT.surplus(inventory, VALUES),
                "Предмет вне базового набора отдаётся целиком: набор решает, что дух берет с собой, "
                        + "всё остальное — груз на складе");
    }

    @Test
    void surplus_EmptyInventory_GivesNothing() {
        assertEquals(List.of(), KIT.surplus(List.of(), VALUES),
                "Пустой инвентарь не даёт излишка: разгружать нечего, скрипт unload завершается успехом");
    }

    @Test
    void surplus_InventoryInsideQuota_GivesNothing() {
        List<Stack> inventory = List.of(
                new Stack("iron_pickaxe", 0, 1, 1),
                new Stack("bread", 0, 16, 64),
                new Stack("torch", 0, 16, 64),
                new Stack("crafting_table", 0, 1, 64));

        assertEquals(List.of(), KIT.surplus(inventory, VALUES),
                "Инвентарь ровно по набору не отдаёт ничего: разгрузка «без фанатизма» не забирает "
                        + "у духа последний инструмент и факел");
    }

    @Test
    void surplus_EmptySlots_AreIgnored() {
        List<Stack> inventory = Arrays.asList(
                new Stack("cobblestone", 0, 64, 64),
                null,
                new Stack("dirt", 0, 40, 64));

        assertEquals(List.of(new Slice(0, 32), new Slice(2, 40)), KIT.surplus(inventory, VALUES),
                "Пустой слот (null) не даёт излишка и не занимает номер: булыжник и земля делят одну "
                        + "квоту 32 строительного блока, при равной ценности квота уходит первому слоту, "
                        + "поэтому остаётся 32 булыжника, а земля уходит целиком");
    }

    @Test
    void surplus_DamagedTool_IsSeparateVariantAndNotChosen() {
        List<Stack> inventory = List.of(
                new Stack("iron_pickaxe", 0, 1, 1),
                new Stack("iron_pickaxe", 7, 1, 1));

        assertEquals(List.of(new Slice(1, 1)), KIT.surplus(inventory, VALUES),
                "Кирка с другими компонентами — другой вариант: набор оставляет первую, "
                        + "а «такую же» с другими компонентами отдаёт, потому что зачарования "
                        + "и повреждение ценность по таблице не меняют");
    }

    @Test
    void isSatisfiedBy_InventoryReducedBySurplus_ReturnsTrue() {
        List<Stack> inventory = List.of(
                new Stack("wooden_pickaxe", 0, 1, 1),
                new Stack("iron_pickaxe", 0, 1, 1),
                new Stack("bread", 0, 20, 64),
                new Stack("cobblestone", 0, 64, 64),
                new Stack("oak_log", 0, 3, 64));
        List<Slice> surplus = KIT.surplus(inventory, VALUES);
        List<Stack> left = remove(inventory, surplus);

        assertTrue(KIT.isSatisfiedBy(left, VALUES),
                "Инвентарь без излишка обязан удовлетворять набору: иначе разгрузка за один проход "
                        + "работала бы не полностью и предметы уходили бы частями. Осталось: " + left);
    }

    @Test
    void isSatisfiedBy_InventoryAboveQuota_ReturnsFalse() {
        List<Stack> inventory = List.of(new Stack("torch", 0, 17, 64));

        assertEquals(false, KIT.isSatisfiedBy(inventory, VALUES),
                "17 факелов при квоте 16 набору не удовлетворяют: проверка ловит инвентарь, "
                        + "который разгружен не полностью");
    }

    @Test
    void isSatisfiedBy_WrongToolKept_ReturnsFalse() {
        List<Stack> inventory = List.of(
                new Stack("wooden_pickaxe", 0, 1, 1),
                new Stack("iron_pickaxe", 0, 1, 1));

        assertEquals(false, KIT.isSatisfiedBy(inventory, VALUES),
                "Две кирки разных материалов набору не удовлетворяют: в наборе одна, лучшая");
    }

    @Test
    void load_LoadedKit_CoversToolsFoodAndBuilding() {
        assertEquals(12, KIT.rules().size(),
                "Файл базового набора описывает 12 правил: пять инструментов, три ведра, еда, "
                        + "факелы, строительный блок и верстак. Пропущенное правило — забытый предмет в походе");
    }

    @ParameterizedTest(name = "{0}: квота {1}, режим {2}")
    @CsvSource({
            "iron_pickaxe, 1, BEST",
            "wooden_axe, 1, BEST",
            "diamond_shovel, 1, BEST",
            "bucket, 1, ANY",
            "water_bucket, 1, ANY",
            "bread, 16, ANY",
            "cooked_beef, 16, ANY",
            "torch, 16, ANY",
            "cobblestone, 32, ANY",
            "dirt, 32, ANY",
            "crafting_table, 1, ANY"})
    void rules_ItemCoveredByRule_KeepsQuotaAndMode(String item, int keep, String mode) {
        KitRule rule = ruleFor(item);

        assertEquals(keep, rule.keep(),
                "Правило набора для '" + item + "' оставляет " + keep + ": квота решает, сколько предметов "
                        + "дух берёт с собой, а остальное уходит в контейнер при разгрузке");
        assertEquals(KitMode.valueOf(mode), rule.mode(),
                "Правило набора для '" + item + "' работает в режиме " + mode + ": best оставляет лучший "
                        + "предмет группы, any — любое количество до квоты");
    }

    @Test
    void rules_FoodRule_TakesCategoryFromValueTable() {
        KitRule rule = ruleFor("bread");

        assertEquals("food", rule.category(),
                "Правило еды берёт группу 'food' из файла ценностей: отдельный список id еды в файле набора "
                        + "разошёлся бы с категориями, и новый продукт перестал бы попадать в набор");
    }

    private static KitRule ruleFor(String item) {
        for (KitRule rule : KIT.rules()) {
            if (rule.covers(item, VALUES)) {
                return rule;
            }
        }
        throw new AssertionError("Предмет '" + item + "' не описан ни одним правилом базового набора: "
                + "неописанный предмет уходит в контейнер первым");
    }

    @Test
    void parse_RuleWithBothMatchAndCategory_Throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BaseKit.parse("""
                        {"rules": [{"match": ["*_pickaxe"], "category": "tools", "keep": 1, "mode": "best"}]}
                        """, ORIGIN),
                "Правило с match и category сразу обязано быть ошибкой: два способа выбора предметов "
                        + "делают квоту неопределённой, и разгрузка станет непредсказуемой");

        assertTrue(error.getMessage().contains("match") && error.getMessage().contains("category"),
                "Сообщение обязано называть оба поля: по ним правят правило. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_RuleWithoutMatchAndCategory_Throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BaseKit.parse("""
                        {"rules": [{"keep": 1, "mode": "best"}]}
                        """, ORIGIN),
                "Правило без шаблонов и без категории обязано быть ошибкой: такое правило молча "
                        + "ничего не описывает, и предмет уходит в контейнер мимо набора");

        assertTrue(error.getMessage().contains("match"),
                "Сообщение обязано называть поле match: по нему правят правило. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void parse_NegativeKeep_ThrowsWithValue() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BaseKit.parse("""
                        {"rules": [{"match": ["torch"], "keep": -1, "mode": "any"}]}
                        """, ORIGIN),
                "Отрицательная квота обязана быть ошибкой: квота «сколько оставить» меньше нуля "
                        + "отдала бы в контейнер даже предметы, которых у духа нет");

        assertTrue(error.getMessage().contains("-1"),
                "Сообщение обязано называть отрицательную квоту -1: по нему находят строку файла. "
                        + "Фактическое: " + error.getMessage());
    }

    @Test
    void parse_UnknownMode_ThrowsWithValue() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BaseKit.parse("""
                        {"rules": [{"match": ["torch"], "keep": 1, "mode": "cheapest"}]}
                        """, ORIGIN),
                "Неизвестный режим обязана быть ошибкой: опечатка в режиме делает квоту неработающей, "
                        + "а молчаливый пропуск отдал бы весь набор в контейнер");

        assertTrue(error.getMessage().contains("cheapest"),
                "Сообщение обязано называть неверный режим 'cheapest': по нему правят файл. "
                        + "Фактическое: " + error.getMessage());
    }

    @Test
    void parse_PatternOutsideFilterAlphabet_ThrowsWithPattern() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BaseKit.parse("""
                        {"rules": [{"match": ["*_Pickaxe"], "keep": 1, "mode": "best"}]}
                        """, ORIGIN),
                "Шаблон вне алфавита IdFilter обязан быть ошибкой: он не совпадёт ни с одним id "
                        + "и тихо выключит правило набора");

        assertTrue(error.getMessage().contains("*_Pickaxe"),
                "Сообщение обязано называть плохой шаблон '*_Pickaxe': по нему правят файл. "
                        + "Фактическое: " + error.getMessage());
    }

    @Test
    void load_MissingFile_ThrowsWithPath() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BaseKit.load("data/theminespirit/inventory/absent-kit.json"),
                "Отсутствие файла базового набора обязано быть ошибкой: без набора разгрузка "
                        + "отдала бы в контейнер всё, включая последнюю кирку");

        assertTrue(error.getMessage().contains("data/theminespirit/inventory/absent-kit.json"),
                "Сообщение обязано называть путь, по которому файл искали. Фактическое: "
                        + error.getMessage());
    }

    private static List<Stack> remove(List<Stack> inventory, List<Slice> surplus) {
        List<Stack> left = new ArrayList<>(inventory);
        for (Slice slice : surplus) {
            Stack stack = left.get(slice.slot());
            int kept = stack.count() - slice.count();
            left.set(slice.slot(), kept == 0 ? null : stack.withCount(kept));
        }
        return left;
    }
}
