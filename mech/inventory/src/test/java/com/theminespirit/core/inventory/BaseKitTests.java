package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BaseKitTests {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = BaseKit.load();

    @Test
    void surplus_TwoSwords_KeepsBestAndGivesAwayWorse() {
        List<Stack> inventory = List.of(new Stack("wooden_sword", 0, 1, 64), new Stack("iron_sword", 0, 1, 1));

        assertEquals(
                List.of(new Slice(0, 1)),
                KIT.surplus(inventory, VALUES),
                "Из двух мечей набор оставляет лучший по ценности: железный (80) дороже деревянного (5), "
                        + "поэтому в контейнер уходит деревянный, иначе дух потащит худший меч в походе. "
                        + "При смене surplus проверь выбор меча");
    }

    @Test
    void surplus_TwentyBread_GivesAwayFour() {
        List<Stack> inventory = List.of(new Stack("bread", 0, 20, 64));

        assertEquals(
                List.of(new Slice(0, 4)),
                KIT.surplus(inventory, VALUES),
                "Квота еды — 16 штук: из 20 хлеба 4 лишних. Квота спасает духа от голода в походе, "
                        + "но не даёт тащить еду вместо алмаза, иначе место занято зря. "
                        + "При смене surplus проверь квоту еды");
    }

    @Test
    void surplus_MixedFood_CountsWholeGroupAgainstQuota() {
        List<Stack> inventory = List.of(new Stack("bread", 0, 8, 64), new Stack("cooked_beef", 0, 20, 64));

        assertEquals(
                List.of(new Slice(0, 8), new Slice(1, 4)),
                KIT.surplus(inventory, VALUES),
                "Еда считается одной группой на квоту 16: 8 хлеба + 20 жареной говядины дают 12 лишних, "
                        + "квота уходит более ценной жареной говядине (10), хлеб (8) уходит целиком, "
                        + "иначе счёт по стопкам завысит квоту. При смене surplus проверь общую группу еды");
    }

    @Test
    void surplus_ThirtyTorches_GivesAwaySix() {
        List<Stack> inventory = List.of(new Stack("torch", 0, 30, 64));

        assertEquals(
                List.of(new Slice(0, 6)),
                KIT.surplus(inventory, VALUES),
                "Квота факелов — 24 (минимум 20 по замыслу набора): из 30 факелов 6 лишних уходят "
                        + "в контейнер, иначе шахтёр потащит лишнее. При смене surplus проверь квоту факелов");
    }

    @Test
    void surplus_SixteenArrowsInsideQuota_GivesNothing() {
        List<Stack> inventory = List.of(new Stack("arrow", 0, 16, 64));

        assertEquals(
                List.of(),
                KIT.surplus(inventory, VALUES),
                "16 стрел — ровно квота постоянной части: стрелы нужны духу в любом походе, иначе "
                        + "лук останется без заряда. При смене surplus проверь квоту стрел");
    }

    @Test
    void surplus_SecondBow_GivesAwayWhole() {
        List<Stack> inventory = List.of(new Stack("bow", 0, 1, 1), new Stack("bow", 0, 1, 1));

        assertEquals(
                List.of(new Slice(1, 1)),
                KIT.surplus(inventory, VALUES),
                "В наборе один лук: второй уходит в контейнер, иначе дух тащил бы запасной лук вместо "
                        + "добычи. При смене surplus проверь второй лук");
    }

    @Test
    void surplus_ItemOutsideKit_IsGivenAwayWhole() {
        List<Stack> inventory = List.of(new Stack("oak_log", 0, 12, 64));

        assertEquals(
                List.of(new Slice(0, 12)),
                KIT.surplus(inventory, VALUES),
                "Предмет вне набора отдаётся целиком: брёвна не входят ни в постоянную часть, ни в "
                        + "деятельность без её набора, иначе в контейнере занимали бы место добычи. "
                        + "При смене surplus проверь предмет вне набора");
    }

    @Test
    void surplus_EmptyInventory_GivesNothing() {
        assertEquals(
                List.of(),
                KIT.surplus(List.of(), VALUES),
                "Пустой инвентарь не даёт излишка: разгружать нечего, иначе скрипт unload завершится "
                        + "ошибкой на пустом духе. При смене surplus проверь пустой инвентарь");
    }

    @Test
    void surplus_InventoryInsideQuota_GivesNothing() {
        List<Stack> inventory = List.of(
                new Stack("iron_sword", 0, 1, 1),
                new Stack("bow", 0, 1, 1),
                new Stack("arrow", 0, 16, 64),
                new Stack("bread", 0, 16, 64),
                new Stack("torch", 0, 24, 64));

        assertEquals(
                List.of(),
                KIT.surplus(inventory, VALUES),
                "Инвентарь ровно по набору не отдаёт ничего: разгрузка «без фанатизма» не забирает "
                        + "у духа последний лук и факел, иначе он останется без снаряжения. "
                        + "При смене surplus проверь инвентарь по набору");
    }

    @Test
    void surplus_EmptySlots_AreIgnored() {
        List<Stack> inventory = Arrays.asList(new Stack("torch", 0, 40, 64), null, new Stack("arrow", 0, 32, 64));

        assertEquals(
                List.of(new Slice(0, 16), new Slice(2, 16)),
                KIT.surplus(inventory, VALUES),
                "Пустой слот (null) не даёт излишка и не занимает номер: факелы и стрелы делят постоянные "
                        + "квоты, излишек уходит сверх квоты, иначе slice укажет не на тот слот. "
                        + "При смене surplus проверь null между стопками");
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

        assertTrue(
                KIT.isSatisfiedBy(left, VALUES),
                "Инвентарь без излишка обязан удовлетворять набору: иначе разгрузка за один проход "
                        + "работала бы не полностью и предметы уходили бы частями. Осталось: " + left
                        + ". При смене isSatisfiedBy проверь после вычитания излишка");
    }

    @Test
    void isSatisfiedBy_InventoryAboveQuota_ReturnsFalse() {
        List<Stack> inventory = List.of(new Stack("torch", 0, 25, 64));

        assertEquals(
                false,
                KIT.isSatisfiedBy(inventory, VALUES),
                "25 факелов при квоте 24 набору не удовлетворяют: проверка ловит инвентарь, который "
                        + "разгружен не полностью, иначе излишек останется. При смене isSatisfiedBy проверь 25 факелов");
    }

    @Test
    void isSatisfiedBy_WrongToolKept_ReturnsFalse() {
        List<Stack> inventory = List.of(new Stack("wooden_sword", 0, 1, 1), new Stack("iron_sword", 0, 1, 1));

        assertEquals(
                false,
                KIT.isSatisfiedBy(inventory, VALUES),
                "Два меча набору не удовлетворяют: в наборе один, лучший, иначе проверка пропустит "
                        + "лишний меч. При смене isSatisfiedBy проверь два меча");
    }

    @Test
    void load_LoadedKit_CoversWeaponsAmmoFoodAndTorches() {
        assertEquals(
                5,
                KIT.rules().size(),
                "Постоянная часть описывает 5 правил: меч, лук, стрелы, еда и факелы. Пропущенное "
                        + "правило — забытый предмет в походе, иначе дух останется без снаряжения. "
                        + "При смене загрузки проверь число правил");
    }

    @ParameterizedTest(name = "{0}: квота {1}, режим {2}")
    @CsvSource({
        "iron_sword, 1, BEST",
        "diamond_sword, 1, BEST",
        "bow, 1, ANY",
        "arrow, 16, ANY",
        "bread, 16, ANY",
        "cooked_beef, 16, ANY",
        "torch, 24, ANY"
    })
    void rules_ItemCoveredByRule_KeepsQuotaAndMode(String item, int keep, String mode) {
        KitRule rule = ruleFor(item);

        assertEquals(
                keep,
                rule.keep(),
                "Правило набора для '" + item + "' оставляет " + keep + ": квота решает, сколько предметов "
                        + "дух берёт с собой, иначе остальное уйдёт в контейнер при разгрузке. "
                        + "При смене правил проверь квоту " + item);
        assertEquals(
                KitMode.valueOf(mode),
                rule.mode(),
                "Правило набора для '" + item + "' работает в режиме " + mode + ": best оставляет лучший "
                        + "предмет группы, any — любое количество до квоты, иначе выбор предмета сломается. "
                        + "При смене правил проверь режим " + item);
    }

    @Test
    void rules_FoodRule_TakesCategoryFromValueTable() {
        KitRule rule = ruleFor("bread");

        assertEquals(
                "food",
                rule.category(),
                "Правило еды берёт группу 'food' из файла ценностей: отдельный список id еды в файле набора "
                        + "разошёлся бы с категориями, иначе новый продукт перестал бы попадать в набор. "
                        + "При смене правил проверь категорию еды");
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
