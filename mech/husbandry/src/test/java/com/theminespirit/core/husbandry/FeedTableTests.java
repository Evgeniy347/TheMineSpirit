package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.api.FeedTable;
import com.theminespirit.core.husbandry.api.Species;
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
                    "Вид " + species + " обязан НЕ есть " + food + " (" + reason + "): иначе дух скормит стаду "
                            + "чужой корм, разведение не сработает, и пара простоит до тайм-аута. При смене таблицы "
                            + "в FeedTable.feedFor проверь feed.json и живое кормление пары этого вида. Фактически: "
                            + foods);
        } else {
            assertTrue(
                    foods.contains(food),
                    "Вид " + species + " обязан есть " + food + " (" + reason + "): иначе дух решит, что корма "
                            + "нет, не начнёт разведение и загон останется без приплода. При смене FeedTable.feedFor "
                            + "проверь feed.json и живое кормление пары этого вида. Фактически: " + foods);
        }
    }

    @Test
    void loadFile_AllSpeciesCoveredWithoutDuplicates_MatchesDefaults() {
        FeedTable fromFile = FeedTable.loadFile();

        assertEquals(
                Set.of(Species.COW, Species.SHEEP, Species.PIG, Species.CHICKEN),
                fromFile.feeds().keySet(),
                "Файл feed.json обязан покрывать все четыре вида: иначе непокрытый вид останется без корма и "
                        + "разведения, и дух молча пройдёт мимо загона. При смене набора Species проверь feed.json и "
                        + "FeedTable.defaults, затем живое разведение каждого вида");
        Set<String> seen = new HashSet<>();
        for (Map.Entry<Species, List<String>> entry : fromFile.feeds().entrySet()) {
            assertTrue(
                    !entry.getValue().isEmpty(),
                    "У вида " + entry.getKey() + " пустой список кормов: такой вид нельзя кормить, иначе дух "
                            + "принесёт корм, который никто не ест, и потратит ход впустую. При смене feed.json "
                            + "проверь непустые списки у всех четырёх видов");
            for (String food : entry.getValue()) {
                assertTrue(
                        seen.add(entry.getKey() + "=" + food),
                        "У вида " + entry.getKey() + " корм " + food + " повторяется: дубль ничего не значит, "
                                + "иначе подсчёт корма и таймауты кормления идут дважды. При смене feed.json "
                                + "проверь уникальность записей по виду и корму");
            }
        }
        assertEquals(
                FeedTable.defaults(),
                fromFile,
                "Таблица в коде обязана совпадать с feed.json: данные правятся файлом, код его только читает, "
                        + "а расхождение даст духу чужой корм после рестарта, иначе разведение встанет. При смене feed.json обнови "
                        + "FeedTable.defaults и проверь живое разведение всех видов");
    }

    @Test
    void load_UnknownSpeciesId_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> FeedTable.load(Map.of("dragon", List.of("minecraft:wheat"))),
                "Неизвестный вид в таблице обязан отвергаться исключением: тихий пропуск оставил бы вид без "
                        + "корма, а опечатку в файле никто не заметил бы, иначе вид останется голодным. При смене FeedTable.load проверь отказ "
                        + "с именем вида и живое чтение feed.json при старте сервера");

        assertTrue(
                error.getMessage().contains("dragon"),
                "Сообщение обязано называть неизвестный вид: иначе опечатку в feed.json не найти, а отказ "
                        + "выглядит как «неверная таблица» вообще, иначе опечатку не найти. Фактическое: "
                        + error.getMessage()
                        + ". При смене текста проверь имя вида в причине");
    }

    @Test
    void load_EmptyFoodList_IsRejected() {
        Map<String, List<String>> raw = Map.of("cow", List.of());

        assertThrows(
                IllegalStateException.class,
                () -> FeedTable.load(raw),
                "Пустой список кормов обязан отвергаться: вид без корма пропускать молча нельзя, иначе дух "
                        + "решит, что кормить нечем, без объяснения и простоит у загона. При смене FeedTable.load "
                        + "проверь отказ на виде с пустым списком в feed.json");
    }

    @Test
    void parse_UnknownSpeciesName_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Species.parse("dragon"),
                "Неизвестный вид из плана обязан отвергаться: иначе скрипт будет искать несуществующее стадо, "
                        + "дух уйдёт в пустую клетку и потеряет цикл. При смене Species.parse проверь отказ на "
                        + "имени вне списка и принятие всех четырёх видов feed.json");
    }
}
