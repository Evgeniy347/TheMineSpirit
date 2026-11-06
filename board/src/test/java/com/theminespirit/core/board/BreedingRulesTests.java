package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.Species;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BreedingRulesTests {

    @Test
    void action_OneAnimal_NoBreed() {
        assertEquals(
                BreedingRules.Action.NONE,
                BreedingRules.action(Species.COW, 1, true),
                "Одна корова в загоне — разведение недоступно: пару сводят из двух, одна голова " + "не даёт приплода");
        assertFalse(
                BreedingRules.breedingAvailable(Species.COW, 1, true),
                "Задание разведения не ставится, пока в загоне меньше пары");
    }

    @Test
    void action_TwoAnimals_Breed() {
        assertEquals(
                BreedingRules.Action.BREED,
                BreedingRules.action(Species.COW, 2, true),
                "Две коровы в загоне — разведение доступно: пара есть и лимит 4 не выбран");
        assertTrue(
                BreedingRules.breedingAvailable(Species.COW, 2, true),
                "BREED_ANIMALS ставится, когда в загоне вида ≥ 2 и лимит не выбран");
    }

    @Test
    void action_FourAnimals_Cull() {
        assertEquals(
                BreedingRules.Action.CULL,
                BreedingRules.action(Species.COW, 4, true),
                "Четыре коровы — лимит выбран: вместо разведения контроль численности (HERD_CULL)");
        assertTrue(
                BreedingRules.cullNeeded(Species.COW, 4, true),
                "При выбранном лимите ставится HERD_CULL, а не BREED_ANIMALS");
        assertFalse(
                BreedingRules.breedingAvailable(Species.COW, 4, true), "Пятую голову не разводим: лимит вида закрыт");
    }

    @Test
    void action_FourSheep_NoCull() {
        assertEquals(
                BreedingRules.Action.NONE,
                BreedingRules.action(Species.SHEEP, 4, true),
                "Четыре овцы — лимит выбран, но забой не ставится: овец загона не убиваем, "
                        + "шерсть — возобновляемый ресурс");
        assertFalse(
                BreedingRules.cullNeeded(Species.SHEEP, 4, true),
                "HERD_CULL на овец не выдаётся: иначе доска сама наряжала бы духа резать своё стадо");
    }

    @Test
    void action_WithoutPen_None() {
        assertEquals(
                BreedingRules.Action.NONE,
                BreedingRules.action(Species.COW, 4, false),
                "Без загона разведение и контроль численности не идут: стадо не приманено, " + "работать некому");
    }

    @Test
    void action_NullSpeciesOrNegative_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> BreedingRules.action(null, 2, true),
                "Вид null обязан отвергаться: без него неизвестно, чей загон и лимит считать");
        assertThrows(
                IllegalArgumentException.class,
                () -> BreedingRules.action(Species.COW, -1, true),
                "Отрицательное поголовье обязано падать: минус голов ломал бы переход к контролю");
    }

    @Test
    void breedingGate_AnySpecies_UsesOwnFeed() {
        assertEquals(
                Map.of("minecraft:wheat", 2),
                BreedingRules.breedingGate(Species.COW),
                "Гейт разведения коровы — пшеница вида из FeedTable, две на пару: чужой корм не " + "свёл бы пару");
        assertEquals(
                Map.of("minecraft:carrot", 2),
                BreedingRules.breedingGate(Species.PIG),
                "Гейт разведения свиньи — морковь вида: корм берётся из таблицы husbandry, а не "
                        + "выбирается наугад");
    }

    @Test
    void stockFor_AnyPopulation_MultipliesPerCapita() {
        assertEquals(
                Map.of("minecraft:beef", 4, "minecraft:leather", 4),
                BreedingRules.stockFor(Species.COW, 4),
                "Запас на 4 коровы — норма на душу (1) умножена на поголовье: мясо и шкура по 4");
        assertEquals(
                Map.of("minecraft:mutton", 2, "minecraft:white_wool", 2),
                BreedingRules.stockFor(Species.SHEEP, 2),
                "Овцы дают мясо и шерсть: норма на душу умножается на число голов");
    }

    @Test
    void stockFor_ZeroPopulation_NoStock() {
        assertEquals(
                Map.of("minecraft:chicken", 0, "minecraft:egg", 0),
                BreedingRules.stockFor(Species.CHICKEN, 0),
                "Ноль кур — нулевой запас: норма на душу умножается на ноль, задание склада на "
                        + "ноль голов не ставится");
    }

    @Test
    void stockFor_NullSpeciesOrNegative_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> BreedingRules.stockPerCapita(null),
                "Вид null обязан отвергаться: норму считают по виду");
        assertThrows(
                IllegalArgumentException.class,
                () -> BreedingRules.stockFor(Species.COW, -1),
                "Отрицательная численность обязана падать: запас на минус голов не считается");
    }

    @Property
    void action_AnyHoused_MatchesLimitAndIsMonotone(
            @ForAll("species") Species species, @ForAll("populations") int housed) {
        int min = HerdCap.minBreed();
        int max = HerdCap.maxPerSpecies();

        BreedingRules.Action action = BreedingRules.action(species, housed, true);

        if (housed >= max && species == Species.SHEEP) {
            assertEquals(
                    BreedingRules.Action.NONE,
                    action,
                    "Овца на лимите не забивается: загон держит 4 головы, шерсть стригут повторно");
        } else if (housed >= max) {
            assertEquals(
                    BreedingRules.Action.CULL,
                    action,
                    "При выбранном лимите (" + max + ") — контроль численности, а не разведение");
        } else if (housed >= min) {
            assertEquals(
                    BreedingRules.Action.BREED, action, "От пары (" + min + ") до лимита (" + max + ") — разведение");
        } else {
            assertEquals(BreedingRules.Action.NONE, action, "Меньше пары — нечего разводить");
        }
        if (action == BreedingRules.Action.CULL) {
            assertEquals(
                    BreedingRules.Action.CULL,
                    BreedingRules.action(species, housed + 1, true),
                    "Лимит монотонен: добавление головы не возвращает разведение");
        }
    }

    @Property
    void action_AnyInput_IsDeterministic(
            @ForAll("species") Species species, @ForAll("populations") int housed, @ForAll boolean penBuilt) {
        assertEquals(
                BreedingRules.action(species, housed, penBuilt),
                BreedingRules.action(species, housed, penBuilt),
                "Переход детерминирован: один вход — одно действие, иначе доска плывёт между "
                        + "разведением и забоем");
    }

    @Property
    void stockFor_AnyPopulation_IsMonotone(@ForAll("species") Species species, @ForAll("populations") int population) {
        Map<String, Integer> smaller = BreedingRules.stockFor(species, population);
        Map<String, Integer> larger = BreedingRules.stockFor(species, population + 1);

        assertEquals(
                smaller.keySet(),
                larger.keySet(),
                "Состав продуктов не зависит от поголовья: у вида одни и те же продукты");
        for (Map.Entry<String, Integer> entry : smaller.entrySet()) {
            String item = entry.getKey();
            assertTrue(
                    larger.get(item) >= entry.getValue(),
                    "Запас растёт с поголовьем: " + item + " при " + population + " голов не больше, " + "чем при "
                            + (population + 1));
        }
    }

    @Provide
    Arbitrary<Species> species() {
        return Arbitraries.of(Species.values());
    }

    @Provide
    Arbitrary<Integer> populations() {
        return Arbitraries.integers().between(0, 6);
    }
}
