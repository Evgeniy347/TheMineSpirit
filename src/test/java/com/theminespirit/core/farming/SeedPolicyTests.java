package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SeedPolicyTests {
    static Stream<Arguments> replantRows() {
        return Stream.of(
                Arguments.of(
                        CropKind.WHEAT,
                        0,
                        SeedPolicy.Decision.OUT_OF_SEEDS,
                        "без семян клетка остаётся пустой и считается"),
                Arguments.of(
                        CropKind.WHEAT, 1, SeedPolicy.Decision.PLANT, "одно семя из урожая закрывает пересадку клетки"),
                Arguments.of(
                        CropKind.CARROT,
                        64,
                        SeedPolicy.Decision.PLANT,
                        "запас моркови из урожая держит грядку самоподдерживающейся"),
                Arguments.of(
                        CropKind.BEETROOT,
                        5,
                        SeedPolicy.Decision.PLANT,
                        "пересадка берёт семена из урожая и инвентаря"));
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("replantRows")
    void forReplant_SeedCount_MatchesDecision(CropKind kind, int items, SeedPolicy.Decision expected, String reason) {
        assertEquals(
                expected,
                SeedPolicy.forReplant(kind, items),
                "Решение о пересадке обязано зависеть от запаса посадочного предмета (" + reason + "): "
                        + "иначе дух оставит грядку пустой при полных закромах или посадит из воздуха");
    }

    static Stream<Arguments> plantingRows() {
        return Stream.of(
                Arguments.of(CropKind.WHEAT, 0, 9, 0, "без семян не сажается ни одна клетка"),
                Arguments.of(CropKind.WHEAT, 1, 9, 1, "одно семя закрывает одну клетку"),
                Arguments.of(CropKind.WHEAT, 10, 9, 9, "лишние семена остаются: клеток меньше, чем семян"),
                Arguments.of(CropKind.CARROT, 3, 9, 3, "семян меньше клеток: засаживается только запас"),
                Arguments.of(CropKind.POTATO, 64, 0, 0, "без клеток сажать некуда"));
    }

    @ParameterizedTest(name = "{4}")
    @MethodSource("plantingRows")
    void forPlanting_ItemsAndCells_MatchesCount(CropKind kind, int items, int cells, int expected, String reason) {
        assertEquals(
                expected,
                SeedPolicy.forPlanting(kind, items, cells),
                "Число посадок обязано равняться min(предметов, клеток) (" + reason + "): "
                        + "иначе дух посадит из воздуха или бросит семена при пустой грядке");
    }

    @Property
    void forPlanting_AnyCounts_PlantableWithinItemsAndCells(
            @ForAll CropKind kind,
            @ForAll @IntRange(min = 0, max = 1000) int items,
            @ForAll @IntRange(min = 0, max = 1000) int cells) {
        if (kind == CropKind.ANY) {
            return;
        }

        int planted = SeedPolicy.forPlanting(kind, items, cells);

        assertTrue(
                planted <= Math.min(items, cells),
                "Посажено обязано не превышать min(предметов, клеток): иначе посадка тратит семена из воздуха. "
                        + "Предметов: " + items + ", клеток: " + cells + ", посажено: " + planted);
    }

    @Property
    void forReplant_AnySeedCount_MatchesSingleSeedRule(@ForAll @IntRange(min = 0, max = 1000) int items) {
        assertEquals(
                items > 0 ? SeedPolicy.Decision.PLANT : SeedPolicy.Decision.OUT_OF_SEEDS,
                SeedPolicy.forReplant(CropKind.WHEAT, items),
                "Пересадка одной клетки требует ровно одно семя: иначе клетка останется пустой при запасе. "
                        + "Предметов: " + items);
    }

    @Test
    void forReplant_NegativeItems_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SeedPolicy.forReplant(CropKind.WHEAT, -1),
                "Отрицательный запас обязан отвергаться: иначе учёт семян расходится с инвентарём");
    }
}
