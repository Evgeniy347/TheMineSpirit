package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FeedTableTests {
    static Stream<Arguments> feedRows() {
        return Stream.of(
                Arguments.of(Species.COW, "minecraft:wheat", true, "корова ест пшеницу"),
                Arguments.of(Species.SHEEP, "minecraft:wheat", true, "овца ест пшеницу"),
                Arguments.of(Species.PIG, "minecraft:carrot", true, "свинья ест морковь"),
                Arguments.of(Species.CHICKEN, "minecraft:wheat_seeds", true, "курица ест семена"),
                Arguments.of(Species.CHICKEN, "minecraft:wheat", false, "курице пшеница не подходит, только семена"),
                Arguments.of(Species.PIG, "minecraft:wheat", false, "свинье пшеница не подходит, только морковь"),
                Arguments.of(Species.COW, "minecraft:carrot", false, "корове морковь не подходит, только пшеница"),
                Arguments.of(Species.SHEEP, "minecraft:wheat_seeds", false, "овце семена не подходят, только пшеница"));
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("feedRows")
    void feedFor_SpeciesTable_MatchesContract(Species species, String food, boolean eats, String reason) {
        List<String> foods = FeedTable.feedFor(species);

        if (!eats) {
            assertTrue(
                    !foods.contains(food),
                    "Вид " + species + " обязан НЕ есть " + food + " (" + reason + "): иначе дух скормит стаду чужой "
                            + "корм и разведение не сработает. Фактически: " + foods);
        } else {
            assertTrue(
                    foods.contains(food),
                    "Вид " + species + " обязан есть " + food + " (" + reason + "): иначе дух решит, что корма нет. "
                            + "Фактически: " + foods);
        }
    }

    @Test
    void loadFile_AllSpeciesCoveredWithoutDuplicates_MatchesDefaults() {
        FeedTable fromFile = FeedTable.loadFile();

        assertEquals(
                Set.of(Species.COW, Species.SHEEP, Species.PIG, Species.CHICKEN),
                fromFile.feeds().keySet(),
                "Файл feed.json обязан покрывать все четыре вида: непокрытый вид останется без корма и разведения");
        Set<String> seen = new HashSet<>();
        for (Map.Entry<Species, List<String>> entry : fromFile.feeds().entrySet()) {
            assertTrue(
                    !entry.getValue().isEmpty(),
                    "У вида " + entry.getKey() + " пустой список кормов: такой вид нельзя кормить");
            for (String food : entry.getValue()) {
                assertTrue(
                        seen.add(entry.getKey() + "=" + food),
                        "У вида " + entry.getKey() + " корм " + food + " повторяется: дубль ничего не значит");
            }
        }
        assertEquals(
                FeedTable.defaults(),
                fromFile,
                "Таблица в коде обязана совпадать с feed.json: данные правятся файлом, код его только читает, "
                        + "расхождение даст духу чужой корм");
    }

    @Test
    void load_UnknownSpeciesId_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> FeedTable.load(Map.of("dragon", List.of("minecraft:wheat"))),
                "Неизвестный вид в таблице обязан отвергаться исключением: тихий пропуск оставил бы вид без корма, "
                        + "а опечатку в файле никто не заметил бы");

        assertTrue(
                error.getMessage().contains("dragon"),
                "Сообщение обязано называть неизвестный вид: иначе опечатку в файле не найти. Фактически: "
                        + error.getMessage());
    }

    @Test
    void load_EmptyFoodList_IsRejected() {
        Map<String, List<String>> raw = Map.of("cow", List.of());

        assertThrows(
                IllegalStateException.class,
                () -> FeedTable.load(raw),
                "Пустой список кормов обязан отвергаться: вид без корма пропускать молча нельзя, иначе дух решит, "
                        + "что кормить нечем, без объяснения");
    }

    @Test
    void parse_UnknownSpeciesName_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Species.parse("dragon"),
                "Неизвестный вид из плана обязан отвергаться: иначе скрипт будет искать несуществующее стадо");
    }
}
