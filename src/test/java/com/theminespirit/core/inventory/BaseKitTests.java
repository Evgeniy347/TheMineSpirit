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
    void surplus_TwoSwords_KeepsBestAndGivesAwayWorse() {
        List<Stack> inventory = List.of(
                new Stack("wooden_sword", 0, 1, 64),
                new Stack("iron_sword", 0, 1, 1));

        assertEquals(List.of(new Slice(0, 1)), KIT.surplus(inventory, VALUES),
                "Из двух мечей набор оставляет лучший по ценности: железный (80) дороже деревянного (5), "
                        + "поэтому в контейнер уходит деревянный. Ошибка здесь заставит духа тащить худший "
                        + "меч в походе");
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
    void surplus_ThirtyTorches_GivesAwaySix() {
        List<Stack> inventory = List.of(new Stack("torch", 0, 30, 64));

        assertEquals(List.of(new Slice(0, 6)), KIT.surplus(inventory, VALUES),
                "Квота факелов — 24 (минимум 20 по замыслу набора): из 30 факелов 6 лишних уходят в контейнер");
    }

    @Test
    void surplus_SixteenArrowsInsideQuota_GivesNothing() {
        List<Stack> inventory = List.of(new Stack("arrow", 0, 16, 64));

        assertEquals(List.of(), KIT.surplus(inventory, VALUES),
                "16 стрел — ровно квота постоянной части: стрелы нужны духу в любом походе");
    }

    @Test
    void surplus_SecondBow_GivesAwayWhole() {
        List<Stack> inventory = List.of(
                new Stack("bow", 0, 1, 1),
                new Stack("bow", 0, 1, 1));

        assertEquals(List.of(new Slice(1, 1)), KIT.surplus(inventory, VALUES),
                "В наборе один лук: второй уходит в контейнер, иначе дух тащил бы запасной лук вместо добычи");
    }

    @Test
    void surplus_ItemOutsideKit_IsGivenAwayWhole() {
        List<Stack> inventory = List.of(new Stack("oak_log", 0, 12, 64));

        assertEquals(List.of(new Slice(0, 12)), KIT.surplus(inventory, VALUES),
                "Предмет вне набора отдаётся целиком: брёвна не входят ни в постоянную часть, ни в деятельность "
                        + "без её набора, и в контейнере занимали бы место добычи");
    }

    @Test
    void surplus_EmptyInventory_GivesNothing() {
        assertEquals(List.of(), KIT.surplus(List.of(), VALUES),
                "Пустой инвентарь не даёт излишка: разгружать нечего, скрипт unload завершается успехом");
    }

    @Test
    void surplus_InventoryInsideQuota_GivesNothing() {
        List<Stack> inventory = List.of(
                new Stack("iron_sword", 0, 1, 1),
                new Stack("bow", 0, 1, 1),
                new Stack("arrow", 0, 16, 64),
                new Stack("bread", 0, 16, 64),
                new Stack("torch", 0, 24, 64));

        assertEquals(List.of(), KIT.surplus(inventory, VALUES),
                "Инвентарь ровно по набору не отдаёт ничего: разгрузка «без фанатизма» не забирает "
                        + "у духа последний лук и факел");
    }

    @Test
    void surplus_EmptySlots_AreIgnored() {
        List<Stack> inventory = Arrays.asList(
                new Stack("torch", 0, 40, 64),
                null,
                new Stack("arrow", 0, 32, 64));

        assertEquals(List.of(new Slice(0, 16), new Slice(2, 16)), KIT.surplus(inventory, VALUES),
                "Пустой слот (null) не даёт излишка и не занимает номер: факелы и стрелы делят постоянные "
                        + "квоты, излишек уходит сверх квоты");
    }

    @Test
    void isSatisfiedBy_InventoryReducedBySurplus_ReturnsTrue() {
        List<Stack> inventory = List.of(
                new Stack("wooden_sword", 0, 1, 1),
                new Stack("iron_sword", 0, 1, 1),
                new Stack("bread", 0, 20, 64),
                new Stack("torch", 0, 40, 64),
                new Stack("oak_log", 0, 3, 64));
        List<Slice> surplus = KIT.surplus(inventory, VALUES);
        List<Stack> left = remove(inventory, surplus);

        assertTrue(KIT.isSatisfiedBy(left, VALUES),
                "Инвентарь без излишка обязан удовлетворять набору: иначе разгрузка за один проход "
                        + "работала бы не полностью и предметы уходили бы частями. Осталось: " + left);
    }

    @Test
    void isSatisfiedBy_InventoryAboveQuota_ReturnsFalse() {
        List<Stack> inventory = List.of(new Stack("torch", 0, 25, 64));

        assertEquals(false, KIT.isSatisfiedBy(inventory, VALUES),
                "25 факелов при квоте 24 набору не удовлетворяют: проверка ловит инвентарь, "
                        + "который разгружен не полностью");
    }

    @Test
    void isSatisfiedBy_WrongToolKept_ReturnsFalse() {
        List<Stack> inventory = List.of(
                new Stack("wooden_sword", 0, 1, 1),
                new Stack("iron_sword", 0, 1, 1));

        assertEquals(false, KIT.isSatisfiedBy(inventory, VALUES),
                "Два меча набору не удовлетворяют: в наборе один, лучший");
    }

    @Test
    void load_LoadedKit_CoversWeaponsAmmoFoodAndTorches() {
        assertEquals(5, KIT.rules().size(),
                "Постоянная часть описывает 5 правил: меч, лук, стрелы, еда и факелы. Пропущенное правило — "
                        + "забытый предмет в походе");
    }

    @ParameterizedTest(name = "{0}: квота {1}, режим {2}")
    @CsvSource({
            "iron_sword, 1, BEST",
            "diamond_sword, 1, BEST",
            "bow, 1, ANY",
            "arrow, 16, ANY",
            "bread, 16, ANY",
            "cooked_beef, 16, ANY",
            "torch, 24, ANY"})
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
                        {"rules": [{"match": ["*_sword"], "category": "tools", "keep": 1, "mode": "best"}]}
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
                        {"rules": [{"match": ["*_Sword"], "keep": 1, "mode": "best"}]}
                        """, ORIGIN),
                "Шаблон вне алфавита IdFilter обязан быть ошибкой: он не совпадёт ни с одним id "
                        + "и тихо выключит правило набора");

        assertTrue(error.getMessage().contains("*_Sword"),
                "Сообщение обязано называть плохой шаблон '*_Sword': по нему правят файл. "
                        + "Фактическое: " + error.getMessage());
    }

    @Test
    void load_MissingFile_ThrowsWithPath() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> BaseKit.load("data/theminespirit/inventory/absent-kit.json"),
                "Отсутствие файла базового набора обязано быть ошибкой: без набора разгрузка "
                        + "отдала бы в контейнер всё, включая последний лук");

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
